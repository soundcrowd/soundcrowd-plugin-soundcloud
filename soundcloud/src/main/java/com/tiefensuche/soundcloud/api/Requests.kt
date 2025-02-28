/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import com.tiefensuche.soundcloud.api.Constants.COLLECTION
import com.tiefensuche.soundcloud.api.Constants.JSON_NULL
import com.tiefensuche.soundcloud.api.Constants.NEXT_HREF
import com.tiefensuche.soundcrowd.plugins.WebRequests
import org.json.JSONArray
import org.json.JSONObject

class Requests {

    enum class Method(val value: String) {
        GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE")
    }

    open class Endpoint(route: String, val method: Method) {
        open val url = "${Endpoints.SC_API_URL}$route"
    }

    class CollectionEndpoint(route: String, limit: Int = 50) : Endpoint(route, Method.GET) {
        override val url = append(append(super.url, "linked_partitioning", "1"), "limit", "$limit")

        private fun append(url: String, key: String, value: String?): String {
            return url + when {
                url.contains('?') -> '&'
                else -> '?'
            } + "$key=$value"
        }
    }

    class CollectionRequest(session: SoundCloudApi, endpoint: CollectionEndpoint, private val reset: Boolean, vararg args: Any?) : Request<JSONArray>(session, endpoint, *args) {

        override fun execute(): JSONArray {
            var currentUrl = url

            if (!reset && url in session.nextQueryUrls) {
                session.nextQueryUrls[url]?.let {
                    currentUrl = it
                } ?: return JSONArray()
            }

            while (currentUrl != JSON_NULL) {
                val response = request(currentUrl)
                val json = JSONObject(response.value)
                currentUrl = if (!json.isNull(NEXT_HREF)) {
                    json.getString(NEXT_HREF)
                } else {
                    JSON_NULL
                }
                session.nextQueryUrls[url] = currentUrl

                if (!json.has(COLLECTION)) {
                    return JSONArray()
                }
                val collection = json.getJSONArray(COLLECTION)
                if (collection.length() > 0)
                    return collection
            }
            return JSONArray()
        }
    }

    class ActionRequest(session: SoundCloudApi, endpoint: Endpoint, vararg args: Any?) : Request<WebRequests.Response>(session, endpoint, *args) {
        override fun execute(): WebRequests.Response {
            return request(url)
        }
    }

    abstract class Request<T>(val session: SoundCloudApi, val endpoint: Endpoint, vararg args: Any?) {
        val url = endpoint.url.format(*args)

        fun request(url: String): WebRequests.Response {
            if (session.accessToken == null) {
                throw SoundCloudApi.NotAuthenticatedException("Not authenticated!")
            }
            try {
                return WebRequests.request(url, endpoint.method.value, mapOf("Authorization" to "OAuth ${session.accessToken}"))
            } catch (e: WebRequests.HttpException) {
                if (e.code == 401) {
                    session.accessToken = null
                    session.refreshToken?.let {
                        session.getAccessToken(it, true)
                        return WebRequests.request(url, endpoint.method.value, mapOf("Authorization" to "OAuth ${session.accessToken}"))
                    }
                }
                throw e
            }
        }

        abstract fun execute(): T
    }
}