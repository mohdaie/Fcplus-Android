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

val GeminiInk = Color(0xFF0B0F14)
val GeminiSurface = Color(0xFF111820)
val GeminiSurface2 = Color(0xFF19222D)
val GeminiStroke = Color(0xFF2B3745)
val GeminiText = Color(0xFFEAF0F8)
val GeminiMuted = Color(0xFF9AA7B7)
val GeminiBlue = Color(0xFF4C8DF6)
val GeminiCyan = Color(0xFF7FCFFF)
val GeminiPurple = Color(0xFF9B72F2)
val GeminiPink = Color(0xFFD783E8)
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
