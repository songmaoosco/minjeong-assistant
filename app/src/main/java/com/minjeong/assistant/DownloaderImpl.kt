package com.minjeong.assistant

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class DownloaderImpl : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder().url(url)

        for ((key, values) in headers) {
            for (value in values) {
                builder.addHeader(key, value)
            }
        }

        val method = httpMethod.uppercase()
        if (method == "POST" || method == "PUT" || method == "PATCH") {
            val body = dataToSend?.toRequestBody() ?: "".toRequestBody()
            builder.method(method, body)
        } else {
            builder.method(method, null)
        }

        val response = client.newCall(builder.build()).execute()
        val responseBody = response.body?.string()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            response.request.url.toString()
        )
    }
}