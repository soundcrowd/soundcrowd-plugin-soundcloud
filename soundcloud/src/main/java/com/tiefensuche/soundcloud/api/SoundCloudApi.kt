/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.Constants.ERROR
import com.tiefensuche.soundcloud.api.Constants.KIND
import com.tiefensuche.soundcloud.api.Constants.LIKE
import com.tiefensuche.soundcloud.api.Constants.ORIGIN
import com.tiefensuche.soundcloud.api.Constants.TRACK
import com.tiefensuche.soundcloud.api.Constants.USER
import com.tiefensuche.soundcloud.api.Constants.REFRESH_TOKEN
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.WebRequests
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.collections.HashMap
import androidx.core.net.toUri

/**
 *
 * Created by tiefensuche on 07.02.18.
 */
class SoundCloudApi(private val CLIENT_ID: String, private val CLIENT_SECRET: String, private val REDIRECT_URI: String, private val prefs: SharedPreferences) {

    companion object {
        private var TAG = this::class.java.simpleName
    }

    var accessToken: String? = prefs.getString(ACCESS_TOKEN, null)
    var refreshToken: String? = prefs.getString(REFRESH_TOKEN, null)
    val nextQueryUrls: HashMap<String, String> = HashMap()
    private var likesTrackIds: MutableSet<Long> = HashSet()

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
    fun getStream(reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.STREAM_URL, reset).execute())
    }

    /**
     * SoundCloud likes for username
     */
    @Throws(IOException::class, JSONException::class, NotAuthenticatedException::class)
    fun getLikes(reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.SELF_LIKES_URL, reset).execute())
    }

    /**
     * The SoundCloud search function
     *
     * @param query
     */
    @Throws(JSONException::class, IOException::class)
    fun query(query: String, endpoint: Requests.CollectionEndpoint, reset: Boolean): List<MediaItem> {
        val trackList = JSONArray()
        val tracks = Requests.CollectionRequest(this, endpoint, reset, URLEncoder.encode(query, "UTF-8")).execute()
        for (j in 0 until tracks.length()) {
            trackList.put(tracks.getJSONObject(j))
        }
        return parseTracksFromJSONArray(trackList)
    }

    /**
     * Returns the tracks of the given user id
     */
    @Throws(JSONException::class, IOException::class, NotAuthenticatedException::class)
    fun getUserTracks(userId: String, reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.USER_TRACKS_URL, reset, userId).execute())
    }

    /**
     * Returns the logged in user's tracks and playlists
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getSelfTracks(reset: Boolean): List<MediaItem> {
        val res = parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.SELF_TRACKS_URL, reset).execute()).toMutableList()
        res += parsePlaylists(Requests.CollectionRequest(this, Endpoints.SELF_PLAYLISTS_URL, reset).execute())
        return res
    }

    /**
     * Returns the logged in user's liked playlists
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getPlaylists(reset: Boolean): List<MediaItem> {
        return parsePlaylists(Requests.CollectionRequest(this, Endpoints.SELF_PLAYLIST_LIKES_URL, reset).execute())
    }

    /**
     * Returns playlist identified by the given id
     */
    fun getPlaylist(id: String, reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.PLAYLIST_URL, reset, id).execute())
    }

    private fun parsePlaylists(playlists: JSONArray): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        for (i in 0 until playlists.length()) {
            val playlist = playlists.getJSONObject(i)
            val artwork: String = if (!playlist.isNull(Constants.ARTWORK_URL)) {
                playlist.getString(Constants.ARTWORK_URL)
            } else {
                playlist.getJSONObject(Constants.USER).getString(Constants.AVATAR_URL)
            }

            result.add(MediaItemUtils.createBrowsableItem(
                playlist.getString(Constants.ID),
                playlist.getString(Constants.TITLE),
                MediaMetadataCompatExt.MediaType.STREAM,
                playlist.getJSONObject(Constants.USER).getString(Constants.USERNAME),
                artworkUri = Uri.parse(artwork.replace("large", "t500x500"))
            ))
        }
        return result
    }

    /**
     * Returns the users the logged in user is following
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowings(reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.FOLLOWINGS_USER_URL, reset).execute())
    }

    /**
     * Returns the users that are following the logged in user
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowers(reset: Boolean): List<MediaItem> {
        return parseTracksFromJSONArray(Requests.CollectionRequest(this, Endpoints.FOLLOWERS_USER_URL, reset).execute())
    }

    private fun parseTracksFromJSONArray(tracks: JSONArray): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
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
    internal fun buildTrackFromJSON(json: JSONObject): MediaItem {
        if (!json.getBoolean(Constants.STREAMABLE))
            throw NotStreamableException("Item can not be streamed!")

        val artwork = if (!json.isNull(Constants.ARTWORK_URL)) {
            json.getString(Constants.ARTWORK_URL)
        } else {
            json.getJSONObject(Constants.USER).getString(Constants.AVATAR_URL)
        }
        val result = MediaItemUtils.createMediaItem(
            json.getLong(Constants.ID).toString(),
            json.getString(Constants.URI).toUri(),
            json.getString(Constants.TITLE),
            json.getLong(Constants.DURATION),
            json.getJSONObject(Constants.USER).getString(Constants.USERNAME),
            null,
            Uri.parse(artwork.replace("large", "t500x500")),
            json.getString(Constants.WAVEFORM_URL).replace("w1", "wis").replace("png", "json"),
            json.getString(Constants.PERMALINK_URL),
            if (accessToken != null) {
                if (!json.isNull(Constants.USER_FAVORITE) && json.getBoolean(Constants.USER_FAVORITE)) {
                    HeartRating(true).also {
                        likesTrackIds.add(json.getLong(Constants.ID))
                    }
                } else {
                    HeartRating(false)
                }
            } else null
        )
        return result
    }

    @Throws(JSONException::class)
    private fun buildUserFromJSON(json: JSONObject): MediaItem {
        return MediaItemUtils.createBrowsableItem(
            json.getLong(Constants.ID).toString(),
            json.getString(Constants.USERNAME),
            MediaMetadataCompatExt.MediaType.STREAM,
            json.getString(Constants.FULL_NAME),
            null,
            Uri.parse(json.getString(Constants.AVATAR_URL).replace("large", "t500x500")),
            null,
            json.getString(Constants.DESCRIPTION)
        )
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
     * @return true if success, false otherwise
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

    fun getStreamUrl(uri: Uri): String {
        val trackStreams = JSONObject(Requests.ActionRequest(this, Endpoints.TRACK_STREAMS, "soundcloud:tracks:$uri").execute().value)
        if (trackStreams.has(Constants.HLS_AAC_160_URL))
            return trackStreams.getString(Constants.HLS_AAC_160_URL)
        if (trackStreams.has(Constants.HLS_MP3_128_URL))
            return trackStreams.getString(Constants.HLS_MP3_128_URL)
        // FIXME app currently only expects hls stream
//        if (trackStreams.has("http_mp3_128_url")) {
//            val res = Requests.ActionRequest(this, Requests.Endpoint(trackStreams.getString("http_mp3_128_url").replace("https://api.soundcloud.com", ""), Requests.Method.GET)).execute()
//            if (res.status == 302) {
//                return JSONObject(res.value).getString("location")
//            }
//        }
        throw NotStreamableException("Can not get stream url")
    }

    // Exception types
    class NotAuthenticatedException(message: String) : Exception(message)
    class InvalidCredentialsException(message: String) : Exception(message)
    class NotStreamableException(message: String) : Exception(message)
    class UserNotFoundException(message: String) : Exception(message)
}