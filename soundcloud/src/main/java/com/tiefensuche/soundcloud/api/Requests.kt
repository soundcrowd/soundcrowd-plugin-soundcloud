/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcloud.api

import com.tiefensuche.soundcloud.api.Constants.COLLECTION
import com.tiefensuche.soundcloud.api.Constants.JSON_NULL
import com.tiefensuche.soundcloud.api.Constants.NEXT_HREF
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONObject

class Requests {

    enum class Method(val value: String) {
        GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE")
    }

    open class Endpoint(route: String, val method: Method) {
        open val url = "${Endpoints.SC_API_URL}$route"
    }

    class CollectionEndpoint(route: String, size: Int = 50) : Endpoint(route, Method.GET) {
        override val url = append(append(super.url, "linked_partitioning", "1"), "page_size", "$size")

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
                    if (it == JSON_NULL) {
                        return JSONArray()
                    }
                    currentUrl = it
                } ?: return JSONArray()
            }

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