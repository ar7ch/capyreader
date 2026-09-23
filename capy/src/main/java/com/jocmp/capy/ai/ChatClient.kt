package com.jocmp.capy.ai

import com.jocmp.capy.common.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AiSettings(
    val baseURL: String,
    val apiKey: String,
    val model: String,
)

/**
 * Minimal client for the OpenAI chat completions API. Works against OpenAI itself and against
 * compatible servers such as Ollama.
 *
 * The reply is untrusted text. Callers render it as plain text and never as HTML or markup.
 */
class ChatClient(httpClient: OkHttpClient) {
    private val client = httpClient.newBuilder()
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun complete(settings: AiSettings, system: String, user: String): Result<String> {
        val url = completionsURL(settings.baseURL)
            ?: return Result.failure(InvalidSettingsError("Base URL is not a valid http or https URL"))

        if (settings.model.isBlank()) {
            return Result.failure(InvalidSettingsError("Model name is empty"))
        }

        val payload = json.encodeToString(
            ChatRequest(
                model = settings.model,
                messages = listOf(
                    ChatMessage(role = "system", content = system),
                    ChatMessage(role = "user", content = user),
                ),
            )
        )

        val builder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        val apiKey = settings.apiKey.trim()

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        return try {
            val response = client.newCall(builder.build()).await()
            val body = response.body.string()

            if (!response.isSuccessful) {
                return Result.failure(HttpError(code = response.code, body = body.take(500)))
            }

            val content = json.decodeFromString<ChatResponse>(body)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()

            val cleaned = stripReasoning(content)

            if (cleaned.isBlank()) {
                Result.failure(EmptyResponseError())
            } else {
                Result.success(cleaned)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    class InvalidSettingsError(override val message: String) : Throwable(message)

    class HttpError(val code: Int, val body: String) : Throwable("HTTP $code")

    class EmptyResponseError : Throwable("The model returned an empty response")

    companion object {
        private const val READ_TIMEOUT_SECONDS = 300L
        private const val CALL_TIMEOUT_SECONDS = 360L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private val reasoningBlock =
            Regex("<think>.*?(</think>|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        internal fun completionsURL(baseURL: String): String? {
            val trimmed = baseURL.trim().trimEnd('/')
            val parsed = trimmed.toHttpUrlOrNull() ?: return null

            return "$parsed".trimEnd('/') + "/chat/completions"
        }

        /**
         * Reasoning models such as Qwen3 wrap their scratch work in think tags. Only the text
         * after the block is the answer.
         */
        internal fun stripReasoning(content: String): String {
            return content.replace(reasoningBlock, "").trim()
        }
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val stream: Boolean = false,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatResponse(
    @SerialName("choices") val choices: List<ChatChoice> = emptyList(),
)

@Serializable
private data class ChatChoice(
    val message: ChatMessage? = null,
)
