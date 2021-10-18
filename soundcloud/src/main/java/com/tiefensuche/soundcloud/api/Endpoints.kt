/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

object Endpoints {
    // base url
    const val SC_API_URL = "https://api.soundcloud.com"

    // unauthenticated request methods
    val USER_TRACKS_URL = Requests.CollectionEndpoint("/users/%s/tracks")
    val PLAYLIST_URL = Requests.CollectionEndpoint("/playlists/%s")
    val QUERY_URL = Requests.CollectionEndpoint("/tracks?q=%s")
    val QUERY_USER_URL = Requests.CollectionEndpoint("/users?q=%s")

    // authentication
    const val OAUTH2_TOKEN_URL = "$SC_API_URL/oauth2/token"

    // authenticated request methods
    val STREAM_URL = Requests.CollectionEndpoint("/me/activities/tracks")
    val SELF_LIKES_URL = Requests.CollectionEndpoint("/me/likes/tracks")
    val SELF_TRACKS_URL = Requests.CollectionEndpoint("/me/tracks")
    val SELF_PLAYLISTS_URL = Requests.CollectionEndpoint("/me/playlists")
    val FOLLOWINGS_USER_URL = Requests.CollectionEndpoint("/me/followings")
    val FOLLOWERS_USER_URL = Requests.CollectionEndpoint("/me/followers")

    // authenticated actions
    val LIKE_TRACK_URL = Requests.Endpoint("/likes/tracks/%s", Requests.Method.POST)
    val UNLIKE_TRACK_URL = Requests.Endpoint("/likes/tracks/%s", Requests.Method.DELETE)
}