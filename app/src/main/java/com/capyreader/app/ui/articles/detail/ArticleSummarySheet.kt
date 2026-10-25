package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.jocmp.capy.ai.Summary
import com.jocmp.capy.ai.SummaryStage

/**
 * Model output is untrusted text. [SummaryText] lays it out as paragraphs and bullet points, and
 * nothing in it can act as markup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleSummarySheet(
    state: SummaryState,
    hasDiscussionSite: Boolean,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onChat: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (state) {
                is SummaryState.Loading, SummaryState.Idle -> {
                    val loading = state as? SummaryState.Loading

                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = stringResource(stageLabel(loading?.stage)),
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )

                    val partial = loading?.partial

                    if (partial != null) {
                        SummaryBody(summary = partial, hasDiscussionSite = false)
                    }
                }

                is SummaryState.Error -> {
                    Text(
                        text = stringResource(R.string.article_summary_error),
                        style = typography.titleMedium,
                    )
                    if (state.message.isNotBlank()) {
                        Text(
                            text = state.message,
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.article_summary_retry))
                    }
                }

                is SummaryState.Success -> {
                    SummaryBody(summary = state.summary, hasDiscussionSite = hasDiscussionSite)

                    Text(
                        text = stringResource(R.string.article_summary_disclaimer),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRegenerate) {
                        Text(stringResource(R.string.article_summary_regenerate))
                    }
                }
            }

            TextButton(onClick = onChat) {
                Text(stringResource(R.string.article_chat_open))
            }
        }
    }
}

@Composable
private fun SummaryBody(summary: Summary, hasDiscussionSite: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SummarySection(title = stringResource(R.string.article_summary_title)) {
            SummaryText(text = summary.article)
        }

        val discussion = summary.discussion

        if (discussion != null) {
            SummarySection(title = stringResource(R.string.article_summary_discussion_title)) {
                SummaryText(text = discussion)
            }
        } else if (hasDiscussionSite) {
            SummarySection(title = stringResource(R.string.article_summary_discussion_title)) {
                Text(
                    text = stringResource(R.string.article_summary_discussion_unavailable),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

private fun stageLabel(stage: SummaryStage?): Int = when (stage) {
    null -> R.string.article_summary_stage_queued
    SummaryStage.FETCHING_PAGE -> R.string.article_summary_stage_fetching_page
    SummaryStage.SUMMARIZING_ARTICLE -> R.string.article_summary_stage_summarizing_article
    SummaryStage.FETCHING_COMMENTS -> R.string.article_summary_stage_fetching_comments
    SummaryStage.SUMMARIZING_COMMENTS -> R.string.article_summary_stage_summarizing_discussion
}
