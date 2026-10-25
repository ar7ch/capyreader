package com.jocmp.capy.ai

import com.jocmp.capy.common.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException

/**
 * Fetches a comment thread for the discussion sites Capy knows about. Everything returned here is
 * untrusted text written by strangers, so callers sanitize it before it reaches a model.
 */
object Discussion {
    private const val MAX_COMMENTS = 60
    private const val MAX_LENGTH = 12_000

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns null on success when the URL points at a site without a comment API, and a blank
     * string when the thread has no comments yet.
     */
    suspend fun fetch(commentsURL: String?, httpClient: OkHttpClient): Result<String?> {
        val apiURL = apiURL(commentsURL) ?: return Result.success(null)

        val request = Request.Builder()
            .url(apiURL)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            val response = httpClient.newCall(request).await()
            val body = response.body.string()

            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP ${response.code}"))
            }

            val thread = if (apiURL.startsWith(HN_API)) {
                formatHackerNews(json.decodeFromString<HNItem>(body))
            } else {
                formatLobsters(json.decodeFromString<LobstersStory>(body))
            }

            Result.success(thread)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** True when the comments URL points at a site with a comment API. */
    fun supports(commentsURL: String?): Boolean = apiURL(commentsURL) != null

    internal fun apiURL(commentsURL: String?): String? {
        val url = commentsURL?.trim().orEmpty().toHttpUrlOrNull() ?: return null

        if (url.host == "news.ycombinator.com" && url.encodedPath == "/item") {
            val id = url.queryParameter("id")?.takeIf { it.all(Char::isDigit) } ?: return null

            return "$HN_API$id"
        }

        if (url.host == "lobste.rs" && url.pathSegments.size >= 2 && url.pathSegments[0] == "s") {
            val short = url.pathSegments[1].takeIf { it.isNotBlank() && it.all(Char::isLetterOrDigit) }
                ?: return null

            return "https://lobste.rs/s/$short.json"
        }

        return null
    }

    private fun formatHackerNews(item: HNItem): String {
        val lines = mutableListOf<String>()

        fun visit(node: HNItem) {
            if (lines.size >= MAX_COMMENTS) {
                return
            }

            val text = plainText(node.text)

            if (text.isNotBlank()) {
                lines.add(line(score = node.points, text = text))
            }

            node.children.forEach(::visit)
        }

        item.children.forEach(::visit)

        return join(lines)
    }

    private fun formatLobsters(story: LobstersStory): String {
        val lines = story.comments.take(MAX_COMMENTS).mapNotNull { comment ->
            val text = comment.comment_plain?.trim().orEmpty().ifBlank { plainText(comment.comment) }

            if (text.isBlank()) {
                null
            } else {
                line(score = comment.score, text = text)
            }
        }

        return join(lines)
    }

    // Usernames stay out of the prompt: summaries should not name commenters, and a name is one
    // more piece of untrusted text for the model to echo.
    private fun line(score: Int?, text: String): String {
        val points = score?.let { "[$it points] " }.orEmpty()

        return "- $points$text"
    }

    private fun join(lines: List<String>): String {
        val joined = StringBuilder()

        lines.forEach { line ->
            if (joined.length + line.length + 1 <= MAX_LENGTH) {
                joined.append(line).append("\n")
            } else if (joined.isEmpty()) {
                // A thread whose first comment alone is too long still has comments.
                joined.append(line.take(MAX_LENGTH - 1)).append("\n")
            }
        }

        return joined.toString().trim()
    }

    private fun plainText(html: String?): String {
        if (html.isNullOrBlank()) {
            return ""
        }

        return Jsoup.parse(html, "", Parser.htmlParser()).text().trim()
    }

    private const val HN_API = "https://hn.algolia.com/api/v1/items/"
}

@Serializable
internal data class HNItem(
    val author: String? = null,
    val points: Int? = null,
    val text: String? = null,
    val children: List<HNItem> = emptyList(),
)

@Serializable
internal data class LobstersStory(
    val title: String? = null,
    val comments: List<LobstersComment> = emptyList(),
)

@Serializable
internal data class LobstersComment(
    val comment: String? = null,
    val comment_plain: String? = null,
    val commenting_user: String? = null,
    val score: Int? = null,
)
