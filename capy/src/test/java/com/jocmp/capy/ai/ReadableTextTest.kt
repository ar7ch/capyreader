package com.jocmp.capy.ai

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReadableTextTest {
    @Test
    fun fromHTML_prefersTheArticleElementAndDropsChrome() {
        val html = """
            <html><body>
              <nav>Home About</nav>
              <script>alert('hi')</script>
              <article><p>The release adds incremental backups.</p></article>
              <footer>Copyright</footer>
            </body></html>
        """.trimIndent()

        val text = ReadableText.fromHTML(html)

        assertEquals(expected = "The release adds incremental backups.", actual = text)
    }

    @Test
    fun fromHTML_fallsBackToBody() {
        val text = ReadableText.fromHTML("<html><body><div>Plain body text</div></body></html>")

        assertEquals(expected = "Plain body text", actual = text)
    }

    @Test
    fun fromHTML_dropsStyleAndNoscript() {
        val html = "<body><style>.a{color:red}</style><noscript>Enable JS</noscript><p>Real text</p></body>"

        val text = ReadableText.fromHTML(html)

        assertEquals(expected = "Real text", actual = text)
        assertFalse(text.contains("Enable JS"))
    }

    @Test
    fun fromHTML_capsLength() {
        val html = "<body><p>${"word ".repeat(10_000)}</p></body>"

        assertEquals(expected = ReadableText.MAX_LENGTH, actual = ReadableText.fromHTML(html).length)
    }

    @Test
    fun fromHTML_returnsEmptyForBlankInput() {
        assertEquals(expected = "", actual = ReadableText.fromHTML(""))
    }

    @Test
    fun fromHTML_keepsVisibleTextFromNestedMarkup() {
        val text = ReadableText.fromHTML("<body><main><h1>Title</h1><p>Body <b>here</b></p></main></body>")

        assertContains(text, "Title")
        assertContains(text, "Body here")
    }
}
