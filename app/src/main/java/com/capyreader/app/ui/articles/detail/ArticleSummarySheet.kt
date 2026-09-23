package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.ui.articles.ArticleScreenViewModel.SummaryState

/**
 * Model output is untrusted text. It is rendered with [Text] so nothing in it can act as markup.
 */
@Composable
fun ArticleSummarySheet(
    state: SummaryState,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
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
                    Text(
                        text = stringResource(R.string.article_summary_title),
                        style = typography.titleMedium,
                    )
                    Text(text = state.summary.article)

                    state.summary.discussion?.let { discussion ->
                        Text(
                            text = stringResource(R.string.article_summary_discussion_title),
                            style = typography.titleMedium,
                        )
                        Text(text = discussion)
                    }

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
        }
    }
}
