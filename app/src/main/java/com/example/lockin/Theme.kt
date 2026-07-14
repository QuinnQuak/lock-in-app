package com.example.lockin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LavenderPrimary = Color(0xFF7B6EF6)
val LavenderPrimaryContainer = Color(0xFFE8E4FF)
val LavenderOnPrimaryContainer = Color(0xFF29235C)
val SageSecondary = Color(0xFF5E9C82)
val CalmBackground = Color(0xFFFBFAFF)
val CalmOnBackground = Color(0xFF2B2640)
val CalmSurfaceVariant = Color(0xFFF1EEFB)
val CalmOnSurfaceVariant = Color(0xFF453F5C)
val CoralError = Color(0xFFE8637A)
val CoralErrorContainer = Color(0xFFFFE1E6)
val CoralOnErrorContainer = Color(0xFF5C1626)

private val LockInLightColors = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderPrimaryContainer,
    onPrimaryContainer = LavenderOnPrimaryContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    background = CalmBackground,
    onBackground = CalmOnBackground,
    surface = Color.White,
    onSurface = CalmOnBackground,
    surfaceVariant = CalmSurfaceVariant,
    onSurfaceVariant = CalmOnSurfaceVariant,
    error = CoralError,
    onError = Color.White,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralOnErrorContainer,
)

private val LockInDarkColors = darkColorScheme(
    primary = Color(0xFFB6ACFF),
    onPrimary = Color(0xFF1E1B3A),
    primaryContainer = Color(0xFF3B3570),
    onPrimaryContainer = Color(0xFFE8E4FF),
    secondary = Color(0xFF9ADFC0),
    onSecondary = Color(0xFF113022),
    background = Color(0xFF17151F),
    onBackground = Color(0xFFEAE7F5),
    surface = Color(0xFF1D1A29),
    onSurface = Color(0xFFEAE7F5),
    surfaceVariant = Color(0xFF2A2638),
    onSurfaceVariant = Color(0xFFD9D5EA),
    error = Color(0xFFFFB3C1),
    onError = Color(0xFF5C1626),
    errorContainer = Color(0xFF7A2B3D),
    onErrorContainer = Color(0xFFFFE1E6),
)

@OptIn(ExperimentalTextApi::class)
val QuicksandFamily = FontFamily(
    Font(R.font.quicksand, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.quicksand, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.quicksand, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.quicksand, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val LockInTypography = Typography().let { base ->
    Typography(
        displaySmall = base.displaySmall.copy(
            fontFamily = QuicksandFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = QuicksandFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 34.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = QuicksandFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = QuicksandFamily,
            fontSize = 18.sp,
            lineHeight = 26.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = QuicksandFamily,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = QuicksandFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = QuicksandFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.8.sp
        ),
        // Remaining slots aren't styled with custom sizes, but still get the
        // font family so nothing silently falls back to the system default.
        displayLarge = base.displayLarge.withQuicksand(),
        displayMedium = base.displayMedium.withQuicksand(),
        headlineLarge = base.headlineLarge.withQuicksand(),
        headlineMedium = base.headlineMedium.withQuicksand(),
        titleMedium = base.titleMedium.withQuicksand(),
        titleSmall = base.titleSmall.withQuicksand(),
        bodySmall = base.bodySmall.withQuicksand(),
        labelSmall = base.labelSmall.withQuicksand(),
    )
}

private fun TextStyle.withQuicksand(): TextStyle = copy(fontFamily = QuicksandFamily)

@Composable
fun LockInTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) LockInDarkColors else LockInLightColors
    MaterialTheme(colorScheme = colorScheme, typography = LockInTypography, content = content)
}
