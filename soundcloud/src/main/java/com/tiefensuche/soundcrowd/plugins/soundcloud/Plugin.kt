package com.tiefensuche.soundcrowd.plugins.soundcloud

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaMetadataCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.Constants.REFRESH_TOKEN
import com.tiefensuche.soundcloud.api.Endpoints
import com.tiefensuche.soundcloud.api.SoundCloudApi
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin

/**
 * SoundCloud plugin for soundcrowd
 */
class Plugin(appContext: Context, val context: Context) : IPlugin {

    companion object {
        const val name = "SoundCloud"
        const val STREAM = "Stream"
        const val LIKES = "Likes"
        const val PLAYLISTS = "Playlists"
        const val FOLLOWINGS = "Followings"
        const val FOLLOWERS = "Followers"
        const val YOU = "You"
    }

    private val icon: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private var soundCloudApi: SoundCloudApi = SoundCloudApi(context.getString(R.string.client_id),
        context.getString(R.string.client_secret), context.getString(R.string.redirect_uri), sharedPref)
    private val connectPreference = SwitchPreference(appContext)

    init {
        connectPreference.key = context.getString(R.string.connect_key)
        connectPreference.title = context.getString(R.string.connect_title)
        connectPreference.summary = context.getString(R.string.connect_summary)
        connectPreference.isChecked = sharedPref.getString(ACCESS_TOKEN, null) != null
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                val intent = Intent(
                    Intent.ACTION_VIEW, Uri.parse("${Endpoints.SC_API_URL}/connect?client_id=${context.getString(
                        R.string.client_id)}&redirect_uri=${context.getString(
                        R.string.redirect_uri)}&response_type=code"))
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                appContext.startActivity(intent)
                false
            } else {
                sharedPref.edit().putString(ACCESS_TOKEN, null).putString(REFRESH_TOKEN, null).apply()
                true
            }
        }
    }

    override fun name(): String = name

    override fun mediaCategories(): List<String> = listOf(STREAM, LIKES, PLAYLISTS, FOLLOWINGS, FOLLOWERS, YOU)

    override fun preferences() = listOf(connectPreference)

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        processRequest(mediaCategory, callback, refresh)
    }

    private fun processRequest(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        when (mediaCategory) {
            STREAM -> callback.onResult(soundCloudApi.getStream(refresh))
            LIKES -> callback.onResult(soundCloudApi.getLikes(refresh))
            PLAYLISTS -> callback.onResult(soundCloudApi.getSelfPlaylists())
            FOLLOWINGS -> callback.onResult(soundCloudApi.getFollowings(refresh))
            FOLLOWERS -> callback.onResult(soundCloudApi.getFollowers(refresh))
            YOU -> callback.onResult(soundCloudApi.getSelfTracks(refresh))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, path: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        when (mediaCategory) {
            STREAM -> callback.onResult(soundCloudApi.getPlaylist(path, refresh))
            PLAYLISTS -> callback.onResult(soundCloudApi.getPlaylist(path, refresh))
            FOLLOWINGS, FOLLOWERS -> callback.onResult(soundCloudApi.getUserTracks(path, refresh))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, path: String, query: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        if (path.isEmpty()) {
            callback.onResult(soundCloudApi.query(query, refresh))
        } else {
            callback.onResult(soundCloudApi.getUserTracks(path, refresh))
        }
    }

    override fun getMediaUrl(metadata: MediaMetadataCompat, callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>) {
        val steamUrl = soundCloudApi.getStreamUrl(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))
        callback.onResult(Pair(MediaMetadataCompat.Builder(metadata)
            .putString(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL, steamUrl).build(), null))
    }

    override fun favorite(id: String, callback: Callback<Boolean>) {
        callback.onResult(soundCloudApi.toggleLike(id))
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
}