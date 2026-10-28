package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF06030D)
val BgSoft = Color(0xFF0B0617)
val PanelBg = Color(0xCC150D24)
val PanelSoftBg = Color(0x661A102C)
val PanelBorder = Color(0x2DB292FF)
val PanelBorderStrong = Color(0x52C4A5FF)

val TextPrimary = Color(0xFFF4F0FF)
val TextMuted = Color(0xFFA79BC6)
val TextFaint = Color(0xFF746A90)

val NeonPink = Color(0xFFFF2F8F)
val NeonMagenta = Color(0xFFD946EF)
val NeonViolet = Color(0xFF8B5CF6)
val NeonIndigo = Color(0xFF5B6CFF)
val NeonCyan = Color(0xFF5EEAD4)

val StatusGreen = Color(0xFF4ADE80)
val StatusAmber = Color(0xFFFBBF24)
val StatusRose = Color(0xFFFB7185)

val JarvisButtonGradient = Brush.horizontalGradient(
    colors = listOf(NeonPink, NeonMagenta, NeonIndigo)
)

val JarvisHeaderGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD9F4), Color(0xFFD8B4FE), Color(0xFFA5B4FC))
)
