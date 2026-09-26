package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.capyreader.app.R
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Article
import com.jocmp.capy.ai.ArticleChat
import com.jocmp.capy.ai.ChatMessage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

/**
 * A follow-up chat about the open article. Replies are untrusted model text and render through
 * [SummaryText] as plain text. The conversation lives only while the dialog is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleChatDialog(
    article: Article,
    onDismissRequest: () -> Unit,
    articleChat: ArticleChat = koinInject(),
    appPreferences: AppPreferences = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val send = {
        val question = input.trim()

        if (question.isNotBlank() && !sending) {
            val history = messages.toList()
            input = ""
            error = null
            sending = true
            messages.add(ChatMessage(role = ROLE_USER, content = question))
            val replyIndex = messages.size
            messages.add(ChatMessage(role = ROLE_ASSISTANT, content = ""))

            scope.launch {
                val settings = appPreferences.aiOptions.settings()

                articleChat.reply(
                    settings = settings,
                    article = article,
                    pageHTML = article.content,
                    history = history,
                    question = question,
                    onDelta = { partial ->
                        messages[replyIndex] = ChatMessage(role = ROLE_ASSISTANT, content = partial)
                    },
                ).onSuccess { answer ->
                    messages[replyIndex] = ChatMessage(role = ROLE_ASSISTANT, content = answer)
                }.onFailure {
                    messages.removeAt(replyIndex)
                    error = it.message.orEmpty()
                }

                sending = false
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.article_chat_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.article_chat_close),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                ChatInput(
                    value = input,
                    onValueChange = { input = it },
                    onSend = send,
                    sending = sending,
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.article_chat_placeholder),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                items(messages.size) { index ->
                    ChatBubble(messages[index])
                }

                error?.let { message ->
                    item {
                        Text(
                            text = stringResource(R.string.article_chat_error, message),
                            style = typography.bodyMedium,
                            color = colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    if (message.role == ROLE_USER) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.content,
                    style = typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    } else if (message.content.isBlank()) {
        CircularProgressIndicator(modifier = Modifier.padding(vertical = 4.dp))
    } else {
        SummaryText(text = message.content, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.imePadding()) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.article_chat_input_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                trailingIcon = {
                    IconButton(onClick = onSend, enabled = value.isNotBlank() && !sending) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.article_chat_send),
                        )
                    }
                },
            )
        }
    }
}
