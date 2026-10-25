package com.jocmp.capy.ai

import com.jocmp.aiprofiles.AiProfile
import com.jocmp.aiprofiles.AllProfilesFailedError
import com.jocmp.aiprofiles.NoProfileError
import com.jocmp.aiprofiles.ThinkingLevel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatClientTest {
    private val profile = AiProfile(baseUrl = "https://api.example.com/v1", apiKey = "sk-test", model = "test-model")

    private val settings = AiSettings(profiles = listOf(profile))

    private fun settingsFor(profile: AiProfile) = settings.copy(profiles = listOf(profile))

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
        assertTrue(!body.contains("thinking"))
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
    fun complete_sendsThinkingLevelAndInstructions() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("ok"))
        val custom = settings.copy(thinkingLevel = ThinkingLevel.HIGH, instructions = "Answer in German.")

        ChatClient(interceptor.client()).complete(custom, system = "system rules", user = "u")

        val body = interceptor.bodies.single()

        assertContains(body, "\"reasoning_effort\":\"high\"")
        assertTrue(!body.contains("thinking"))
        assertContains(body, "system rules\\n\\nAdditional instructions from the user:\\nAnswer in German.")
        assertTrue(!body.contains("\"reasoning\":"))
    }

    @Test
    fun complete_sendsGLMThinkingSwitchOnly() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("ok"))
        val glm = settingsFor(profile.copy(baseUrl = "https://api.z.ai/api/paas/v4")).copy(thinkingLevel = ThinkingLevel.MEDIUM)

        ChatClient(interceptor.client()).complete(glm, system = "s", user = "u")

        val body = interceptor.bodies.single()

        assertContains(body, "\"thinking\":{\"type\":\"enabled\"}")
        assertTrue(!body.contains("reasoning"))
    }

    @Test
    fun complete_matchesGLMHostsByWholeDomain() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("ok"))
        val lookalike = settingsFor(profile.copy(baseUrl = "https://api.quiz.ai/v1")).copy(thinkingLevel = ThinkingLevel.LOW)

        ChatClient(interceptor.client()).complete(lookalike, system = "s", user = "u")

        val body = interceptor.bodies.single()

        assertContains(body, "\"reasoning_effort\":\"low\"")
        assertTrue(!body.contains("thinking"))
    }

    @Test
    fun complete_sendsOpenRouterReasoningObject() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("ok"))
        val openRouter = settingsFor(profile.copy(baseUrl = "https://openrouter.ai/api/v1")).copy(thinkingLevel = ThinkingLevel.LOW)

        ChatClient(interceptor.client()).complete(openRouter, system = "s", user = "u")

        val body = interceptor.bodies.single()

        assertContains(body, "\"reasoning\":{\"effort\":\"low\"}")
        assertTrue(!body.contains("reasoning_effort"))
        assertTrue(!body.contains("thinking"))
    }

    @Test
    fun complete_sendsAWholeConversation() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Because it is faster."))

        val result = ChatClient(interceptor.client()).complete(
            settings,
            messages = listOf(
                ChatMessage(role = "system", content = "answer about the article"),
                ChatMessage(role = "user", content = "what is it about?"),
                ChatMessage(role = "assistant", content = "incremental backups"),
                ChatMessage(role = "user", content = "why does that matter?"),
            ),
        )

        assertEquals(expected = "Because it is faster.", actual = result.getOrThrow())

        val body = interceptor.bodies.single()

        assertContains(body, "\"role\":\"assistant\"")
        assertContains(body, "why does that matter?")
    }

    @Test
    fun complete_omitsAuthorizationWhenKeyIsBlank() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        ChatClient(interceptor.client()).complete(
            settingsFor(profile.copy(apiKey = "  ")),
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

        val error = assertIs<AllProfilesFailedError>(result.exceptionOrNull())
        assertIs<ChatClient.HttpError>(error.failures.single().second)
        assertEquals(expected = 3, actual = interceptor.requests.size)
    }

    @Test
    fun complete_fallsBackToTheNextProfile() = runTest {
        val interceptor = RecordingInterceptor(
            responses = listOf("down", "down", "down", chatResponse("From the backup.")),
            codes = listOf(500, 500, 500, 200),
        )
        val backup = profile.copy(baseUrl = "https://backup.example.com/v1")

        val deltas = mutableListOf<String>()

        val result = ChatClient(interceptor.client())
            .complete(
                settings.copy(profiles = listOf(profile, backup)),
                system = "s",
                user = "u",
                onDelta = { deltas.add(it) },
            )

        assertEquals(expected = "From the backup.", actual = result.getOrThrow())
        assertEquals(expected = listOf("", "", ""), actual = deltas)
        assertEquals(
            expected = "https://backup.example.com/v1/chat/completions",
            actual = interceptor.requests.last().url.toString(),
        )
    }

    @Test
    fun complete_rejectsInvalidBaseURL() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        val result = ChatClient(interceptor.client())
            .complete(settingsFor(profile.copy(baseUrl = "not a url")), system = "s", user = "u")

        val error = assertIs<AllProfilesFailedError>(result.exceptionOrNull())
        assertIs<ChatClient.InvalidSettingsError>(error.failures.single().second)
        assertTrue(interceptor.requests.isEmpty())
    }

    @Test
    fun complete_rejectsBlankModel() = runTest {
        val interceptor = RecordingInterceptor(chatResponse("Summary."))

        val result = ChatClient(interceptor.client())
            .complete(settingsFor(profile.copy(model = "")), system = "s", user = "u")

        assertIs<NoProfileError>(result.exceptionOrNull())
    }

    @Test
    fun completionsURL_trimsTrailingSlashes() {
        assertEquals(
            expected = "http://10.0.2.2:11434/v1/chat/completions",
            actual = ChatClient.completionsURL("http://10.0.2.2:11434/v1/")
        )
    }
}
