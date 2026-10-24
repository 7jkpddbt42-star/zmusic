package com.musicbox.app.source.netease

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : HttpTransport {

    private val formType =
        "application/x-www-form-urlencoded;charset=utf-8".toMediaType()

    override fun post(url: String, body: String, headers: Map<String, String>): TransportResponse {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(formType))
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .build()

        client.newCall(request).execute().use { response ->
            return TransportResponse(response.code, response.body.string(), response.headers("Set-Cookie"))
        }
    }

    override fun get(url: String, headers: Map<String, String>): TransportResponse {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .build()

        client.newCall(request).execute().use { response ->
            return TransportResponse(response.code, response.body.string(), response.headers("Set-Cookie"))
        }
    }
}
