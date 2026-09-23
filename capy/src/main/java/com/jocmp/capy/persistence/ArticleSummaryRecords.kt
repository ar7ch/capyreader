package com.jocmp.capy.persistence

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.jocmp.capy.ai.Summary
import com.jocmp.capy.common.TimeHelpers.nowUTC
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.db.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class ArticleSummaryRecords internal constructor(
    private val database: Database
) {
    /** The articles with a summary on disk, so a list can show which ones are already done. */
    fun articleIDs(): Flow<List<String>> {
        return database.article_summariesQueries
            .allArticleIDs()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun find(articleID: String): Summary? = withIOContext {
        database.article_summariesQueries
            .findBy(articleID) { summary, discussion, _, _ ->
                Summary(article = summary, discussion = discussion)
            }
            .executeAsOneOrNull()
    }

    suspend fun save(articleID: String, summary: Summary, model: String) = withIOContext {
        database.article_summariesQueries.upsert(
            article_id = articleID,
            summary = summary.article,
            discussion = summary.discussion,
            model = model,
            created_at = nowUTC().toEpochSecond(),
        )
    }

    suspend fun delete(articleID: String) = withIOContext {
        database.article_summariesQueries.deleteBy(articleID)
    }
}
