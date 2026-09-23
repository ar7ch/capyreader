package com.jocmp.capy.ai

import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.fixtures.AccountFixture
import com.jocmp.capy.fixtures.ArticleFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryQueueTest {
    @JvmField
    @Rule
    val folder = TemporaryFolder()

    private val settings = AiSettings(
        baseURL = "https://api.example.com/v1",
        apiKey = "",
        model = "test-model",
    )

    private lateinit var account: Account
    private lateinit var articleFixture: ArticleFixture
    private lateinit var summarizer: Summarizer

    private val summarized = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        account = AccountFixture.create(parentFolder = folder)
        articleFixture = ArticleFixture(account.database)
        summarizer = mockk()
    }

    @Test
    fun enqueue_summarizesInOrder() = runTest {
        val articleIDs = createArticles("1", "2", "3")
        succeedAll()
        val queue = buildQueue()

        queue.enqueue(articleIDs)
        queue.awaitSettled(articleIDs)

        assertEquals(expected = articleIDs, actual = summarized)
        assertNull(queue.progress.value)
        articleIDs.forEach { articleID ->
            assertEquals(
                expected = SummaryStatus.Done(Summary(article = "Summary of $articleID")),
                actual = queue.states.value[articleID],
            )
        }
    }

    @Test
    fun prioritize_movesAnArticleToTheFront() = runTest {
        val articleIDs = createArticles("1", "2", "3")
        val gate = CompletableDeferred<Unit>()
        succeedAll(holdBack = "1", gate = gate)
        val queue = buildQueue()

        queue.enqueue(articleIDs)
        queue.states.first { it["1"] == SummaryStatus.Running }

        queue.prioritize("3")
        gate.complete(Unit)
        queue.awaitSettled(articleIDs)

        assertEquals(expected = listOf("1", "3", "2"), actual = summarized)
    }

    @Test
    fun states_reportsSummariesAlreadyOnDisk() = runTest {
        val articleIDs = createArticles("1")
        account.saveSummary(
            articleID = "1",
            summary = Summary(article = "Stored summary"),
            model = "old-model",
        )
        succeedAll()
        val queue = buildQueue()

        queue.states.first { it["1"] == SummaryStatus.Stored }

        queue.enqueue(articleIDs)
        advanceUntilIdle()

        assertEquals(expected = SummaryStatus.Stored, actual = queue.states.value["1"])
        assertNull(queue.progress.value)
        coVerify(exactly = 0) { summarizer.summarize(any(), any(), any()) }
    }

    @Test
    fun prioritize_readsAStoredSummaryWhileTheQueueIsBusy() = runTest {
        createArticles("1", "2")
        val stored = Summary(article = "Stored summary", discussion = "Stored discussion")
        account.saveSummary(articleID = "1", summary = stored, model = "old-model")
        val gate = CompletableDeferred<Unit>()
        succeedAll(holdBack = "2", gate = gate)
        val queue = buildQueue()

        queue.states.first { it["1"] == SummaryStatus.Stored }

        queue.enqueue(listOf("2"))
        queue.states.first { it["2"] == SummaryStatus.Running }

        queue.prioritize("1")

        assertEquals(
            expected = SummaryStatus.Done(stored),
            actual = queue.states.first { it["1"] is SummaryStatus.Done }["1"],
        )
        assertEquals(expected = SummaryStatus.Running, actual = queue.states.value["2"])
        coVerify(exactly = 0) { summarizer.summarize(any(), match { it.id == "1" }, any()) }

        gate.complete(Unit)
    }

    @Test
    fun enqueue_continuesAfterAFailure() = runTest {
        val articleIDs = createArticles("1", "2")
        coEvery { summarizer.summarize(any(), any(), any()) } coAnswers {
            val articleID = secondArg<Article>().id
            summarized.add(articleID)

            if (articleID == "1") {
                Result.failure(IOException("Connection reset"))
            } else {
                Result.success(Summary(article = "Summary of $articleID"))
            }
        }
        val queue = buildQueue()

        queue.enqueue(articleIDs)
        queue.awaitSettled(articleIDs)

        assertEquals(
            expected = SummaryStatus.Failed("Connection reset"),
            actual = queue.states.value["1"],
        )
        assertEquals(
            expected = SummaryStatus.Done(Summary(article = "Summary of 2")),
            actual = queue.states.value["2"],
        )
        assertEquals(expected = listOf("1", "2"), actual = summarized)
    }

    @Test
    fun cancelAll_emptiesTheQueue() = runTest {
        val articleIDs = createArticles("1", "2", "3")
        val gate = CompletableDeferred<Unit>()
        succeedAll(holdBack = "1", gate = gate)
        val queue = buildQueue()

        queue.enqueue(articleIDs)
        queue.states.first { it["1"] == SummaryStatus.Running }

        assertEquals(expected = SummaryProgress(done = 0, total = 3), actual = queue.progress.value)

        queue.cancelAll()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(queue.progress.value)
        assertTrue(queue.states.value.isEmpty())
        assertTrue(summarized.isEmpty())
    }

    private fun TestScope.buildQueue() = SummaryQueue(
        account = account,
        summarizer = summarizer,
        settings = { settings },
        scope = backgroundScope,
    )

    private suspend fun SummaryQueue.awaitSettled(articleIDs: List<String>) {
        states.first { current ->
            articleIDs.all { articleID ->
                val status = current[articleID]

                status is SummaryStatus.Done || status is SummaryStatus.Failed
            }
        }
    }

    /**
     * The articles have no URL so the queue never reaches out for the full page.
     */
    private fun createArticles(vararg articleIDs: String): List<String> {
        return articleIDs.map { articleFixture.create(id = it, url = "").id }
    }

    private fun succeedAll(holdBack: String? = null, gate: CompletableDeferred<Unit>? = null) {
        coEvery { summarizer.summarize(any(), any(), any()) } coAnswers {
            val articleID = secondArg<Article>().id

            if (articleID == holdBack) {
                gate?.await()
            }

            summarized.add(articleID)

            Result.success(Summary(article = "Summary of $articleID"))
        }
    }
}
