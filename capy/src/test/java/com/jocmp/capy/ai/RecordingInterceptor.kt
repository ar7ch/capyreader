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
class RecordingInterceptor(
    private val responses: List<String>,
    private val codes: List<Int> = listOf(200),
    private val contentTypes: List<String> = listOf(JSON_CONTENT_TYPE),
) : Interceptor {
    constructor(
        response: String,
        code: Int = 200,
        contentType: String = JSON_CONTENT_TYPE,
    ) : this(listOf(response), listOf(code), listOf(contentType))

    val requests = mutableListOf<Request>()

    val bodies: List<String>
        get() = requests.map { request ->
            Buffer().also { request.body?.writeTo(it) }.readUtf8()
        }

    fun client() = OkHttpClient.Builder().addInterceptor(this).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = responses[minOf(requests.size, responses.size - 1)]
        val code = codes[minOf(requests.size, codes.size - 1)]
        val contentType = contentTypes[minOf(requests.size, contentTypes.size - 1)]

        requests.add(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }

    companion object {
        const val JSON_CONTENT_TYPE = "application/json"
        const val EVENT_STREAM_CONTENT_TYPE = "text/event-stream"
    }
}

fun chatResponse(content: String): String {
    return """{"choices":[{"index":0,"message":{"role":"assistant","content":"${escapeJSON(content)}"}}]}"""
}

/** One server sent event per delta, the way a streaming chat completion answers. */
fun chatStream(deltas: List<String>): String {
    val events = deltas.joinToString("") { delta ->
        """data: {"choices":[{"index":0,"delta":{"content":"${escapeJSON(delta)}"}}]}""" + "\n\n"
    }

    return events + "data: [DONE]\n\n"
}

private fun escapeJSON(content: String): String {
    return content
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
}
