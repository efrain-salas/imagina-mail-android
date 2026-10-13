package net.thunderbird.android.imagina

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.test.Test
import net.thunderbird.components.ui.bolt.theme.ThemeTypography
import net.thunderbird.components.ui.bolt.theme.default.defaultTypography

/**
 * Imagina's typography (IMAGINA.md, «Tipografía»): Instrument Sans for the interface and Fraunces for big headlines,
 * on the screens of Compose and on the ones of XML. The XML ones take the typeface from four strings of Material that
 * the app replaces, and from the text appearances of Android that the themes replace; a Thunderbird update that
 * changes a theme could drop them.
 */
class ImaginaTypographyTest {
    private val res = ImaginaProjectFiles.module("src/main/res")

    @Test
    fun `the typefaces of the design system are bundled as TrueType fonts`() {
        val fonts = listOf(
            "instrument_sans_regular",
            "instrument_sans_medium",
            "instrument_sans_semibold",
            "instrument_sans_bold",
            "fraunces_semibold",
        )

        val notFonts = fonts.filter { name ->
            val bytes = File(res, "font/$name.ttf").also { check(it.exists()) { "$name.ttf is not in res/font" } }
                .readBytes()
            bytes.size < MINIMUM_FONT_BYTES || bytes.take(4) != TRUE_TYPE_SIGNATURE
        }

        assertThat(notFonts).isEmpty()
    }

    @Test
    fun `the fonts travel with their licence`() {
        val licence = ImaginaProjectFiles.root("imagina/brand/fonts/OFL.txt").readText()

        assertThat(licence).contains("SIL OPEN FONT LICENSE Version 1.1")
        assertThat(licence).contains("The Instrument Sans Project Authors")
        assertThat(licence).contains("The Fraunces Project Authors")
    }

    @Test
    fun `the four typefaces of Material 3 are Imagina's families, which exist and point to the bundled fonts`() {
        val strings = ImaginaProjectFiles.strings(File(res, "values/imagina_typography.xml"))

        assertThat(strings.keys).containsAll(*MATERIAL_TYPEFACES)
        val problems = MATERIAL_TYPEFACES.flatMap { name ->
            val family = strings.getValue(name).removePrefix("@font/")
            val file = File(res, "font/$family.xml")
            when {
                !strings.getValue(name).startsWith("@font/") -> listOf("$name is not a font: ${strings[name]}")

                !file.exists() -> listOf("$name: font/$family.xml does not exist")

                else -> Regex("""android:font="@font/(\w+)"""").findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { !File(res, "font/$it.ttf").exists() }
                    .map { "$family.xml: $it.ttf does not exist" }
                    .toList()
            }
        }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `the font families have the real weights, for Android before 8 too`() {
        val weights = mapOf(
            "imagina_sans" to listOf("400", "500", "600", "700"),
            "imagina_sans_medium" to listOf("500", "700"),
            "imagina_sans_semibold" to listOf("600", "700"),
        )

        weights.forEach { (family, expected) ->
            val xml = File(res, "font/$family.xml").readText()

            assertThat(Regex("""android:fontWeight="(\d+)"""").findAll(xml).map { it.groupValues[1] }.toList())
                .isEqualTo(expected)
            assertThat(Regex("""app:fontWeight="(\d+)"""").findAll(xml).map { it.groupValues[1] }.toList())
                .isEqualTo(expected)
        }
    }

    @Test
    fun `both themes use Instrument Sans for what the text appearances of Material do not reach`() {
        val themes = File(res, "values/themes.xml")
        val appearances = listOf(
            "android:textAppearance",
            "android:textAppearanceSmall",
            "android:textAppearanceMedium",
            "android:textAppearanceLarge",
            "android:textAppearanceListItem",
            "android:textAppearanceListItemSmall",
            "android:textAppearanceListItemSecondary",
        )

        val missing = listOf("Theme.Thunderbird.Light", "Theme.Thunderbird.Dark").flatMap { theme ->
            val items = ImaginaProjectFiles.items(themes, "style", theme).toMap()
            appearances.filter { items[it]?.startsWith("@style/TextAppearance.Imagina") != true }
                .map { "$theme: $it" }
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `Compose uses Fraunces for the display and headline styles and Instrument Sans for the rest`() {
        val typography = imaginaTypography()
        val headlines = with(typography) {
            listOf(displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall)
        }
        val texts = with(typography) {
            listOf(
                titleLarge, titleMedium, titleSmall,
                bodyLarge, bodyMedium, bodySmall,
                labelLarge, labelMedium, labelSmall,
            )
        }

        assertThat(headlines.map { it.fontFamily }.distinct()).isEqualTo(listOf<FontFamily?>(Fraunces))
        assertThat(texts.map { it.fontFamily }.distinct()).isEqualTo(listOf<FontFamily?>(InstrumentSans))
        assertThat(headlines.map { it.fontWeight }.distinct()).isEqualTo(listOf<FontWeight?>(FontWeight.SemiBold))
    }

    @Test
    fun `Compose keeps the sizes and line heights of Bolt, and no style is bolder than semibold`() {
        val ours = imaginaTypography().styles()

        assertThat(ours.map { it.fontSize to it.lineHeight })
            .isEqualTo(defaultTypography.styles().map { it.fontSize to it.lineHeight })
        assertThat(ours.maxOf { it.fontWeight?.weight ?: 0 }).isEqualTo(FontWeight.SemiBold.weight)
    }

    private fun ThemeTypography.styles(): List<TextStyle> = listOf(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
        labelLarge, labelMedium, labelSmall,
    )

    private companion object {
        val MATERIAL_TYPEFACES = arrayOf(
            "m3_ref_typeface_plain_regular",
            "m3_ref_typeface_plain_medium",
            "m3_ref_typeface_brand_regular",
            "m3_ref_typeface_brand_medium",
        )
        const val MINIMUM_FONT_BYTES = 10_000
        val TRUE_TYPE_SIGNATURE = listOf<Byte>(0, 1, 0, 0)
    }
}
