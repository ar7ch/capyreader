package com.jocmp.aiprofiles

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

class NoProfileError : Exception("No AI profile configured")

class AllProfilesFailedError(val failures: List<Pair<AiProfile, Throwable>>) : Exception(
    failures.joinToString(separator = "; ") { (profile, error) -> "${profile.label}: ${error.message}" }
)

/**
 * Calls [attempt] on the usable [profiles] in order until one succeeds. The first profile gets
 * [FIRST_PROFILE_ATTEMPTS] tries with growing waits between them, every later one a single try.
 * Any failure moves on; cancellation does not.
 */
suspend fun <T> firstSuccess(
    profiles: List<AiProfile>,
    attempt: suspend (AiProfile) -> Result<T>,
): Result<T> {
    val usable = profiles.filter { it.usable }

    if (usable.isEmpty()) {
        return Result.failure(NoProfileError())
    }

    val failures = mutableListOf<Pair<AiProfile, Throwable>>()

    usable.forEachIndexed { index, profile ->
        val tries = if (index == 0) FIRST_PROFILE_ATTEMPTS else 1
        var lastError: Throwable? = null

        repeat(tries) { n ->
            if (n > 0) {
                delay(RETRY_WAIT_MS * n)
            }

            val result = attempt(profile)

            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error

                lastError = error
            } ?: return result
        }

        lastError?.let { failures.add(profile to it) }
    }

    return Result.failure(AllProfilesFailedError(failures))
}

private const val FIRST_PROFILE_ATTEMPTS = 3

private const val RETRY_WAIT_MS = 1_000L
