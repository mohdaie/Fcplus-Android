package com.fcplus.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GeminiInk = Color(0xFF080B14)
val GeminiSurface = Color(0xFF111626)
val GeminiSurface2 = Color(0xFF171D31)
val GeminiStroke = Color(0xFF29304A)
val GeminiText = Color(0xFFF5F7FF)
val GeminiMuted = Color(0xFFAAB2CE)
val GeminiBlue = Color(0xFF4C8DFF)
val GeminiCyan = Color(0xFF78E3FF)
val GeminiPurple = Color(0xFF8B6CFF)
val GeminiPink = Color(0xFFD76DFF)
val GeminiGreen = Color(0xFF61DDAA)
val GeminiRed = Color(0xFFFF6B7D)

private val FcColors = darkColorScheme(
    primary = GeminiBlue,
    secondary = GeminiPurple,
    tertiary = GeminiCyan,
    background = GeminiInk,
    surface = GeminiSurface,
    surfaceVariant = GeminiSurface2,
    outline = GeminiStroke,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = GeminiText,
    onSurface = GeminiText,
    onSurfaceVariant = GeminiMuted,
    error = GeminiRed
)

private val FcTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun FcPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FcColors,
        typography = FcTypography,
        content = content
    )
}
