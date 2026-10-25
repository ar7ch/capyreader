package com.jocmp.capy.ai

import com.jocmp.aiprofiles.AiProfile
import com.jocmp.aiprofiles.ThinkingLevel
import com.jocmp.aiprofiles.firstSuccess
import com.jocmp.capy.common.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AiSettings(
    /** In priority order. A request falls back down the list, see [firstSuccess]. */
    val profiles: List<AiProfile>,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.NONE,
    /** Extra rules from the user, appended to the system message of every request. */
    val instructions: String = "",
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

    /**
     * [onDelta] is called with the whole answer so far each time more of it arrives, so a caller
     * can replace whatever it is showing with the new text.
     */
    suspend fun complete(
        settings: AiSettings,
        system: String,
        user: String,
        onDelta: (String) -> Unit = {},
        onAnswered: (AiProfile) -> Unit = {},
    ): Result<String> = complete(
        settings = settings,
        messages = listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = user),
        ),
        onDelta = onDelta,
        onAnswered = onAnswered,
    )

    /**
     * [messages] is the whole conversation so far, oldest first, starting with the system message.
     * Requests run on the IO dispatcher, so [onDelta] is called from a background thread. Each failed
     * attempt ends with an empty [onDelta], so text from it does not linger.
     * [onAnswered] gets the profile that produced the reply.
     */
    suspend fun complete(
        settings: AiSettings,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit = {},
        onAnswered: (AiProfile) -> Unit = {},
    ): Result<String> {
        val withInstructions = withInstructions(messages, settings.instructions)

        return firstSuccess(settings.profiles) { profile ->
            withContext(Dispatchers.IO) { request(profile, settings.thinkingLevel, withInstructions, onDelta) }
                .onSuccess { onAnswered(profile) }
                // A failed attempt may have streamed part of an answer; take it back.
                .onFailure { onDelta("") }
        }
    }

    private suspend fun request(
        profile: AiProfile,
        thinkingLevel: ThinkingLevel,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
    ): Result<String> {
        val url = completionsURL(profile.baseUrl)
            ?: return Result.failure(InvalidSettingsError("Base URL is not a valid http or https URL"))

        val request = chatRequest(url, profile.model, thinkingLevel, messages)

        return try {
            var response = send(url, profile.apiKey, request)

            // A server that rejects the thinking field answers 400; retry once without it. A
            // genuine 400 repeats on the plain request and is returned.
            if (response.code == 400) {
                val body = response.body.string()

                response = send(url, profile.apiKey, request.copy(reasoningEffort = null, thinking = null, reasoning = null))

                if (!response.isSuccessful) {
                    return Result.failure(HttpError(code = 400, body = body.take(500)))
                }
            }

            if (!response.isSuccessful) {
                val body = response.body.string()

                return Result.failure(HttpError(code = response.code, body = body.take(500)))
            }

            val cleaned = stripReasoning(read(response, onDelta))

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

    private fun chatRequest(
        url: String,
        model: String,
        level: ThinkingLevel,
        messages: List<ChatMessage>,
    ): ChatRequest {
        val host = url.toHttpUrlOrNull()?.host.orEmpty()

        // Each family reads its own field and some reject the others' fields with 400, so a
        // request carries only the one its host understands.
        return when {
            isOn(host, "openrouter.ai") -> ChatRequest(
                model = model,
                messages = messages,
                reasoning = Reasoning(effort = level.effort),
            )

            GLM_HOSTS.any { isOn(host, it) } -> ChatRequest(
                model = model,
                messages = messages,
                thinking = Thinking(if (level == ThinkingLevel.NONE) "disabled" else "enabled"),
            )

            // Non-reasoning models such as gpt-4.1-mini reject the field outright, so off sends
            // nothing and reasoning models keep their default effort.
            else -> ChatRequest(
                model = model,
                messages = messages,
                reasoningEffort = level.effort.takeUnless { level == ThinkingLevel.NONE },
            )
        }
    }

    private fun isOn(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun withInstructions(messages: List<ChatMessage>, instructions: String): List<ChatMessage> {
        val extra = instructions.trim()
        val first = messages.firstOrNull()

        if (extra.isBlank() || first?.role != "system") {
            return messages
        }

        return listOf(first.copy(content = "${first.content}\n\nAdditional instructions from the user:\n$extra")) +
            messages.drop(1)
    }

    private suspend fun send(url: String, apiKey: String, request: ChatRequest): Response {
        val builder = Request.Builder()
            .url(url)
            .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")

        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        return client.newCall(builder.build()).await()
    }

    private suspend fun read(response: Response, onDelta: (String) -> Unit): String {
        val body = response.body

        // Some proxies ignore the stream flag and answer with one JSON document.
        if (body.contentType()?.subtype?.lowercase() != EVENT_STREAM_SUBTYPE) {
            return content(body.string())
        }

        return withContext(Dispatchers.IO) { readEventStream(body, onDelta) }
    }

    private fun readEventStream(body: ResponseBody, onDelta: (String) -> Unit): String {
        val answer = StringBuilder()
        var emitted = ""

        body.source().use { source ->
            while (true) {
                val line = source.readUtf8Line() ?: break

                if (!line.startsWith(DATA_PREFIX)) {
                    continue
                }

                val payload = line.removePrefix(DATA_PREFIX).trim()

                if (payload == DONE_PAYLOAD) {
                    break
                }

                val delta = deltaContent(payload)

                if (delta.isEmpty()) {
                    continue
                }

                answer.append(delta)

                val visible = emittable(answer.toString())

                if (visible != null && visible != emitted) {
                    emitted = visible
                    onDelta(visible)
                }
            }
        }

        return answer.toString()
    }

    class InvalidSettingsError(override val message: String) : Throwable(message)

    class HttpError(val code: Int, val body: String) : Throwable("HTTP $code")

    class EmptyResponseError : Throwable("The model returned an empty response")

    companion object {
        private const val READ_TIMEOUT_SECONDS = 300L
        private const val CALL_TIMEOUT_SECONDS = 360L
        private const val EVENT_STREAM_SUBTYPE = "event-stream"

        private val GLM_HOSTS = listOf("z.ai", "bigmodel.cn")
        private const val DATA_PREFIX = "data:"
        private const val DONE_PAYLOAD = "[DONE]"

        private val THINK_TAGS = listOf("<think>", "</think>")

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

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

        private fun content(body: String): String {
            return json.decodeFromString<ChatResponse>(body)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()
        }

        private fun deltaContent(payload: String): String {
            return runCatching { json.decodeFromString<ChatStreamChunk>(payload) }
                .getOrNull()
                ?.choices
                ?.firstOrNull()
                ?.delta
                ?.content
                .orEmpty()
        }

        /**
         * The answer to show while it is still arriving, or null when there is nothing to show yet.
         * Scratch work is dropped as it comes in rather than shown and taken back, and a half
         * written think tag waits for the rest of itself.
         */
        internal fun emittable(answer: String): String? {
            if (holdsPartialTag(answer)) {
                return null
            }

            return stripReasoning(answer).ifBlank { null }
        }

        private fun holdsPartialTag(answer: String): Boolean {
            val opened = answer.lastIndexOf('<')

            if (opened == -1) {
                return false
            }

            val tail = answer.substring(opened)

            return THINK_TAGS.any { tag ->
                tag.length > tail.length && tag.startsWith(tail, ignoreCase = true)
            }
        }
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val stream: Boolean = true,
    @SerialName("reasoning_effort")
    val reasoningEffort: String? = null,
    // GLM ignores reasoning_effort; this is its own switch for the same thing.
    val thinking: Thinking? = null,
    // OpenRouter's form of the same setting.
    val reasoning: Reasoning? = null,
)

@Serializable
private data class Reasoning(
    val effort: String,
)

@Serializable
private data class Thinking(
    val type: String,
)

@Serializable
data class ChatMessage(
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

@Serializable
private data class ChatStreamChunk(
    @SerialName("choices") val choices: List<ChatStreamChoice> = emptyList(),
)

@Serializable
private data class ChatStreamChoice(
    val delta: ChatMessageDelta? = null,
)

/** Only the answer is read back; a model's reasoning fields are left alone. */
@Serializable
private data class ChatMessageDelta(
    val content: String? = null,
)
