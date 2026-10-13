package net.thunderbird.android.provider

import androidx.compose.runtime.Composable
import net.thunderbird.android.imagina.ImaginaBoltTheme
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

internal class TbFeatureThemeProvider : FeatureThemeProvider {
    @Composable
    override fun WithTheme(content: @Composable () -> Unit) {
        ImaginaBoltTheme {
            content()
        }
    }

    @Composable
    override fun WithTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
        ImaginaBoltTheme(darkTheme = darkTheme) {
            content()
        }
    }
}
