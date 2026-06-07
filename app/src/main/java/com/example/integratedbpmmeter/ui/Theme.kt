package com.example.integratedbpmmeter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF007A6E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEDE6),
    onPrimaryContainer = Color(0xFF06201C),
    secondary = Color(0xFF54635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E9E6),
    onSecondaryContainer = Color(0xFF111E1B),
    tertiary = Color(0xFF7B5D18),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA7),
    onTertiaryContainer = Color(0xFF251A00),
    background = Color(0xFFF6F8F5),
    onBackground = Color(0xFF171D1B),
    surface = Color.White,
    onSurface = Color(0xFF171D1B),
    surfaceDim = Color(0xFFD8DED9),
    surfaceBright = Color(0xFFF6F8F5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F1),
    surfaceContainer = Color(0xFFECF0EC),
    surfaceContainerHigh = Color(0xFFE7ECE8),
    surfaceContainerHighest = Color(0xFFE1E8E3),
    surfaceVariant = Color(0xFFE2E7E2),
    onSurfaceVariant = Color(0xFF48524E),
    outline = Color(0xFF72807A),
    outlineVariant = Color(0xFFC8D1CC)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 64.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun IntegratedBpmMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
