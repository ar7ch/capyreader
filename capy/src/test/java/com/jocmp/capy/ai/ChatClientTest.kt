package com.jocmp.capy.ai

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatClientTest {
    private val settings = AiSettings(
        baseURL = "https://api.example.com/v1",
        apiKey = "sk-test",
        model = "test-model",
    )

    @Test
    fun complete_postsToChatCompletions() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("A short summary."))

        val result = ChatClient(interceptor.client())
            .complete(settings, system = "system rules", user = "<<<ARTICLE>>>body<<<END ARTICLE>>>")

        assertEquals(expected = "A short summary.", actual = result.getOrThrow())

        val request = interceptor.requests.single()

        assertEquals(
            expected = "https://api.example.com/v1/chat/completions",
            actual = request.url.toString()
        )
        assertEquals(expected = "Bearer sk-test", actual = request.header("Authorization"))

        val body = interceptor.bodies.single()

        assertContains(body, "\"model\":\"test-model\"")
        assertContains(body, "\"reasoning_effort\":\"none\"")
        assertContains(body, "\"thinking\":{\"type\":\"disabled\"}")
        assertContains(body, "system rules")
        assertContains(body, "<<<ARTICLE>>>")
    }

    @Test
    fun complete_ignoresGLMReasoningContentDeltas() = runTest {
        // GLM streams its scratch work in a separate reasoning_content field, not in content.
        val stream = listOf(
            """data: {"choices":[{"index":0,"delta":{"reasoning_content":"weighing options"}}]}""",
            """data: {"choices":[{"index":0,"delta":{"content":"Caching "}}]}""",
            """data: {"choices":[{"index":0,"delta":{"content":"is explained."}}]}""",
            "data: [DONE]",
        ).joinToString("\n\n", postfix = "\n\n")
        val interceptor = RecordingInterceptor(
            stream,
            contentType = RecordingInterceptor.EVENT_STREAM_CONTENT_TYPE,
        )
        val deltas = mutableListOf<String>()

        val result = ChatClient(interceptor.client())
            .complete(settings, system = "s", user = "u", onDelta = { deltas.add(it) })

        assertEquals(expected = "Caching is explained.", actual = result.getOrThrow())
        assertEquals(expected = listOf("Caching", "Caching is explained."), actual = deltas)
    }

    @Test
    fun complete_readsStreamedDeltas() = runTest {
        val interceptor = RecordingInterceptor(
            chatStream(listOf("The release ", "adds incremental ", "backups.")),
            contentType = RecordingInterceptor.EVENT_STREAM_CONTENT_TYPE,
        )
        val deltas = mutableListOf<String>()

        val result = ChatClient(interceptor.client())
            .complete(settings, system = "s", user = "u", onDelta = { deltas.add(it) })

        assertEquals(expected = "The release adds incremental backups.", actual = result.getOrThrow())
        assertEquals(
            expected = listOf(
                "The release",
                "The release adds incremental",
                "The release adds incremental backups.",
            ),
            actual = deltas,
        )
        assertContains(interceptor.bodies.single(), "\"stream\":true")
    }

    @Test
    fun complete_readsAPlainJSONBodyWhenTheServerIgnoresStreaming() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("A short summary."))
        val deltas = mutableListOf<String>()

        val result = ChatClient(interceptor.client())
            .complete(settings, system = "s", user = "u", onDelta = { deltas.add(it) })

        assertEquals(expected = "A short summary.", actual = result.getOrThrow())
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun complete_keepsStreamedReasoningOutOfTheDeltas() = runTest {
        val interceptor = RecordingInterceptor(
            chatStream(
                listOf("<thi", "nk>", "Maybe the ", "second option", "</think>", "Caching ", "is explained.")
            ),
            contentType = RecordingInterceptor.EVENT_STREAM_CONTENT_TYPE,
        )
        val deltas = mutableListOf<String>()

        val result = ChatClient(interceptor.client())
            .complete(settings, system = "s", user = "u", onDelta = { deltas.add(it) })

        assertEquals(expected = "Caching is explained.", actual = result.getOrThrow())
        assertEquals(expected = listOf("Caching", "Caching is explained."), actual = deltas)
    }

    @Test
    fun complete_retriesWithoutReasoningEffortWhenRejected() = runTest {
        val interceptor = RecordingInterceptor(
            responses = listOf(
                """{"error":{"message":"Unsupported parameter: 'reasoning_effort' is not supported with this model."}}""",
                chatResponse("A short summary."),
            ),
            codes = listOf(400, 200),
        )

        val result = ChatClient(interceptor.client()).complete(settings, system = "s", user = "u")

        assertEquals(expected = "A short summary.", actual = result.getOrThrow())
        assertEquals(expected = 2, actual = interceptor.requests.size)
        assertContains(interceptor.bodies[0], "reasoning_effort")
        assertTrue(!interceptor.bodies[1].contains("reasoning_effort"))
        assertTrue(!interceptor.bodies[1].contains("thinking"))
    }

    @Test
    fun complete_omitsAuthorizationWhenKeyIsBlank() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        ChatClient(interceptor.client()).complete(
            settings.copy(apiKey = "  "),
            system = "s",
            user = "u",
        ).getOrThrow()

        assertNull(interceptor.requests.single().header("Authorization"))
    }

    @Test
    fun complete_stripsReasoningBlocks() = runTest {
        val interceptor = RecordingInterceptor(
            chatResponse("<think>Let me weigh the options.</think>\nThe article explains caching.")
        )

        val result = ChatClient(interceptor.client()).complete(settings, system = "s", user = "u")

        assertEquals(expected = "The article explains caching.", actual = result.getOrThrow())
    }

    @Test
    fun complete_failsOnHttpError() = runTest {
        val interceptor = RecordingInterceptor("""{"error":"nope"}""", code = 401)

        val result = ChatClient(interceptor.client()).complete(settings, system = "s", user = "u")

        assertTrue(result.exceptionOrNull() is ChatClient.HttpError)
    }

    @Test
    fun complete_rejectsInvalidBaseURL() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        val result = ChatClient(interceptor.client())
            .complete(settings.copy(baseURL = "not a url"), system = "s", user = "u")

        assertTrue(result.exceptionOrNull() is ChatClient.InvalidSettingsError)
        assertTrue(interceptor.requests.isEmpty())
    }

    @Test
    fun complete_rejectsBlankModel() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        val result = ChatClient(interceptor.client())
            .complete(settings.copy(model = ""), system = "s", user = "u")

        assertTrue(result.exceptionOrNull() is ChatClient.InvalidSettingsError)
    }

    @Test
    fun completionsURL_trimsTrailingSlashes() {
        assertEquals(
            expected = "http://10.0.2.2:11434/v1/chat/completions",
            actual = ChatClient.completionsURL("http://10.0.2.2:11434/v1/")
        )
    }
}
