package com.jocmp.capy.ai

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DiscussionTest {
    private val hackerNewsJSON = """
        {
          "id": 42,
          "title": "A story",
          "author": "submitter",
          "points": 120,
          "text": null,
          "children": [
            {
              "author": "alice",
              "points": 30,
              "text": "<p>I <i>like</i> the approach.</p>",
              "children": [
                { "author": "bob", "points": 5, "text": "It breaks on Windows.", "children": [] }
              ]
            },
            { "author": null, "points": null, "text": "", "children": [] }
          ]
        }
    """.trimIndent()

    private val lobstersJSON = """
        {
          "title": "A story",
          "comments": [
            { "comment": "<p>ignored</p>", "comment_plain": "Benchmarks look suspicious.", "commenting_user": "carol", "score": 9 },
            { "comment": "<p>Only <b>html</b> here</p>", "comment_plain": null, "commenting_user": "dave", "score": 1 }
          ]
        }
    """.trimIndent()

    @Test
    fun apiURL_detectsHackerNews() {
        assertEquals(
            expected = "https://hn.algolia.com/api/v1/items/44712",
            actual = Discussion.apiURL("https://news.ycombinator.com/item?id=44712")
        )
    }

    @Test
    fun apiURL_detectsLobsters() {
        assertEquals(
            expected = "https://lobste.rs/s/abc123.json",
            actual = Discussion.apiURL("https://lobste.rs/s/abc123/some-story-slug")
        )
    }

    @Test
    fun apiURL_ignoresOtherHosts() {
        assertNull(Discussion.apiURL("https://example.com/comments/1"))
        assertNull(Discussion.apiURL("https://news.ycombinator.com/newest"))
        assertNull(Discussion.apiURL("https://news.ycombinator.com/item?id=../../etc"))
        assertNull(Discussion.apiURL(null))
        assertNull(Discussion.apiURL("javascript:alert(1)"))
    }

    @Test
    fun fetch_flattensHackerNewsComments() = runTest {
        val interceptor = RecordingInterceptor(hackerNewsJSON)

        val thread = Discussion.fetch(
            "https://news.ycombinator.com/item?id=42",
            interceptor.client()
        ).getOrThrow()

        assertEquals(
            expected = "[alice, 30 points] I like the approach.\n[bob, 5 points] It breaks on Windows.",
            actual = thread
        )
        assertEquals(
            expected = "https://hn.algolia.com/api/v1/items/42",
            actual = interceptor.requests.single().url.toString()
        )
    }

    @Test
    fun fetch_readsLobstersComments() = runTest {
        val interceptor = RecordingInterceptor(lobstersJSON)

        val thread = Discussion.fetch(
            "https://lobste.rs/s/abc123/a-story",
            interceptor.client()
        ).getOrThrow()

        assertContains(thread.orEmpty(), "[carol, 9 points] Benchmarks look suspicious.")
        assertContains(thread.orEmpty(), "[dave, 1 points] Only html here")
        assertFalse(thread.orEmpty().contains("<b>"))
    }

    @Test
    fun fetch_returnsNullForUnsupportedHost() = runTest {
        val interceptor = RecordingInterceptor(hackerNewsJSON)

        val result = Discussion.fetch("https://example.com/story", interceptor.client())

        assertNull(result.getOrThrow())
        assertEquals(expected = 0, actual = interceptor.requests.size)
    }

    @Test
    fun fetch_failsOnHttpError() = runTest {
        val interceptor = RecordingInterceptor("nope", code = 500)

        val result = Discussion.fetch(
            "https://news.ycombinator.com/item?id=42",
            interceptor.client()
        )

        assertEquals(expected = true, actual = result.isFailure)
    }
}
