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
        assertContains(body, "system rules")
        assertContains(body, "<<<ARTICLE>>>")
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
