package com.capyreader.app.ui.articles.detail

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTextTest {
    @Test
    fun `reads a bullet from every marker a model might use`() {
        val blocks = parseSummaryBlocks(
            """
            * Star point
            - Dash point
            • Real bullet point
            2. Numbered point
            """.trimIndent()
        )

        assertEquals(
            listOf(
                SummaryBlock.Bullet("Star point"),
                SummaryBlock.Bullet("Dash point"),
                SummaryBlock.Bullet("Real bullet point"),
                SummaryBlock.Bullet("Numbered point"),
            ),
            blocks,
        )
    }

    @Test
    fun `joins wrapped lines into one paragraph`() {
        val blocks = parseSummaryBlocks(
            "The release adds\nincremental backups.\n\n## Second thing\n- A point"
        )

        assertEquals(
            listOf(
                SummaryBlock.Paragraph("The release adds incremental backups."),
                SummaryBlock.Paragraph("Second thing"),
                SummaryBlock.Bullet("A point"),
            ),
            blocks,
        )
    }

    @Test
    fun `marks bold spans and drops leftover markers`() {
        val annotated = annotateSummary("**Postgres 18** adds `incremental` backups.")

        assertEquals("Postgres 18 adds incremental backups.", annotated.text)

        val bold = annotated.spanStyles.single()

        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(0, bold.start)
        assertEquals("Postgres 18".length, bold.end)
    }
}
