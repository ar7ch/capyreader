package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.ai.Summary
import com.jocmp.capy.db.Database
import com.jocmp.capy.fixtures.ArticleFixture
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleSummaryRecordsTest {
    private lateinit var database: Database

    @Before
    fun setup() {
        database = InMemoryDatabaseProvider.build("777")
    }

    @Test
    fun find_returnsNullWhenMissing() = runTest {
        assertNull(ArticleSummaryRecords(database).find("missing"))
    }

    @Test
    fun save_roundTripsTheSummary() = runTest {
        val article = ArticleFixture(database).create()
        val records = ArticleSummaryRecords(database)

        records.save(
            articleID = article.id,
            summary = Summary(article = "Short summary", discussion = "- One highlight"),
            model = "qwen3:8b",
        )

        assertEquals(
            expected = Summary(article = "Short summary", discussion = "- One highlight"),
            actual = records.find(article.id)
        )
    }

    @Test
    fun save_overwritesAnEarlierSummary() = runTest {
        val article = ArticleFixture(database).create()
        val records = ArticleSummaryRecords(database)

        records.save(article.id, Summary(article = "First", discussion = "- old"), model = "a")
        records.save(article.id, Summary(article = "Second", discussion = null), model = "b")

        assertEquals(
            expected = Summary(article = "Second", discussion = null),
            actual = records.find(article.id)
        )
    }

    @Test
    fun deletingTheArticleRemovesTheSummary() = runTest {
        val article = ArticleFixture(database).create()
        val records = ArticleSummaryRecords(database)

        records.save(article.id, Summary(article = "Short summary"), model = "qwen3:8b")

        database.articlesQueries.deletePageByID(article.id)

        assertNull(records.find(article.id))
    }
}
