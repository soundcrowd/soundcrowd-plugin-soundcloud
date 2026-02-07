package com.tiefensuche.soundcrowd.plugins.soundcloud

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.Constants.REFRESH_TOKEN
import com.tiefensuche.soundcloud.api.Endpoints
import com.tiefensuche.soundcloud.api.SoundCloudApi
import com.tiefensuche.soundcrowd.plugins.IPlugin

/**
 * SoundCloud plugin for soundcrowd
 */
class Plugin(val context: Context) : IPlugin {

    companion object {
        const val NAME = "SoundCloud"
        const val STREAM = "Stream"
        const val LIKES = "Likes"
        const val PLAYLISTS = "Playlists"
        const val FOLLOWINGS = "Followings"
        const val FOLLOWERS = "Followers"
        const val YOU = "You"

        const val SEARCH_CATEGORY_TRACKS = "Tracks"
        const val SEARCH_CATEGORY_ARTISTS = "Artists"
    }

    private val icon: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.icon_plugin_soundcloud)
    private var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var soundCloudApi: SoundCloudApi = SoundCloudApi(context.getString(R.string.client_id),
        context.getString(R.string.client_secret), context.getString(R.string.redirect_uri), sharedPref)
    private val connectPreference = SwitchPreference(context)

    init {
        connectPreference.key = context.getString(R.string.soundcloud_connect_key)
        connectPreference.title = context.getString(R.string.soundcloud_connect_title)
        connectPreference.summary = context.getString(R.string.soundcloud_connect_summary)
        connectPreference.isChecked = sharedPref.getString(ACCESS_TOKEN, null) != null
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                val intent = Intent(
                    Intent.ACTION_VIEW, Uri.parse("${Endpoints.SC_API_URL}/connect?client_id=${context.getString(
                        R.string.client_id)}&redirect_uri=${context.getString(
                        R.string.redirect_uri)}&response_type=code"))
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                false
            } else {
                sharedPref.edit().putString(ACCESS_TOKEN, null).putString(REFRESH_TOKEN, null).apply()
                true
            }
        }
    }

    override fun name(): String = NAME

    override fun mediaCategories(): List<String> = listOf(STREAM, LIKES, PLAYLISTS, FOLLOWINGS, FOLLOWERS, YOU)

    override fun preferences() = listOf(connectPreference)

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, refresh: Boolean) : List<MediaItem> {
        return processRequest(mediaCategory, refresh)
    }

    private fun processRequest(mediaCategory: String, refresh: Boolean) : List<MediaItem> {
        return when (mediaCategory) {
            STREAM -> soundCloudApi.getStream(refresh)
            LIKES -> soundCloudApi.getLikes(refresh)
            PLAYLISTS -> soundCloudApi.getPlaylists(refresh)
            FOLLOWINGS -> soundCloudApi.getFollowings(refresh)
            FOLLOWERS -> soundCloudApi.getFollowers(refresh)
            YOU -> soundCloudApi.getSelfTracks(refresh)
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, path: String, refresh: Boolean) : List<MediaItem> {
        return when (mediaCategory) {
            STREAM -> soundCloudApi.getPlaylist(path, refresh)
            PLAYLISTS -> soundCloudApi.getPlaylist(path, refresh)
            FOLLOWINGS, FOLLOWERS -> soundCloudApi.getUserTracks(path, refresh)
            YOU -> soundCloudApi.getPlaylist(path, refresh)
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, path: String, query: String, type: String, refresh: Boolean) : List<MediaItem> {
        return if (path.isEmpty()) {
            search(query, type, refresh)
        } else {
            soundCloudApi.getUserTracks(path, refresh)
        }
    }

    override fun getMediaUri(mediaItem: MediaItem) : Uri {
        return "${soundCloudApi.getStreamUrl(mediaItem.mediaId)},${soundCloudApi.accessToken}".toUri()
    }

    override fun favorite(id: String) : Boolean {
        return soundCloudApi.toggleLike(id)
    }

    override fun getIcon(): Bitmap = icon

    private class RequestAccessTokenTask(private val plugin : Plugin) : AsyncTask<String, Void, Boolean>() {
        override fun doInBackground(vararg p0: String): Boolean {
            return try {
                plugin.soundCloudApi.getAccessToken(p0[0], false)
                true
            } catch (e: Exception) {
                false
            }
        }

        override fun onPostExecute(result: Boolean) {
            plugin.connectPreference.isChecked = result
        }
    }

    private fun callback(callback: String) {
        RequestAccessTokenTask(this).execute(callback.substringAfterLast('='))
    }

    override fun callbacks() = mapOf("soundcloud" to ::callback)

    override fun getSuggestions(category: String, query: String): List<String> {
        return search(query, category, true).map { it.mediaMetadata.title.toString() }
    }

    override fun searchCategories(): List<String> {
        return listOf(SEARCH_CATEGORY_TRACKS, SEARCH_CATEGORY_ARTISTS)
    }

    private fun search(query: String, type: String, refresh: Boolean): List<MediaItem> {
        return when (type) {
            SEARCH_CATEGORY_TRACKS -> soundCloudApi.query(query, Endpoints.QUERY_URL, refresh)
            SEARCH_CATEGORY_ARTISTS -> soundCloudApi.query(query, Endpoints.QUERY_USER_URL, refresh)
            else -> emptyList()
        }
    }
}