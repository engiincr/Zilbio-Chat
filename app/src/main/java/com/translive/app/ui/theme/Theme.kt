package com.translive.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AccentAmberOrange,
    onPrimary = Color.White,
    primaryContainer = OutgoingBubblePeach,
    onPrimaryContainer = TextPrimaryEspresso,
    secondary = OnlineStatusGreen,
    onSecondary = Color.White,
    secondaryContainer = InputFieldBackground,
    onSecondaryContainer = TextPrimaryEspresso,
    background = BackgroundAlabasterCream,
    onBackground = TextPrimaryEspresso,
    surface = SurfaceCardWhite,
    onSurface = TextPrimaryEspresso,
    surfaceVariant = BottomNavBackground,
    onSurfaceVariant = TextSecondaryWarmGrey,
    outline = BorderWarmSubtle,
    error = CallEndCoralRed,
    onError = Color.White
)

@Composable
fun TransLiveTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BackgroundAlabasterCream.toArgb()
                window.navigationBarColor = BottomNavBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
