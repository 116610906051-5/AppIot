package com.example.antilost.ui.theme

import android.app.Activity
import android.os.Build
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

// Always use dark (deep space) theme for a premium look
private val DeepSpaceColorScheme = darkColorScheme(
    primary = Primary,                        // Electric Blue #4F8EF7
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A2D50),
    onPrimaryContainer = PrimaryLight,

    secondary = Secondary,                    // Cyan Teal #00D4AA
    onSecondary = Color(0xFF003326),
    secondaryContainer = Color(0xFF00402F),
    onSecondaryContainer = Color(0xFF66FFD6),

    tertiary = AccentPurple,                  // Purple #8B65FF
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF2D1F5E),
    onTertiaryContainer = Color(0xFFDDD0FF),

    error = Error,                            // #FF4F6B
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF400015),
    onErrorContainer = ErrorLight,

    background = BackgroundDark,              // #070B14
    onBackground = Color(0xFFE8ECF5),

    surface = SurfaceDark,                    // #0F1623
    onSurface = Color(0xFFE8ECF5),
    surfaceVariant = SurfaceVariantDark,      // #1A2233
    onSurfaceVariant = Color(0xFFA8B4CC),

    outline = Color(0xFF2D3F5A),
    outlineVariant = Color(0xFF1A2A3F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE8ECF5),
    inverseOnSurface = Color(0xFF0F1623),
    inversePrimary = PrimaryVariant,
    surfaceTint = Primary
)

@Composable
fun AntiLostAppTheme(
    darkTheme: Boolean = true, // Always dark for premium look
    dynamicColor: Boolean = false, // Disabled to keep our custom palette
    content: @Composable () -> Unit
) {
    val colorScheme = DeepSpaceColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}