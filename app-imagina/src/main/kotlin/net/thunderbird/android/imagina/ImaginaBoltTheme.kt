package net.thunderbird.android.imagina

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import net.thunderbird.android.R
import net.thunderbird.components.ui.bolt.resources.Res
import net.thunderbird.components.ui.bolt.resources.bolt_k9mail_logo
import net.thunderbird.components.ui.bolt.resources.bolt_thunderbird_logo
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.components.ui.bolt.theme.ThemeColorScheme
import net.thunderbird.components.ui.bolt.theme.ThemeColorSchemeVariants
import net.thunderbird.components.ui.bolt.theme.ThemeConfig
import net.thunderbird.components.ui.bolt.theme.ThemeImageVariants
import net.thunderbird.components.ui.bolt.theme.ThemeImages
import net.thunderbird.components.ui.bolt.theme.default.defaultThemeElevations
import net.thunderbird.components.ui.bolt.theme.default.defaultThemeShapes
import net.thunderbird.components.ui.bolt.theme.default.defaultThemeSizes
import net.thunderbird.components.ui.bolt.theme.default.defaultThemeSpacings
import net.thunderbird.components.ui.bolt.theme.default.defaultTypography

/**
 * Bolt, Thunderbird's theme for Compose screens, with Imagina's colours (IMAGINA.md, «Colores») instead of
 * Thunderbird's blue: Thunderbird has no way to give [BoltTheme] other colours than its own
 * `ThunderbirdBoltTheme`, but [BoltTheme] and every piece of its [ThemeConfig] are public, so this is
 * `ThunderbirdBoltTheme` with Imagina's colours.
 *
 * The colours are the resources `light*` and `dark*` of the app (`values/imagina_theme_colors.xml`), the
 * ones that also colour the screens of XML: one palette for both. The logos are the two that Bolt has, which the
 * app replaces with Imagina's mark and its inverse for the dark theme (`imagina/generate-resources.sh`).
 */
@Composable
fun ImaginaBoltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Not Thunderbird's and K-9 Mail's logos any more: the assets of the app with those names are Imagina's
    val lightImages = ThemeImages(logo = Res.drawable.bolt_thunderbird_logo)
    val darkImages = ThemeImages(logo = Res.drawable.bolt_k9mail_logo)

    val themeConfig = ThemeConfig(
        colors = ThemeColorSchemeVariants(
            dark = imaginaDarkColorScheme(),
            light = imaginaLightColorScheme(),
        ),
        elevations = defaultThemeElevations,
        images = ThemeImageVariants(
            light = lightImages,
            dark = darkImages,
        ),
        sizes = defaultThemeSizes,
        spacings = defaultThemeSpacings,
        shapes = defaultThemeShapes,
        typography = defaultTypography,
    )

    BoltTheme(
        themeConfig = themeConfig,
        darkTheme = darkTheme,
        content = content,
    )
}

@Composable
@Suppress("LongMethod")
private fun imaginaLightColorScheme() = ThemeColorScheme(
    primary = colorResource(R.color.lightPrimary),
    onPrimary = colorResource(R.color.lightOnPrimary),
    primaryContainer = colorResource(R.color.lightPrimaryContainer),
    onPrimaryContainer = colorResource(R.color.lightOnPrimaryContainer),

    secondary = colorResource(R.color.lightSecondary),
    onSecondary = colorResource(R.color.lightOnSecondary),
    secondaryContainer = colorResource(R.color.lightSecondaryContainer),
    onSecondaryContainer = colorResource(R.color.lightOnSecondaryContainer),

    tertiary = colorResource(R.color.lightTertiary),
    onTertiary = colorResource(R.color.lightOnTertiary),
    tertiaryContainer = colorResource(R.color.lightTertiaryContainer),
    onTertiaryContainer = colorResource(R.color.lightOnTertiaryContainer),

    error = colorResource(R.color.lightError),
    onError = colorResource(R.color.lightOnError),
    errorContainer = colorResource(R.color.lightErrorContainer),
    onErrorContainer = colorResource(R.color.lightOnErrorContainer),

    surfaceDim = colorResource(R.color.lightSurfaceDim),
    surface = colorResource(R.color.lightSurface),
    surfaceBright = colorResource(R.color.lightSurfaceBright),
    onSurface = colorResource(R.color.lightOnSurface),
    onSurfaceVariant = colorResource(R.color.lightOnSurfaceVariant),

    surfaceContainerLowest = colorResource(R.color.lightSurfaceContainerLowest),
    surfaceContainerLow = colorResource(R.color.lightSurfaceContainerLow),
    surfaceContainer = colorResource(R.color.lightSurfaceContainer),
    surfaceContainerHigh = colorResource(R.color.lightSurfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.lightSurfaceContainerHighest),

    inverseSurface = colorResource(R.color.lightSurfaceInverse),
    inverseOnSurface = colorResource(R.color.lightOnSurfaceInverse),
    inversePrimary = colorResource(R.color.lightPrimaryInverse),

    outline = colorResource(R.color.lightOutline),
    outlineVariant = colorResource(R.color.lightOutlineVariant),

    scrim = Color.Black,

    info = colorResource(R.color.lightInfo),
    onInfo = colorResource(R.color.lightOnInfo),
    infoContainer = colorResource(R.color.lightInfoContainer),
    onInfoContainer = colorResource(R.color.lightOnInfoContainer),

    success = colorResource(R.color.lightSuccess),
    onSuccess = colorResource(R.color.lightOnSuccess),
    successContainer = colorResource(R.color.lightSuccessContainer),
    onSuccessContainer = colorResource(R.color.lightOnSuccessContainer),

    warning = colorResource(R.color.lightWarning),
    onWarning = colorResource(R.color.lightOnWarning),
    warningContainer = colorResource(R.color.lightWarningContainer),
    onWarningContainer = colorResource(R.color.lightOnWarningContainer),
)

@Composable
@Suppress("LongMethod")
private fun imaginaDarkColorScheme() = ThemeColorScheme(
    primary = colorResource(R.color.darkPrimary),
    onPrimary = colorResource(R.color.darkOnPrimary),
    primaryContainer = colorResource(R.color.darkPrimaryContainer),
    onPrimaryContainer = colorResource(R.color.darkOnPrimaryContainer),

    secondary = colorResource(R.color.darkSecondary),
    onSecondary = colorResource(R.color.darkOnSecondary),
    secondaryContainer = colorResource(R.color.darkSecondaryContainer),
    onSecondaryContainer = colorResource(R.color.darkOnSecondaryContainer),

    tertiary = colorResource(R.color.darkTertiary),
    onTertiary = colorResource(R.color.darkOnTertiary),
    tertiaryContainer = colorResource(R.color.darkTertiaryContainer),
    onTertiaryContainer = colorResource(R.color.darkOnTertiaryContainer),

    error = colorResource(R.color.darkError),
    onError = colorResource(R.color.darkOnError),
    errorContainer = colorResource(R.color.darkErrorContainer),
    onErrorContainer = colorResource(R.color.darkOnErrorContainer),

    surfaceDim = colorResource(R.color.darkSurfaceDim),
    surface = colorResource(R.color.darkSurface),
    surfaceBright = colorResource(R.color.darkSurfaceBright),
    onSurface = colorResource(R.color.darkOnSurface),
    onSurfaceVariant = colorResource(R.color.darkOnSurfaceVariant),

    surfaceContainerLowest = colorResource(R.color.darkSurfaceContainerLowest),
    surfaceContainerLow = colorResource(R.color.darkSurfaceContainerLow),
    surfaceContainer = colorResource(R.color.darkSurfaceContainer),
    surfaceContainerHigh = colorResource(R.color.darkSurfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.darkSurfaceContainerHighest),

    inverseSurface = colorResource(R.color.darkSurfaceInverse),
    inverseOnSurface = colorResource(R.color.darkOnSurfaceInverse),
    inversePrimary = colorResource(R.color.darkPrimaryInverse),

    outline = colorResource(R.color.darkOutline),
    outlineVariant = colorResource(R.color.darkOutlineVariant),

    scrim = Color.Black,

    info = colorResource(R.color.darkInfo),
    onInfo = colorResource(R.color.darkOnInfo),
    infoContainer = colorResource(R.color.darkInfoContainer),
    onInfoContainer = colorResource(R.color.darkOnInfoContainer),

    success = colorResource(R.color.darkSuccess),
    onSuccess = colorResource(R.color.darkOnSuccess),
    successContainer = colorResource(R.color.darkSuccessContainer),
    onSuccessContainer = colorResource(R.color.darkOnSuccessContainer),

    warning = colorResource(R.color.darkWarning),
    onWarning = colorResource(R.color.darkOnWarning),
    warningContainer = colorResource(R.color.darkWarningContainer),
    onWarningContainer = colorResource(R.color.darkOnWarningContainer),
)
