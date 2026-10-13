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

    @Test
    fun `the background is the cream canvas and the cards are white, never teal`() {
        assertThat(color("lightSurface")).isEqualTo(0xF8F7F4)
        assertThat(color("darkSurface")).isEqualTo(0x111219)
        // Material paints the cards, the bottom sheets and the drawer with surfaceContainerLow: the surface (white)
        assertThat(color("lightSurfaceContainerLow")).isEqualTo(0xFFFFFF)
        assertThat(color("darkSurfaceContainerLow")).isEqualTo(0x1A1B25)
        // What the XML screens paint behind everything and under the toolbar is a surface role, not an accent one
        val theme = ImaginaProjectFiles.root("core/ui/legacy/theme2/thunderbird/src/main/res/values/themes.xml")
        listOf("Theme2.Thunderbird.Light" to "light", "Theme2.Thunderbird.Dark" to "dark").forEach { (style, prefix) ->
            val items = ImaginaProjectFiles.items(theme, "style", style).toMap()

            assertThat(items["android:colorBackground"]).isEqualTo("@color/${prefix}Surface")
            assertThat(items["colorSurface"]).isEqualTo("@color/${prefix}Surface")
        }
    }

    @Test
    fun `the avatars of senders with no picture are Imagina's tones, with the white letter readable on each`() {
        val avatars = ImaginaProjectFiles.colors(ImaginaProjectFiles.module("src/main/res/values/imagina_colors.xml"))
            .filterKeys { it.startsWith("imagina_contact_picture_") }
        val themes = ImaginaProjectFiles.module("src/main/res/values/themes.xml")

        val tooPale = avatars.mapNotNull { (name, value) ->
            val ratio = contrast(0xFFFFFF, value.drop(1).toInt(16))
            "$name $value: ${"%.2f".format(ratio)}".takeIf { ratio < MINIMUM_TEXT_CONTRAST }
        }
        val notImagina = listOf("Light" to "light", "Dark" to "dark").flatMap { (style, theme) ->
            val items = ImaginaProjectFiles.items(themes, "style", "Theme.Thunderbird.$style").toMap()
            listOf(
                "contactPictureFallbackBackgroundColors" to "@array/imagina_contact_picture_colors_$theme",
                "contactPictureFallbackDefaultBackgroundColor" to "@color/imagina_contact_picture_default",
            ).filter { (item, expected) -> items[item] != expected }.map { "$style: ${it.first}" }
        }

        assertThat(avatars.size).isEqualTo(AVATAR_COLOURS * 2 + 1)
        assertThat(tooPale).isEmpty()
        assertThat(notImagina).isEmpty()
    }

    @Test
    fun `the widget of the message list has Imagina's colours in both themes and its text is readable`() {
        val widget = ImaginaProjectFiles.colors(
            ImaginaProjectFiles.root("feature/widget/message-list/src/main/res/values/colors.xml"),
        )
        val light = ImaginaProjectFiles.colors(ImaginaProjectFiles.module("src/main/res/values/imagina_colors.xml"))
        val dark = ImaginaProjectFiles.colors(
            ImaginaProjectFiles.module("src/main/res/values-night/imagina_colors.xml"),
        )

        assertThat(widget.keys.filter { it !in light || it !in dark }).isEmpty()
        listOf("light" to light, "dark" to dark).forEach { (theme, colours) ->
            fun hex(name: String) = colours.getValue(name).drop(1).toInt(16)
            // The list is drawn on the window background, the header on its own colour
            val pairs = listOf(
                "message_list_widget_header_text" to "message_list_widget_header_background",
                "message_list_widget_text_read" to null,
                "message_list_widget_text_unread" to null,
            )
            val failures = pairs.mapNotNull { (text, background) ->
                val backgroundColour = background?.let(::hex) ?: color("${theme}Surface")
                val ratio = contrast(hex(text), backgroundColour)
                "$theme $text: ${"%.2f".format(ratio)}".takeIf { ratio < MINIMUM_TEXT_CONTRAST }
            }

            assertThat(failures).isEmpty()
        }
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
        const val AVATAR_COLOURS = 10

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
