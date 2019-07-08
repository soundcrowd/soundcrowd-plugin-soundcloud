package com.tiefensuche.soundcrowd.plugins.soundcloud

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.preference.PreferenceManager
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.SoundCloudApi
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * SoundCloud plugin for soundcrowd
 */
class Plugin(appContext: Context, val context: Context) : IPlugin {

    companion object {
        const val name = "SoundCloud"
        const val KEY = "key"
        const val NAME = "name"
        const val DESCRIPTION = "description"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val STREAM = "Stream"
        const val LIKES = "Likes"
        const val PLAYLISTS = "Playlists"
        const val FOLLOWINGS = "Followings"
        const val FOLLOWERS = "Followers"
        const val YOU = "You"
    }

    private val icon: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private var soundCloudApi: SoundCloudApi = SoundCloudApi(context.getString(R.string.client_id), context.getString(R.string.client_secret), sharedPref.getString(ACCESS_TOKEN, null))

    override fun name(): String = name

    override fun mediaCategories(): List<String> =
            listOf(STREAM, LIKES, PLAYLISTS, FOLLOWINGS, FOLLOWERS, YOU)

    override fun preferences(): JSONArray = JSONArray()
            .put(JSONObject()
                .put(KEY, USERNAME)
                .put(NAME, context.getString(R.string.username))
                .put(DESCRIPTION, context.getString(R.string.username_description)))
            .put(JSONObject()
                    .put(KEY, PASSWORD)
                    .put(NAME, context.getString(R.string.password))
                    .put(DESCRIPTION, context.getString(R.string.password_description)))

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, callback: Callback<JSONArray>, refresh: Boolean) {
        try {
            processRequest(mediaCategory, callback, refresh)
        } catch (e: SoundCloudApi.NotAuthenticatedException) {
            requestNewAccessToken()
            processRequest(mediaCategory, callback, refresh)
        }
    }

    private fun processRequest(mediaCategory: String, callback: Callback<JSONArray>, refresh: Boolean) {
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
    override fun getMediaItems(mediaCategory: String, path: String, callback: Callback<JSONArray>, refresh: Boolean) {
        when (mediaCategory) {
            STREAM -> callback.onResult(soundCloudApi.getPlaylist(path, refresh))
            PLAYLISTS -> callback.onResult(soundCloudApi.getPlaylist(path, refresh))
            FOLLOWINGS, FOLLOWERS -> callback.onResult(soundCloudApi.getUserTracks(path, refresh))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, path: String, query: String, callback: Callback<JSONArray>, refresh: Boolean) {
        if (path.isEmpty()) {
            callback.onResult(soundCloudApi.query(query, refresh))
        } else {
            callback.onResult(soundCloudApi.getUserTracks(path, refresh))
        }
    }

    override fun getMediaUrl(metadata: JSONObject, callback: Callback<JSONObject>) {
        // pass-through url since it contains the actual stream already
        callback.onResult(metadata)
    }

    @Throws(SoundCloudApi.UserNotFoundException::class, SoundCloudApi.InvalidCredentialsException::class, JSONException::class, IOException::class)
    private fun requestNewAccessToken(): String {

        val username = sharedPref.getString(USERNAME, null)
        val password = sharedPref.getString(PASSWORD, null)

        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            throw SoundCloudApi.InvalidCredentialsException()
        }

        val newToken = soundCloudApi.getAccessToken(username, password)
        sharedPref.edit().putString(ACCESS_TOKEN, newToken).apply()
        return newToken
    }

    override fun favorite(id: String, callback: Callback<Boolean>) {
        callback.onResult(soundCloudApi.toggleLike(id))
    }

    override fun getIcon(): Bitmap = icon
}