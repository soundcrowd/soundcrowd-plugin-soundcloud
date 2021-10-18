/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import android.content.SharedPreferences
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.util.Log
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.Constants.ERROR
import com.tiefensuche.soundcloud.api.Constants.KIND
import com.tiefensuche.soundcloud.api.Constants.LIKE
import com.tiefensuche.soundcloud.api.Constants.ORIGIN
import com.tiefensuche.soundcloud.api.Constants.TRACK
import com.tiefensuche.soundcloud.api.Constants.TRACKS
import com.tiefensuche.soundcloud.api.Constants.USER
import com.tiefensuche.soundcloud.api.Constants.REFRESH_TOKEN
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.collections.HashMap

/**
 *
 * Created by tiefensuche on 07.02.18.
 */

class SoundCloudApi(val CLIENT_ID: String, val CLIENT_SECRET: String, val REDIRECT_URI: String, val prefs: SharedPreferences) {

    companion object {
        private var TAG = this::class.java.simpleName
    }

    var accessToken: String? = prefs.getString(ACCESS_TOKEN, null)
    var refreshToken: String? = prefs.getString(REFRESH_TOKEN, null)
    var preferDownloadStream: Boolean = false
    val nextQueryUrls: HashMap<String, String> = HashMap()
    val playlists: HashMap<String, List<MediaMetadataCompat>> = HashMap()
    var likesTrackIds: MutableSet<Long> = HashSet()

    /**
     * Request new access token for the user identified by the username and password
     */
    @Throws(JSONException::class, IOException::class, UserNotFoundException::class, InvalidCredentialsException::class)
    fun getAccessToken(code: String, refresh: Boolean) {
        val data = "grant_type=" + (if (refresh) "refresh_token" else "authorization_code") +
                "&client_id=$CLIENT_ID" +
                "&client_secret=$CLIENT_SECRET" +
                (if (refresh) "&refresh_token=$code" else "&redirect_uri=$REDIRECT_URI&code=$code")
        try {
            val response = JSONObject(WebRequests.post(Endpoints.OAUTH2_TOKEN_URL, data).value)
            if (!response.has(ACCESS_TOKEN)) {
                throw Exception("Could not get access token!")
            }
            accessToken = response.getString(ACCESS_TOKEN)
            refreshToken = response.getString(REFRESH_TOKEN)
            prefs.edit()
                .putString(ACCESS_TOKEN, accessToken)
                .putString(REFRESH_TOKEN, refreshToken)
                .apply()
        } catch (e: WebRequests.HttpException) {
            val response = JSONObject(e.message)
            if (response.has(ERROR)) {
                if (response.getString(ERROR) == "invalid_grant")
                    throw InvalidCredentialsException("Invalid code")
                else
                    throw Exception(response.getString(ERROR))
            }
            throw e
        }
    }

    /**
     * SoundCloud stream for the configured username and password
     */
    @Throws(NotAuthenticatedException::class, JSONException::class, IOException::class)
    fun getStream(reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.STREAM_URL, reset).execute())
    }

    /**
     * SoundCloud likes for username
     */
    @Throws(IOException::class, JSONException::class, NotAuthenticatedException::class)
    fun getLikes(reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.SELF_LIKES_URL, reset).execute())
    }

    /**
     * The SoundCloud search function
     *
     * @param query
     */
    @Throws(JSONException::class, IOException::class)
    fun query(query: String, reset: Boolean): List<MediaMetadataCompat> {
        val trackList = JSONArray()
        val urls = arrayOf(Endpoints.QUERY_URL, Endpoints.QUERY_USER_URL)
        for (url in urls) {
            val tracks = Requests.CollectionRequest(this, url, reset, URLEncoder.encode(query, "UTF-8")).execute()
            for (j in 0 until tracks.length()) {
                trackList.put(tracks.getJSONObject(j))
            }
        }
        return parseTracksFromJSONArray(trackList)
    }

    /**
     * Returns the tracks of the given user id
     */
    @Throws(JSONException::class, IOException::class, NotAuthenticatedException::class)
    fun getUserTracks(userId: String, reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.USER_TRACKS_URL, reset, userId).execute())
    }

    /**
     * Returns the logged in user's tracks
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getSelfTracks(reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.SELF_TRACKS_URL, reset).execute())
    }

    /**
     * Returns the logged in user's playlists
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getSelfPlaylists(): List<MediaMetadataCompat> {
        val playlists = Requests.CollectionRequest(this, Endpoints.SELF_PLAYLISTS_URL, false).execute()

        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until playlists.length()) {
            val playlist = playlists.getJSONObject(i)

            val artwork: String = if (!playlist.isNull(Constants.ARTWORK_URL)) {
                playlist.getString(Constants.ARTWORK_URL)
            } else {
                playlist.getJSONObject(Constants.USER).getString(Constants.AVATAR_URL)
            }

            result.add(MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playlist.getString(Constants.ID))
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playlist.getString(Constants.TITLE))
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playlist.getJSONObject(Constants.USER).getString(Constants.USERNAME))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, playlist.getLong(Constants.DURATION))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artwork.replace("large", "t500x500"))
                    .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                .build())

            // Since the endpoint additionally serves the playlist items, store them in a cache, so we don't need to query the playlist again
            this.playlists[playlist.getString(Constants.ID)] = parseTracksFromJSONArray(playlist.getJSONArray(TRACKS))
        }

        return result
    }

    /**
     * Returns the users the logged in user is following
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowings(reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.FOLLOWINGS_USER_URL, reset).execute())
    }

    /**
     * Returns the users that are following the logged in user
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowers(reset: Boolean): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.FOLLOWERS_USER_URL, reset).execute())
    }

    /**
     * Returns cached playlists identified by the given id
     */
    @Throws(NotAuthenticatedException::class, JSONException::class, IOException::class)
    fun getPlaylist(id: String, reset: Boolean): List<MediaMetadataCompat> {
        if (!playlists.containsKey(id)) {
            return emptyList()
        }
        return playlists.getValue(id)
    }

    private fun parseTracksFromJSONArray(tracks: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        for (j in 0 until tracks.length()) {
            try {
                var track = tracks.getJSONObject(j)
                if (track.has(ORIGIN)) {
                    track = track.getJSONObject(ORIGIN)
                }
                if (track.has(KIND)) {
                    when (track.getString(KIND)) {
                        TRACK -> result.add(buildTrackFromJSON(track))
                        LIKE -> result.add(buildTrackFromJSON(track.getJSONObject(TRACK)))
                        USER -> result.add(buildUserFromJSON(track))
                        else -> Log.w(TAG, "unexpected kind: " + track.getString(KIND))
                    }
                }
            } catch (e: NotStreamableException) {
                // skip item
            } catch (e: JSONException) {
                Log.w(TAG, "parsing exception", e)
            }
        }
        return result
    }

    @Throws(JSONException::class, NotStreamableException::class)
    internal fun buildTrackFromJSON(json: JSONObject): MediaMetadataCompat {
        if (!json.getBoolean(Constants.STREAMABLE))
            throw NotStreamableException("Item can not be streamed!")

        val artwork = if (!json.isNull(Constants.ARTWORK_URL)) {
            json.getString(Constants.ARTWORK_URL)
        } else {
            json.getJSONObject(Constants.USER).getString(Constants.AVATAR_URL)
        }

        val result = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, json.getLong(Constants.ID).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, json.getString(Constants.STREAM_URL))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, json.getJSONObject(Constants.USER).getString(Constants.USERNAME))
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, json.getString(Constants.DESCRIPTION))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, json.getLong(Constants.DURATION))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artwork.replace("large", "t500x500"))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, json.getString(Constants.TITLE))
                .putString(MediaMetadataCompatExt.METADATA_KEY_URL, json.getString(Constants.PERMALINK_URL))
                .putString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL, json.getString(Constants.WAVEFORM_URL).replace("w1", "wis").replace("png", "json"))
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)

        // The favorite status requires the user to be logged in. Not adding the metadata will
        // effectively disable the favorite functionality and hiding it in the UI.
        if (accessToken != null) {
            if (!json.isNull(Constants.USER_FAVORITE) && json.getBoolean(Constants.USER_FAVORITE)) {
                result.putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, RatingCompat.newHeartRating(true))
                likesTrackIds.add(json.getLong(Constants.ID))
            } else {
                result.putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, RatingCompat.newHeartRating(false))
            }
        }

        if (preferDownloadStream && !json.isNull(Constants.DOWNLOADABLE) && json.getBoolean(Constants.DOWNLOADABLE) && json.has(Constants.DOWNLOAD_URL))
            result.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, json.getString(Constants.DOWNLOAD_URL))

        return result.build()
    }

    @Throws(JSONException::class)
    private fun buildUserFromJSON(json: JSONObject): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, json.getLong(Constants.ID).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, json.getString(Constants.USERNAME))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, json.getString(Constants.FULL_NAME))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, json.getString(Constants.AVATAR_URL).replace("large", "t500x500"))
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, json.getString(Constants.DESCRIPTION))
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
            .build()
    }

    @Throws(IOException::class, NotAuthenticatedException::class)
    fun toggleLike(trackId: String): Boolean {
        return if (!likesTrackIds.contains(trackId.toLong()))
            like(trackId)
        else
            unlike(trackId)
    }

    /**
     * Responses
     * 200 - Success
     *
     * @param trackId
     * @param accessToken
     * @return
     * @throws IOException
     */
    @Throws(IOException::class, NotAuthenticatedException::class)
    private fun like(trackId: String): Boolean {
        val result = Requests.ActionRequest(this, Endpoints.LIKE_TRACK_URL, trackId).execute()
        val success = result.status == 200
        if (success)
            likesTrackIds.add(trackId.toLong())
        return success
    }

    /**
     * Responses
     * 200 - OK - Success
     * 404 - Not found - Track was not liked
     *
     * @param trackId
     * @param accessToken
     * @return
     * @throws IOException
     */
    @Throws(IOException::class, NotAuthenticatedException::class)
    private fun unlike(trackId: String): Boolean {
        val result = Requests.ActionRequest(this, Endpoints.UNLIKE_TRACK_URL, trackId).execute()
        val success = result.status == 200
        if (success)
            likesTrackIds.remove(trackId.toLong())
        return success
    }

    fun getStreamUrl(url: String): String {
        val res = Requests.ActionRequest(this, Requests.Endpoint(Uri.parse(url).path, Requests.Method.GET)).execute()
        if (res.status == 302) {
            return JSONObject(res.value).getString("location")
        }
        throw NotStreamableException("Can not get stream url")
    }

    // Exception types
    class NotAuthenticatedException(message: String) : Exception(message)
    class InvalidCredentialsException(message: String) : Exception(message)
    class NotStreamableException(message: String) : Exception(message)
    class UserNotFoundException(message: String) : Exception(message)
}