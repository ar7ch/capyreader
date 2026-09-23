package com.jocmp.capy.ai

import org.jsoup.Jsoup

/**
 * Reduces a fetched page to the text a summary can be built from.
 */
object ReadableText {
    private const val NOISE = "script, style, noscript, iframe, svg, form, nav, header, footer, aside"

    const val MAX_LENGTH = 12_000

    fun fromHTML(html: String, baseURL: String = ""): String {
        if (html.isBlank()) {
            return ""
        }

        val document = Jsoup.parse(html, baseURL)

        document.select(NOISE).remove()

        val main = document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()
            ?: return ""

        return main.text().trim().take(MAX_LENGTH)
    }
}
