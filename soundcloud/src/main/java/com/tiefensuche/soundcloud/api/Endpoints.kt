/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

object Endpoints {
    // base url
    const val SC_API_URL = "https://api.soundcloud.com"

    // unauthenticated request methods
    val USER_TRACKS_URL = Requests.CollectionEndpoint("/users/%s/tracks", false)
    val PLAYLIST_URL = Requests.CollectionEndpoint("/playlists/%s", false)
    val QUERY_URL = Requests.CollectionEndpoint("/tracks?q=%s", false)
    val QUERY_USER_URL = Requests.CollectionEndpoint("/users?q=%s", false)

    // authentication
    const val OAUTH2_TOKEN_URL = "$SC_API_URL/oauth2/token"

    // authenticated request methods
    val STREAM_URL = Requests.CollectionEndpoint("/me/activities/tracks", true)
    val SELF_LIKES_URL = Requests.CollectionEndpoint("/me/likes/tracks", true)
    val SELF_TRACKS_URL = Requests.CollectionEndpoint("/me/tracks", true)
    val SELF_PLAYLISTS_URL = Requests.CollectionEndpoint("/me/playlists", true)
    val FOLLOWINGS_USER_URL = Requests.CollectionEndpoint("/me/followings", true)
    val FOLLOWERS_USER_URL = Requests.CollectionEndpoint("/me/followers", true)

    // authenticated actions
    val LIKE_TRACK_URL = Requests.Endpoint("/likes/tracks/%s", Requests.Method.POST, true)
    val UNLIKE_TRACK_URL = Requests.Endpoint("/likes/tracks/%s", Requests.Method.DELETE, true)
}