package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.test.Test

/**
 * The wordmark «imagina /mail» takes the place of the icon and the name written as text wherever the product's name
 * fits (IMAGINA.md, «Imágenes de marca»): the drawables are the brand's SVGs, in the light theme and, reversed, in
 * the dark one, and the screens show them through hooks that a Thunderbird update could drop.
 */
class ImaginaBrandTest {
    @Test
    fun `the wordmark drawables are made from the brand's SVGs, light and reversed for the dark theme`() {
        val light = fillColours(ImaginaProjectFiles.root("imagina/brand/imagina-mail-wordmark.svg"), "fill")
        val reverse = fillColours(ImaginaProjectFiles.root("imagina/brand/imagina-mail-wordmark-reverse.svg"), "fill")

        val lightDrawable = ImaginaProjectFiles.module("src/main/res/drawable/ic_imagina_wordmark.xml")
        val nightDrawable = ImaginaProjectFiles.module("src/main/res/drawable-night/ic_imagina_wordmark.xml")

        assertThat(light).isEqualTo(listOf("#1A1B25", "#0D9488", "#0F766E"))
        assertThat(reverse).isEqualTo(listOf("#F8F7F4", "#2DD4BF", "#5EEAD4"))
        assertThat(fillColours(lightDrawable, "android:fillColor")).isEqualTo(light)
        assertThat(fillColours(nightDrawable, "android:fillColor")).isEqualTo(reverse)
    }

    @Test
    fun `the wordmark drawables keep the proportions of the SVG`() {
        listOf("drawable", "drawable-night").forEach { folder ->
            val vector = ImaginaProjectFiles.module("src/main/res/$folder/ic_imagina_wordmark.xml").readText()
            val width = attribute(vector, "android:width").removeSuffix("dp").toDouble()
            val height = attribute(vector, "android:height").removeSuffix("dp").toDouble()
            val viewportWidth = attribute(vector, "android:viewportWidth").toDouble()
            val viewportHeight = attribute(vector, "android:viewportHeight").toDouble()

            assertThat(viewportWidth / viewportHeight).isBetween(WORDMARK_RATIO - 0.001, WORDMARK_RATIO + 0.001)
            assertThat(width / height).isBetween(WORDMARK_RATIO - 0.01, WORDMARK_RATIO + 0.01)
        }
    }

    @Test
    fun `the sign-in screen shows the wordmark, not the mark with the name as text`() {
        val screen = ImaginaProjectFiles
            .module("src/main/kotlin/net/thunderbird/android/imagina/ImaginaSignInScreen.kt")
            .readText()

        assertThat(screen).contains("R.drawable.ic_imagina_wordmark")
        assertThat(screen).doesNotContain("ic_imagina_mark")
    }

    @Test
    fun `Bolt's theme carries the wordmark and Thunderbird's headers show it when there is one`() {
        val theme = ImaginaProjectFiles.module("src/main/kotlin/net/thunderbird/android/imagina/ImaginaBoltTheme.kt")
            .readText()
        val images = ImaginaProjectFiles
            .root("components/ui/bolt/src/commonMain/kotlin/net/thunderbird/components/ui/bolt/theme/ThemeImages.kt")
            .readText()
        val header = ImaginaProjectFiles
            .root("feature/account/common/src/main/kotlin/app/k9mail/feature/account/common/ui/AppTitleTopHeader.kt")
            .readText()
        val about = ImaginaProjectFiles
            .root("legacy/ui/legacy/src/main/java/com/fsck/k9/ui/settings/AboutFragment.kt")
            .readText()

        assertThat(theme).contains("R.drawable.ic_imagina_wordmark")
        assertThat(theme).contains("wordmark = wordmark")
        assertThat(images).contains("val wordmark: Painter? = null")
        assertThat(header).contains("BoltTheme.images.wordmark")
        assertThat(about).contains("BoltTheme.images.wordmark")
    }

    /** The colours of an SVG (`fill="…"`) or of a vector drawable (`android:fillColor="…"`), in order. */
    private fun fillColours(file: File, attribute: String): List<String> =
        Regex("""$attribute="(#[0-9A-Fa-f]{6})"""").findAll(file.readText()).map { it.groupValues[1].uppercase() }
            .toList()

    private fun attribute(xml: String, name: String): String =
        Regex("""$name="([^"]+)"""").find(xml)?.groupValues?.get(1) ?: error("$name is not in the vector drawable")

    private companion object {
        /** 577.5 x 98, the viewBox of both SVGs. */
        const val WORDMARK_RATIO = 577.5 / 98.0
    }
}
