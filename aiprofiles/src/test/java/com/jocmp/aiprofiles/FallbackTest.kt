package com.jocmp.aiprofiles

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FallbackTest {
    private val first = AiProfile(name = "first", baseUrl = "https://a.example/v1", model = "a")
    private val second = AiProfile(name = "second", baseUrl = "https://b.example/v1", model = "b")
    private val third = AiProfile(name = "third", baseUrl = "https://c.example/v1", model = "c")

    @Test
    fun retriesTheFirstProfileThenMovesOnOncePerProfile() = runTest {
        val calls = mutableListOf<String>()

        val result = firstSuccess(listOf(first, second, third)) { profile ->
            calls.add(profile.name)
            if (profile == third) Result.success("ok") else Result.failure(Exception("down"))
        }

        assertEquals(expected = "ok", actual = result.getOrThrow())
        assertEquals(expected = listOf("first", "first", "first", "second", "third"), actual = calls)
        assertEquals(expected = 3_000L, actual = currentTime)
    }

    @Test
    fun stopsAtTheFirstSuccess() = runTest {
        var calls = 0

        val result = firstSuccess(listOf(first, second)) {
            calls++
            if (calls == 2) Result.success(it.name) else Result.failure(Exception("flaky"))
        }

        assertEquals(expected = "first", actual = result.getOrThrow())
        assertEquals(expected = 2, actual = calls)
    }

    @Test
    fun namesEveryProfileWhenAllFail() = runTest {
        val result = firstSuccess(listOf(first, second)) { Result.failure<String>(Exception("${it.name} down")) }

        val error = assertIs<AllProfilesFailedError>(result.exceptionOrNull())
        assertEquals(expected = "first: first down; second: second down", actual = error.message)
    }

    @Test
    fun skipsUnusableProfiles() = runTest {
        val calls = mutableListOf<String>()
        val blank = AiProfile(name = "blank", baseUrl = "", model = "x")

        firstSuccess(listOf(blank, second)) {
            calls.add(it.name)
            Result.success(Unit)
        }

        assertEquals(expected = listOf("second"), actual = calls)
    }

    @Test
    fun failsWithoutUsableProfiles() = runTest {
        val result = firstSuccess(emptyList()) { Result.success(Unit) }

        assertIs<NoProfileError>(result.exceptionOrNull())
    }

    @Test
    fun cancellationStopsTheWalk() = runTest {
        var calls = 0

        assertFailsWith<CancellationException> {
            firstSuccess(listOf(first, second)) {
                calls++
                Result.failure<Unit>(CancellationException("stop"))
            }
        }

        assertEquals(expected = 1, actual = calls)
    }

    @Test
    fun readsTheOldBaseURLKey() {
        val stored = """[{"name":"Old","baseURL":"https://x.example/v1","apiKey":"k","model":"m","extra":1}]"""

        assertEquals(
            expected = listOf(AiProfile(name = "Old", baseUrl = "https://x.example/v1", apiKey = "k", model = "m")),
            actual = AiProfile.decodeList(stored),
        )
    }
}
