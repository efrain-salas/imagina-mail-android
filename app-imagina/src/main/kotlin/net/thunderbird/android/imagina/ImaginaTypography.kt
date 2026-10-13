package net.thunderbird.android.imagina

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.theme.ThemeTypography
import net.thunderbird.components.ui.bolt.theme.default.defaultTypography

/**
 * Instrument Sans, the typeface of Imagina's interface: body, controls, titles and labels (400, 500, 600 and, rarely,
 * 700). The TTFs are in `res/font`; the XML screens use them through `res/font/imagina_sans*.xml`
 * (IMAGINA.md, «Tipografía»).
 */
internal val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

/** Fraunces (soft, semibold), only for big headlines. Never in body, buttons or labels. */
internal val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

/** Headlines tighten a little, as in the design system («titulares con tracking negativo»). */
private const val HEADLINE_LETTER_SPACING_EM = -0.01f

/**
 * Bolt's type scale (sizes, line heights and tracking stay Thunderbird's) with Imagina's typefaces: Fraunces in the
 * display and headline styles, Instrument Sans in everything else, with the weights of the design system: 400 for
 * the body, 500 for the buttons, 600 for the titles and the small labels (Thunderbird's bold becomes semibold: 700 is
 * rare).
 */
internal fun imaginaTypography(base: ThemeTypography = defaultTypography): ThemeTypography = ThemeTypography(
    displayLarge = base.displayLarge.headline(),
    displayMedium = base.displayMedium.headline(),
    displaySmall = base.displaySmall.headline(),
    headlineLarge = base.headlineLarge.headline(),
    headlineMedium = base.headlineMedium.headline(),
    headlineSmall = base.headlineSmall.headline(),
    titleLarge = base.titleLarge.text(FontWeight.SemiBold),
    titleMedium = base.titleMedium.text(FontWeight.SemiBold),
    titleSmall = base.titleSmall.text(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.text(FontWeight.Normal),
    bodyMedium = base.bodyMedium.text(FontWeight.Normal),
    bodySmall = base.bodySmall.text(FontWeight.Normal),
    labelLarge = base.labelLarge.text(FontWeight.Medium),
    labelMedium = base.labelMedium.text(FontWeight.SemiBold),
    labelSmall = base.labelSmall.text(FontWeight.SemiBold),
)

private fun TextStyle.headline(): TextStyle = copy(
    fontFamily = Fraunces,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = HEADLINE_LETTER_SPACING_EM.em,
)

private fun TextStyle.text(weight: FontWeight): TextStyle = copy(fontFamily = InstrumentSans, fontWeight = weight)
