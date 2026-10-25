package com.capyreader.app.ui.articles.detail

import com.jocmp.capy.ai.Summary
import com.jocmp.capy.ai.SummaryStage
import com.jocmp.capy.ai.SummaryStatus

sealed interface SummaryState {
    data object Idle : SummaryState

    /** [partial] is the summary as far as the model has written it, when there is any yet. */
    data class Loading(
        val partial: Summary? = null,
        val stage: SummaryStage? = null,
    ) : SummaryState

    data class Success(val summary: Summary) : SummaryState

    data class Error(val message: String) : SummaryState
}

fun summaryState(status: SummaryStatus?): SummaryState {
    return when (status) {
        // A stored summary is only read once asked for, so until then nothing is in progress.
        null,
        SummaryStatus.Stored -> SummaryState.Idle
        SummaryStatus.Queued -> SummaryState.Loading()
        is SummaryStatus.Running -> SummaryState.Loading(
            partial = status.partial,
            stage = status.stage,
        )
        is SummaryStatus.Done -> SummaryState.Success(status.summary)
        is SummaryStatus.Failed -> SummaryState.Error(status.message)
    }
}
