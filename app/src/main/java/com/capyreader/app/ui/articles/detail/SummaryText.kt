package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.collectChangesWithDefault
import com.jocmp.capy.articles.FontOption
import org.koin.compose.koinInject

sealed interface SummaryBlock {
    data class Paragraph(val text: String) : SummaryBlock

    data class Bullet(val text: String) : SummaryBlock
}

/**
 * Renders one model answer as paragraphs and bullet points in the reader's font.
 *
 * The text is untrusted, so only the handful of markers below mean anything: nothing here can turn
 * into a link, an image, or markup of any kind.
 */
@Composable
fun SummaryText(
    text: String,
    modifier: Modifier = Modifier,
    appPreferences: AppPreferences = koinInject(),
) {
    val fontOption by appPreferences.readerOptions.fontFamily.collectChangesWithDefault()
    val blocks = remember(text) { parseSummaryBlocks(text) }
    val style = typography.bodyLarge.copy(
        fontFamily = findFont(fontOption) ?: FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            val gap = when {
                index == 0 -> 0.dp
                block is SummaryBlock.Paragraph -> PARAGRAPH_GAP
                else -> BULLET_GAP
            }

            when (block) {
                is SummaryBlock.Paragraph -> Text(
                    text = annotateSummary(block.text),
                    style = style,
                    modifier = Modifier.padding(top = gap),
                )

                is SummaryBlock.Bullet -> Row(modifier = Modifier.padding(top = gap)) {
                    Text(
                        text = BULLET,
                        style = style,
                        modifier = Modifier.width(BULLET_INDENT),
                    )
                    Text(
                        text = annotateSummary(block.text),
                        style = style,
                    )
                }
            }
        }
    }
}

/**
 * Splits an answer into paragraphs and bullet points. A line that opens with a bullet or a number
 * is a point of its own; the lines in between join into one paragraph, the way a hard wrapped
 * paragraph is meant to read.
 */
fun parseSummaryBlocks(text: String): List<SummaryBlock> {
    val blocks = mutableListOf<SummaryBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        if (paragraph.isNotBlank()) {
            blocks.add(SummaryBlock.Paragraph(paragraph.toString().trim()))
        }

        paragraph.clear()
    }

    text.lines().forEach { line ->
        val marker = BULLET_MARKER.find(line)

        when {
            line.isBlank() -> flush()

            marker != null -> {
                flush()
                blocks.add(SummaryBlock.Bullet(clean(line.removeRange(marker.range))))
            }

            else -> {
                if (paragraph.isNotEmpty()) {
                    paragraph.append(' ')
                }

                paragraph.append(clean(line))
            }
        }
    }

    flush()

    return blocks
}

/** Turns `**bold**` into bold text and drops the markers a model throws in out of habit. */
fun annotateSummary(text: String): AnnotatedString = buildAnnotatedString {
    var plainFrom = 0

    BOLD_SPAN.findAll(text).forEach { match ->
        append(plain(text.substring(plainFrom, match.range.first)))

        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(plain(match.groupValues[1]))
        }

        plainFrom = match.range.last + 1
    }

    append(plain(text.substring(plainFrom)))
}

private fun clean(line: String): String {
    return line.replace(LEADING_MARKERS, "").trim()
}

private fun plain(text: String): String {
    return text.replace(STRAY_MARKERS, "")
}

private val BULLET_MARKER = Regex("^\\s*([*\\-•]|\\d+[.)])\\s+")

private val LEADING_MARKERS = Regex("^\\s*(?:#{1,6}\\s*|>\\s*)+")

private val BOLD_SPAN = Regex("\\*\\*(.+?)\\*\\*")

/** Leftover emphasis and code markers. Other punctuation is part of the text. */
private val STRAY_MARKERS = Regex("[*`]")

private const val BULLET = "•"

private val BULLET_INDENT = 16.dp

private val PARAGRAPH_GAP = 8.dp

private val BULLET_GAP = 4.dp
