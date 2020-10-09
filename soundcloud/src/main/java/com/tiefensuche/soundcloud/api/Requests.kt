/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import com.tiefensuche.soundcloud.api.Constants.COLLECTION
import com.tiefensuche.soundcloud.api.Constants.JSON_NULL
import com.tiefensuche.soundcloud.api.Constants.NEXT_HREF
import com.tiefensuche.soundcloud.api.SoundCloudApi.Companion.append
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONObject

class Requests {

    enum class Method(val value: String) {
        GET("GET"), PUT("PUT"), DELETE("DELETE")
    }

    open class Endpoint(route: String, val method: Method, val authenticated: Boolean) {
        open val url = "${Endpoints.SC_API_URL}$route"
    }

    class CollectionEndpoint(route: String, authenticated: Boolean, size: Int = 50) : Endpoint(route, Method.GET, authenticated) {
        override val url = append(append(super.url, "linked_partitioning", "1"), "page_size", "$size")
    }

    class CollectionRequest(session: SoundCloudApi.Session, endpoint: CollectionEndpoint, private val reset: Boolean, vararg args: Any?) : Request<JSONArray>(session, endpoint, *args) {

        override fun execute(): JSONArray {
            var currentUrl = url

            if (!reset && url in session.nextQueryUrls) {
                session.nextQueryUrls[url]?.let {
                    if (it == JSON_NULL) {
                        return JSONArray()
                    }
                    currentUrl = it
                } ?: return JSONArray()
            }

            currentUrl = appendAuthentication(currentUrl)

            val response = request(currentUrl)
            val json = JSONObject(response.value)
            if (!json.isNull(NEXT_HREF)) {
                session.nextQueryUrls[url] = JSONObject(response.value).getString(NEXT_HREF)
            } else {
                session.nextQueryUrls[url] = JSON_NULL
            }

            if (!json.has(COLLECTION)) {
                return JSONArray()
            }
            return json.getJSONArray(COLLECTION)
        }
    }

    class ActionRequest(session: SoundCloudApi.Session, endpoint: Endpoint, vararg args: Any?) : Request<WebRequests.Response>(session, endpoint, *args) {
        override fun execute(): WebRequests.Response {
            return request(appendAuthentication(url))
        }
    }

    abstract class Request<T>(val session: SoundCloudApi.Session, val endpoint: Endpoint, vararg args: Any?) {
        val url = endpoint.url.format(*args)

        fun appendAuthentication(url: String): String {
            return if (endpoint.authenticated) {
                if (session.accessToken == null) {
                    throw SoundCloudApi.NotAuthenticatedException()
                }
                append(url, "oauth_token", session.accessToken)
            } else {
                append(url, "client_id", session.CLIENT_ID)
            }
        }

        fun request(url: String): WebRequests.Response {
            try {
                return WebRequests.request(url, endpoint.method.value)
            } catch (e: WebRequests.HttpException) {
                if (e.code == 401) {
                    throw SoundCloudApi.NotAuthenticatedException()
                }
                throw e
            }
        }

        abstract fun execute(): T
    }
}