package com.jocmp.capy.ai

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * Answers every call with a canned body and keeps the requests for assertions.
 */
class RecordingInterceptor(private val responses: List<String>, private val code: Int = 200) :
    Interceptor {
    constructor(response: String, code: Int = 200) : this(listOf(response), code)

    val requests = mutableListOf<Request>()

    val bodies: List<String>
        get() = requests.map { request ->
            Buffer().also { request.body?.writeTo(it) }.readUtf8()
        }

    fun client() = OkHttpClient.Builder().addInterceptor(this).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = responses[minOf(requests.size, responses.size - 1)]

        requests.add(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

fun chatResponse(content: String): String {
    val escaped = content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    return """{"choices":[{"index":0,"message":{"role":"assistant","content":"$escaped"}}]}"""
}
