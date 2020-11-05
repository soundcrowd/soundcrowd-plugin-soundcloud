/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.tiefensuche.soundcloud.api.Constants.ACCESS_TOKEN
import com.tiefensuche.soundcloud.api.Constants.ERROR
import com.tiefensuche.soundcloud.api.Constants.JSON_NULL
import com.tiefensuche.soundcloud.api.Constants.KIND
import com.tiefensuche.soundcloud.api.Constants.LIKE
import com.tiefensuche.soundcloud.api.Constants.ORIGIN
import com.tiefensuche.soundcloud.api.Constants.TRACK
import com.tiefensuche.soundcloud.api.Constants.TRACKS
import com.tiefensuche.soundcloud.api.Constants.USER
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*
import kotlin.collections.HashMap

/**
 *
 * Created by tiefensuche on 07.02.18.
 */

class SoundCloudApi(CLIENT_ID: String, CLIENT_SECRET: String, accessToken: String? = null) {

    companion object {
        private var TAG = this::class.java.simpleName

        fun append(url: String, key: String, value: String?): String {
            return url + when {
                url.contains('?') -> '&'
                else -> '?'
            } + "$key=$value"
        }
    }

    class Session(val CLIENT_ID: String,
                  val CLIENT_SECRET: String,
                  var accessToken: String? = null,
                  val nextQueryUrls: HashMap<String, String> = HashMap(),
                  val playlists: HashMap<String, JSONArray> = HashMap(),
                  var likesTrackIds: MutableSet<Long> = HashSet())

    private val session: Session = Session(CLIENT_ID, CLIENT_SECRET, accessToken)

    /**
     * Request new access token for the user identified by the username and password
     */
    @Throws(JSONException::class, IOException::class, UserNotFoundException::class, InvalidCredentialsException::class)
    fun getAccessToken(username: String, password: String): String {
        try {
            val response = JSONObject(WebRequests.post(Endpoints.OAUTH2_TOKEN_URL,
                    "client_id=${session.CLIENT_ID}&client_secret=${session.CLIENT_SECRET}" +
                            "&grant_type=password&username=$username&password=$password").value)

            if (response.has(ACCESS_TOKEN)) {
                session.accessToken = response.getString(ACCESS_TOKEN)
                return response.getString(ACCESS_TOKEN)
            }
            throw Exception("Could not get access token!")
        } catch (e: WebRequests.HttpException) {
            val response = JSONObject(e.message)
            if (response.has(ERROR)) {
                if (response.getString(ERROR) == "invalid_grant")
                    throw InvalidCredentialsException("Username and/or password wrong!")
                else
                    throw Exception(response.getString(ERROR))
            }
            throw Exception(e.message)
        }
    }

    /**
     * SoundCloud stream for the configured username and password
     */
    @Throws(NotAuthenticatedException::class, JSONException::class, IOException::class)
    fun getStream(reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.STREAM_URL, reset).execute(), session.CLIENT_ID)
    }

    /**
     * SoundCloud likes for username
     */
    @Throws(IOException::class, JSONException::class, NotAuthenticatedException::class)
    fun getLikes(reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.SELF_LIKES_URL, reset).execute(), session.CLIENT_ID)
    }

    /**
     * The SoundCloud search function
     *
     * @param query
     */
    @Throws(JSONException::class, IOException::class)
    fun query(query: String, reset: Boolean): JSONArray {

        val trackList = JSONArray()
        val urls = arrayOf(Endpoints.QUERY_URL, Endpoints.QUERY_USER_URL)
        for (url in urls) {
            val tracks = Requests.CollectionRequest(session, url, reset, URLEncoder.encode(query, "UTF-8")).execute()
            for (j in 0 until tracks.length()) {
                trackList.put(tracks.getJSONObject(j))
            }
        }

        return parseTracksFromJSONArray(trackList, session.CLIENT_ID)
    }

    /**
     * Returns the tracks of the given user id
     */
    @Throws(JSONException::class, IOException::class, NotAuthenticatedException::class)
    fun getUserTracks(userId: String, reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.USER_TRACKS_URL, reset, userId).execute(), session.CLIENT_ID)
    }

    /**
     * Returns the logged in user's tracks
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getSelfTracks(reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.SELF_TRACKS_URL, reset).execute(), session.CLIENT_ID)
    }

    /**
     * Returns the logged in user's playlists
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getSelfPlaylists(): JSONArray {
        val playlists = Requests.CollectionRequest(session, Endpoints.SELF_PLAYLISTS_URL, false).execute()

        val result = JSONArray()
        for (i in 0 until playlists.length()) {
            val playlist = playlists.getJSONObject(i)

            val artwork: String = if (!playlist.isNull(Constants.ARTWORK_URL)) {
                playlist.getString(Constants.ARTWORK_URL)
            } else {
                playlist.getJSONObject(Constants.USER).getString(Constants.AVATAR_URL)
            }

            result.put(JSONObject().put(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playlist.getString(Constants.ID))
                    .put(MediaMetadataCompat.METADATA_KEY_TITLE, playlist.getString(Constants.TITLE))
                    .put(MediaMetadataCompat.METADATA_KEY_ARTIST, playlist.getJSONObject(Constants.USER).getString(Constants.USERNAME))
                    .put(MediaMetadataCompat.METADATA_KEY_DURATION, playlist.getInt(Constants.DURATION))
                    .put(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artwork.replace("large", "t500x500"))
                    .put(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name))

            // Since the endpoint additionally serves the playlist items, store them in a cache, so we don't need to query the playlist again
            session.playlists[playlist.getString(Constants.ID)] = parseTracksFromJSONArray(playlist.getJSONArray(TRACKS), session.CLIENT_ID)
        }

        return result
    }

    /**
     * Returns the users the logged in user is following
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowings(reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.FOLLOWINGS_USER_URL, reset).execute(), session.CLIENT_ID)
    }

    /**
     * Returns the users that are following the logged in user
     */
    @Throws(IOException::class, NotAuthenticatedException::class, JSONException::class)
    fun getFollowers(reset: Boolean): JSONArray {
        return parseTracksFromJSONArray(Requests.CollectionRequest(session, Endpoints.FOLLOWERS_USER_URL, reset).execute(), session.CLIENT_ID)
    }

    /**
     * Returns cached playlists identified by the given id
     */
    @Throws(NotAuthenticatedException::class, JSONException::class, IOException::class)
    fun getPlaylist(id: String, reset: Boolean): JSONArray {

        if (!session.playlists.containsKey(id)) {
            return JSONArray()
        }

        return session.playlists.getValue(id)
    }

    private fun parseTracksFromJSONArray(tracks: JSONArray, clientId: String): JSONArray {
        val result = JSONArray()
        for (j in 0 until tracks.length()) {
            try {
                var track = tracks.getJSONObject(j)
                if (track.has(ORIGIN)) {
                    track = track.getJSONObject(ORIGIN)
                }
                if (track.has(KIND)) {
                    when (track.getString(KIND)) {
                        TRACK -> result.put(buildTrackFromJSON(track, clientId))
                        LIKE -> result.put(buildTrackFromJSON(track.getJSONObject(TRACK), clientId))
                        USER -> result.put(buildUserFromJSON(track))
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
    internal fun buildTrackFromJSON(json: JSONObject, clientId: String): JSONObject {
        if (!json.getBoolean(Constants.STREAMABLE))
            throw NotStreamableException("Item can not be streamed!")

        val user = json.getJSONObject(Constants.USER)
        val avatarUrl = user.getString(Constants.AVATAR_URL)
        var artworkUrl: String? = json.getString(Constants.ARTWORK_URL)
        if (artworkUrl == JSON_NULL) {
            artworkUrl = avatarUrl
            if (artworkUrl.contains("?"))
                artworkUrl = null
        }
        val result = JSONObject()
        result.put(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, json.getLong(Constants.ID).toString())
                .put(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, append(json.getString(Constants.STREAM_URL), "consumer_key", clientId))
                .put(MediaMetadataCompat.METADATA_KEY_ARTIST, user.getString(Constants.USERNAME))
                .put(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, json.getString(Constants.DESCRIPTION))
                .put(MediaMetadataCompat.METADATA_KEY_DURATION, json.getInt(Constants.DURATION))
                .put(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artworkUrl?.replace("large", "t500x500"))
                .put(MediaMetadataCompat.METADATA_KEY_TITLE, json.getString(Constants.TITLE))
                .put(MediaMetadataCompatExt.METADATA_KEY_URL, json.getString(Constants.PERMALINK_URL))
                .put(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL, json.getString(Constants.WAVEFORM_URL).replace("w1", "wis").replace("png", "json"))
                .put(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)

        // The favorite status requires the user to be logged in. Not adding the metadata will
        // effectively disable the favorite functionality and hiding it in the UI.
        if (session.accessToken != null)
            result.put(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, isLiked(json.getLong(Constants.ID)))
        if (!json.isNull(Constants.DOWNLOADABLE) && json.getBoolean(Constants.DOWNLOADABLE) && json.has(Constants.DOWNLOAD_URL))
            result.put(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL, json.getString(Constants.DOWNLOAD_URL))

        return result
    }

    @Throws(JSONException::class)
    private fun buildUserFromJSON(json: JSONObject): JSONObject {
        val result = JSONObject()
        result.put(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, json.getLong(Constants.ID).toString())
                .put(MediaMetadataCompat.METADATA_KEY_TITLE, json.getString(Constants.USERNAME))
                .put(MediaMetadataCompat.METADATA_KEY_ARTIST, json.getString(Constants.FULL_NAME))
                .put(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, json.getString(Constants.AVATAR_URL).replace("large", "t500x500"))
                .put(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, json.getString(Constants.DESCRIPTION))
                .put(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)

        return result
    }

    @Throws(IOException::class, NotAuthenticatedException::class)
    fun toggleLike(trackId: String): Boolean {
        if (session.likesTrackIds.isEmpty())
            getLikedTrackIds()
        return if (!session.likesTrackIds.contains(trackId.toLong()))
            like(trackId)
        else
            unlike(trackId)
    }

    /**
     * Responses
     * 201 - Created - Success
     * 200 - OK - Liked already
     *
     * @param trackId
     * @param accessToken
     * @return
     * @throws IOException
     */
    @Throws(IOException::class, NotAuthenticatedException::class)
    private fun like(trackId: String): Boolean {
        val result = Requests.ActionRequest(session, Endpoints.LIKE_TRACK_URL, trackId).execute()
        val success = result.status == 201
        if (success)
            session.likesTrackIds.add(java.lang.Long.parseLong(trackId))
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
        val result = Requests.ActionRequest(session, Endpoints.UNLIKE_TRACK_URL, trackId).execute()
        val success = result.status == 200
        if (success)
            session.likesTrackIds.remove(java.lang.Long.parseLong(trackId))
        return success
    }

    private fun isLiked(soundCloudId: Long?): Boolean {
        if (session.likesTrackIds.isEmpty()) {
            getLikedTrackIds()
        }
        return session.likesTrackIds.contains(soundCloudId)
    }

    @Throws(IOException::class, JSONException::class)
    private fun getLikedTrackIds() {
        val request = Requests.CollectionRequest(session, Endpoints.SELF_LIKES_IDS_URL, false)
        do {
            val collection = request.execute()
            for (i in 0 until collection.length()) {
                session.likesTrackIds.add(collection.getLong(i))
            }
        } while (!session.nextQueryUrls[request.url].equals(JSON_NULL))
    }

    // Exception types
    class NotAuthenticatedException(message: String) : Exception(message)
    class InvalidCredentialsException(message: String) : Exception(message)
    class NotStreamableException(message: String) : Exception(message)
    class UserNotFoundException(message: String) : Exception(message)
}