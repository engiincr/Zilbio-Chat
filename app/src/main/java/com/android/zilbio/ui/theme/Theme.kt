package com.android.zilbio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MidnightNavyDarkColorScheme = darkColorScheme(
    primary = AccentNeonLime,
    onPrimary = OnAccentNeonLime,
    primaryContainer = SurfaceSlateNavyElevated,
    onPrimaryContainer = TextOffWhite,
    secondary = OnlineStatusGreen,
    onSecondary = Color.Black,
    secondaryContainer = EmeraldReactionBg,
    onSecondaryContainer = EmeraldReactionText,
    background = BackgroundMidnightNavy,
    onBackground = TextOffWhite,
    surface = SurfaceSlateNavy,
    onSurface = TextOffWhite,
    surfaceVariant = BackgroundMidnightNavyAlt,
    onSurfaceVariant = TextMutedSlateGrey,
    outline = BorderSlateNavy,
    error = DestructiveCoralRed,
    onError = Color.White
)

private val ModernLightColorScheme = lightColorScheme(
    primary = AccentNeonLimeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = OnlineStatusGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF137333),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    error = DestructiveCoralRed,
    onError = Color.White
)

@Composable
fun ZilbioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MidnightNavyDarkColorScheme else ModernLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val statusBarColor = if (darkTheme) BackgroundMidnightNavy else Color(0xFFF8FAFC)
                val navBarColor = if (darkTheme) BackgroundMidnightNavyAlt else Color(0xFFEDF2F7)
                window.statusBarColor = statusBarColor.toArgb()
                window.navigationBarColor = navBarColor.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

