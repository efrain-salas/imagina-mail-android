package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.isEmpty
import kotlin.test.Test

/**
 * The About screen says that Imagina Mail is a modified version of Thunderbird for Android and not the official app
 * (IMAGINA.md, «Textos y enlaces»), in every language Imagina writes its own texts in.
 */
class ImaginaAboutTest {
    @Test
    fun `the About notice is in every language of Imagina and names Thunderbird, Apache and Mozilla`() {
        val problems = LANGUAGE_FOLDERS.mapNotNull { folder ->
            val strings = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/$folder/strings.xml"))
            val notice = strings["about_notice"]
            when {
                notice.isNullOrBlank() -> "$folder has no about_notice"
                REQUIRED_WORDS.any { it !in notice } -> "$folder: $notice"
                else -> null
            }
        }

        assertThat(problems).isEmpty()
    }

    private companion object {
        val LANGUAGE_FOLDERS = listOf("values", "values-es", "values-de", "values-fr", "values-it", "values-pt-rPT")
        val REQUIRED_WORDS = listOf("Thunderbird", "Apache", "Mozilla")
    }
}
