package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * The links of the About and Settings screens are Imagina's, not Thunderbird's or Mozilla's (IMAGINA.md, «Textos y
 * enlaces»): every constant of Thunderbird that points to them is overridden in values/constants.xml, and none of
 * Imagina's does.
 */
class ImaginaLinksTest {
    private val thunderbird = ImaginaProjectFiles.strings(
        ImaginaProjectFiles.root("legacy/ui/legacy/src/main/res/values/constants.xml"),
    )
    private val imagina = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))

    @Test
    fun `every link of Thunderbird to its own sites is overridden`() {
        val notOverridden = thunderbird
            .filterValues { it.startsWith("http") && isThunderbirdsOrMozillas(it) }
            .keys
            .filter { it !in imagina }

        assertThat(notOverridden).isEmpty()
    }

    @Test
    fun `no link of Imagina points to Thunderbird or Mozilla`() {
        val links = imagina.filterValues { it.startsWith("http") && isThunderbirdsOrMozillas(it) }

        assertThat(links).isEmpty()
    }

    @Test
    fun `the help and the website are Imagina's and the source code is its repository`() {
        assertThat(imagina["app_webpage_url"]).isEqualTo("https://imagina.build")
        assertThat(imagina["user_forum_url"]).isEqualTo("https://imagina.build")
        assertThat(imagina["app_source_url"]).isEqualTo("https://github.com/efrain-salas/imagina-mail-android")
    }

    @Test
    fun `the User-Agent of the mail is Imagina Mail`() {
        assertThat(imagina["message_header_mua"]).isEqualTo("Imagina Mail")
    }

    private fun isThunderbirdsOrMozillas(url: String): Boolean =
        listOf("thunderbird", "mozilla").any { url.contains(it, ignoreCase = true) }
}
