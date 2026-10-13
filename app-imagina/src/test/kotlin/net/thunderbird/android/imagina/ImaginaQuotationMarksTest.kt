package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.isEmpty
import kotlin.test.Test

/**
 * Every text of Imagina Mail uses the quotation marks of its language: «…» in Spanish, Italian and Portuguese, “…” in
 * English, „…“ in German and « … » in French, with a narrow no-break space (U+202F) inside the guillemets.
 */
class ImaginaQuotationMarksTest {
    @Test
    fun `every string of Imagina uses the quotation marks of its language`() {
        val problems = MARKS.flatMap { (folder, marks) ->
            val strings = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/$folder/strings.xml"))
            strings.mapNotNull { (name, text) -> problemOf(text, marks)?.let { "$folder $name: $it ($text)" } }
        }

        assertThat(problems).isEmpty()
    }

    private fun problemOf(text: String, marks: Marks): String? {
        val found = text.filter { it in ALL_MARKS }
        val allowed = setOf(marks.open, marks.close)
        return when {
            found.any { it !in allowed } -> "other quotation marks than ${marks.open}${marks.close}"
            text.count { it == marks.open } != text.count { it == marks.close } -> "an unpaired quotation mark"
            marks.inner != null && !hasInnerSpaces(text, marks) -> "no narrow no-break space inside the guillemets"
            marks.inner == null && hasSpaceInside(text, marks) -> "a space inside the quotation marks"
            else -> null
        }
    }

    private fun hasSpaceInside(text: String, marks: Marks): Boolean = text.indices.any { index ->
        (text[index] == marks.open && text.getOrNull(index + 1)?.isWhitespace() == true) ||
            (text[index] == marks.close && text.getOrNull(index - 1)?.isWhitespace() == true)
    }

    private fun hasInnerSpaces(text: String, marks: Marks): Boolean = text.indices.all { index ->
        when (text[index]) {
            marks.open -> text.getOrNull(index + 1) == marks.inner
            marks.close -> text.getOrNull(index - 1) == marks.inner
            else -> true
        }
    }

    private class Marks(val open: Char, val close: Char, val inner: Char? = null)

    private companion object {
        /** Every double quotation mark a text could use, the straight one included. */
        const val ALL_MARKS = "\"«»‹›“”„‟"
        const val NARROW_NO_BREAK_SPACE = ' '

        val GUILLEMETS = Marks('«', '»')
        val MARKS = mapOf(
            "values" to Marks('“', '”'),
            "values-en-rGB" to Marks('“', '”'),
            "values-es" to GUILLEMETS,
            "values-de" to Marks('„', '“'),
            "values-fr" to Marks('«', '»', NARROW_NO_BREAK_SPACE),
            "values-it" to GUILLEMETS,
            "values-pt-rPT" to GUILLEMETS,
        )
    }
}
