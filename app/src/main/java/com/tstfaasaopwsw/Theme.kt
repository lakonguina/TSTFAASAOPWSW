package com.tstfaasaopwsw

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/** Theme: Material You dynamic colors (Android 12+), otherwise the default palette. */
@Composable
internal fun ForcelistTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // Default content colour app-wide: without this, text with no explicit colour falls back
    // to the CompositionLocal default (black) on any screen not wrapped in a Material Surface
    // — which is why labels showed up black. onSurface is readable in both themes.
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
}
