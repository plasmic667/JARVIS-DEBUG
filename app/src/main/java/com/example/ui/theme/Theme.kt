package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PanelDark = Color(0xFF150D24)

private val DarkColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = TextPrimary,
    primaryContainer = PanelDark,
    onPrimaryContainer = TextPrimary,
    secondary = NeonPink,
    onSecondary = TextPrimary,
    tertiary = NeonCyan,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgSoft,
    onSurface = TextPrimary,
    surfaceVariant = PanelBg,
    onSurfaceVariant = TextMuted,
    outline = PanelBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
