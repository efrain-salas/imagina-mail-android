package net.thunderbird.components.ui.bolt.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource

/**
 * @param logo The app's logo.
 * @param wordmark Imagina (IMAGINA.md): the app's name drawn as its logotype, for the headers that have room for it.
 *  When it is not null they show it in place of the [logo] and the name as text. Null in Thunderbird and K-9 Mail.
 */
@Immutable
data class ThemeImages(
    val logo: DrawableResource,
    val wordmark: Painter? = null,
)

internal val LocalThemeImages = staticCompositionLocalOf<ThemeImages> {
    error("No ThemeImages provided")
}
