package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.math.pow
import kotlin.test.Test

/**
 * Imagina's colours (values/imagina_theme_colors.xml, IMAGINA.md «Colores»): every colour of Thunderbird's theme is
 * replaced (a new one that comes with a Thunderbird update would show Thunderbird's blue until it is), and the text
 * keeps its contrast in both themes.
 */
class ImaginaColorsTest {
    private val imagina = ImaginaProjectFiles.colors(
        ImaginaProjectFiles.module("src/main/res/values/imagina_theme_colors.xml"),
    )
    private val thunderbird = ImaginaProjectFiles.colors(
        ImaginaProjectFiles.root("core/ui/legacy/theme2/thunderbird/src/main/res/values/colors.xml"),
    )

    @Test
    fun `every colour of Thunderbird's theme is replaced with one of Imagina`() {
        val notReplaced = thunderbird.keys.filter { it !in imagina }

        assertThat(notReplaced).isEmpty()
    }

    @Test
    fun `the colours of the Compose theme exist in both themes`() {
        val roles = imagina.keys.filter { it.startsWith("light") }.map { it.removePrefix("light") }
        val missingInDark = roles.filter { "dark$it" !in imagina }
        val missingInLight = imagina.keys.filter { it.startsWith("dark") }.map { it.removePrefix("dark") }
            .filter { "light$it" !in imagina }

        assertThat(missingInDark + missingInLight).isEmpty()
    }

    @Test
    fun `the text keeps 4,5 to 1 against what it is drawn on, in both themes`() {
        val failures = THEMES.flatMap { theme ->
            textPairs.mapNotNull { (text, background) ->
                val ratio = contrast(color("$theme$text"), color("$theme$background"))
                "$theme$text on $theme$background: ${"%.2f".format(ratio)}".takeIf { ratio < MINIMUM_TEXT_CONTRAST }
            }
        }

        assertThat(failures).isEmpty()
    }

    @Test
    fun `the outline of the controls keeps 3 to 1 against the main surfaces, in both themes`() {
        val failures = THEMES.flatMap { theme ->
            listOf("Surface", "SurfaceContainerLowest", "SurfaceContainerLow").mapNotNull { surface ->
                val ratio = contrast(color("${theme}Outline"), color("$theme$surface"))
                "${theme}Outline on $theme$surface: ${"%.2f".format(ratio)}".takeIf { ratio < MINIMUM_GRAPHIC_CONTRAST }
            }
        }

        assertThat(failures).isEmpty()
    }

    @Test
    fun `the accent of the design system is the primary colour`() {
        assertThat(color("lightPrimary")).isEqualTo(0x0F766E)
        assertThat(color("darkPrimary")).isEqualTo(0x2DD4BF)
    }

    private fun color(name: String): Int {
        val value = imagina[name] ?: error("$name is not in imagina_theme_colors.xml")
        check(Regex("#[0-9A-Fa-f]{6}").matches(value)) { "$name is $value, an opaque #RRGGBB colour was expected" }
        return value.drop(1).toInt(16)
    }

    private fun contrast(first: Int, second: Int): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        val THEMES = listOf("light", "dark")
        const val MINIMUM_TEXT_CONTRAST = 4.5
        const val MINIMUM_GRAPHIC_CONTRAST = 3.0

        val surfaces = listOf(
            "Surface",
            "SurfaceDim",
            "SurfaceBright",
            "SurfaceContainerLowest",
            "SurfaceContainerLow",
            "SurfaceContainer",
            "SurfaceContainerHigh",
            "SurfaceContainerHighest",
        )

        /** Text colour and what it is drawn on. Links and highlights use the primary colour as text. */
        val textPairs: List<Pair<String, String>> =
            listOf("Primary", "Secondary", "Tertiary", "Error", "Info", "Success", "Warning")
                .flatMap { listOf("On$it" to it, "On${it}Container" to "${it}Container") } +
                surfaces.flatMap { listOf("OnSurface" to it, "OnSurfaceVariant" to it, "Primary" to it) } +
                listOf(
                    "OnSurfaceInverse" to "SurfaceInverse",
                    "PrimaryInverse" to "SurfaceInverse",
                    "Error" to "Surface",
                    "Error" to "SurfaceContainer",
                    "OnSurface" to "PrimaryContainer",
                    "OnPrimaryFixed" to "PrimaryFixed",
                    "OnPrimaryFixed" to "PrimaryFixedDim",
                    "OnPrimaryFixedVariant" to "PrimaryFixed",
                    "OnSecondaryFixed" to "SecondaryFixed",
                    "OnSecondaryFixedVariant" to "SecondaryFixed",
                    "OnTertiaryFixed" to "TertiaryFixed",
                    "OnTertiaryFixedVariant" to "TertiaryFixed",
                )
    }
}
