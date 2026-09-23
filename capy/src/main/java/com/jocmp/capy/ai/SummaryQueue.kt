package com.jocmp.capy.ai

import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SummaryStatus {
    data object Queued : SummaryStatus

    data object Running : SummaryStatus

    data class Done(val summary: Summary) : SummaryStatus

    data class Failed(val message: String) : SummaryStatus
}

data class SummaryProgress(
    val done: Int,
    val total: Int,
)

/**
 * Summarizes articles one at a time in the order they were asked for. Local models answer one
 * request at a time anyway, and a queue keeps the list and the open article from competing.
 *
 * A stored summary is handed back without calling the model. A failure marks its own article and
 * the queue moves on to the next one.
 *
 * NOTE: the queue lives only as long as the process. Nothing about it is written down, so whatever
 * is still waiting when the app goes away is dropped.
 */
class SummaryQueue(
    private val account: Account,
    private val summarizer: Summarizer,
    private val settings: () -> AiSettings,
    private val scope: CoroutineScope,
) {
    private val lock = Any()

    private val pending = ArrayDeque<String>()

    private val regenerating = mutableSetOf<String>()

    private var worker: Job? = null

    private var running: String? = null

    private var completed = 0

    private val _states = MutableStateFlow<Map<String, SummaryStatus>>(emptyMap())

    val states: StateFlow<Map<String, SummaryStatus>> = _states.asStateFlow()

    private val _progress = MutableStateFlow<SummaryProgress?>(null)

    val progress: StateFlow<SummaryProgress?> = _progress.asStateFlow()

    /**
     * Appends ids to the back of the queue. Articles already waiting, running, or summarized are
     * left alone.
     */
    fun enqueue(articleIDs: List<String>) {
        synchronized(lock) {
            articleIDs.forEach { articleID ->
                if (!isTracked(articleID)) {
                    pending.addLast(articleID)
                    setStatus(articleID, SummaryStatus.Queued)
                }
            }

            publishProgress()
        }

        start()
    }

    /**
     * Puts one article at the front of the queue. An article that already has a summary stays
     * where it is unless [regenerate] asks for a new one.
     */
    fun prioritize(articleID: String, regenerate: Boolean = false) {
        synchronized(lock) {
            val status = _states.value[articleID]

            if (status == SummaryStatus.Running || (status is SummaryStatus.Done && !regenerate)) {
                return
            }

            if (regenerate) {
                regenerating.add(articleID)
            }

            pending.remove(articleID)
            pending.addFirst(articleID)
            setStatus(articleID, SummaryStatus.Queued)
            publishProgress()
        }

        start()
    }

    /** Empties the queue and stops the article being summarized right now. */
    fun cancelAll() {
        val current = synchronized(lock) {
            pending.clear()
            regenerating.clear()
            running = null
            completed = 0
            _progress.value = null
            _states.value = _states.value.filterValues { it !is SummaryStatus.Queued && it !is SummaryStatus.Running }

            worker.also { worker = null }
        }

        current?.cancel()
    }

    private fun start() {
        synchronized(lock) {
            if (worker != null) {
                return
            }

            worker = scope.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val articleID = next() ?: return

            summarize(articleID)

            finish(articleID)
        }
    }

    private fun next(): String? = synchronized(lock) {
        val articleID = pending.removeFirstOrNull()

        if (articleID == null) {
            worker = null
            running = null
            completed = 0
            _progress.value = null
        } else {
            running = articleID
            setStatus(articleID, SummaryStatus.Running)
            publishProgress()
        }

        articleID
    }

    private fun finish(articleID: String) = synchronized(lock) {
        if (running == articleID) {
            running = null
            completed += 1
        }

        publishProgress()
    }

    private suspend fun summarize(articleID: String) {
        val regenerate = synchronized(lock) { regenerating.remove(articleID) }

        try {
            if (!regenerate) {
                val stored = account.findSummary(articleID)

                if (stored != null) {
                    setStatus(articleID, SummaryStatus.Done(stored))
                    return
                }
            }

            val article = account.findArticle(articleID)

            if (article == null) {
                setStatus(articleID, SummaryStatus.Failed(MISSING_ARTICLE_MESSAGE))
                return
            }

            val pageHTML = if (article.fullContent == Article.FullContentState.LOADED) {
                article.content
            } else {
                account.fetchFullContent(article).getOrNull()
            }

            val current = settings()

            summarizer.summarize(settings = current, article = article, pageHTML = pageHTML).fold(
                onSuccess = { summary ->
                    account.saveSummary(articleID, summary, model = current.model)
                    setStatus(articleID, SummaryStatus.Done(summary))
                },
                onFailure = { error -> fail(articleID, error) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(articleID, e)
        }
    }

    private fun fail(articleID: String, error: Throwable) {
        CapyLog.warn(
            "summary_queue",
            mapOf("error_type" to error::class.simpleName, "error_message" to error.message)
        )

        setStatus(articleID, SummaryStatus.Failed(error.message.orEmpty()))
    }

    private fun setStatus(articleID: String, status: SummaryStatus) = synchronized(lock) {
        _states.value = _states.value + (articleID to status)
    }

    private fun isTracked(articleID: String): Boolean {
        val status = _states.value[articleID]

        return status == SummaryStatus.Queued ||
                status == SummaryStatus.Running ||
                status is SummaryStatus.Done
    }

    private fun publishProgress() {
        val remaining = pending.size + if (running == null) 0 else 1

        _progress.value = if (remaining == 0) {
            null
        } else {
            SummaryProgress(done = completed, total = completed + remaining)
        }
    }

    private companion object {
        const val MISSING_ARTICLE_MESSAGE = "The article could not be found"
    }
}
