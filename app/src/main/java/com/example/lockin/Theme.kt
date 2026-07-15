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

// Warm / energetic palette: amber drive, green momentum, warm-cream canvas.
val AmberPrimary = Color(0xFFF57C1F)
val AmberPrimaryContainer = Color(0xFFFFE1C4)
val AmberOnPrimaryContainer = Color(0xFF5A2A00)
val GreenSecondary = Color(0xFF2BB673)
val GreenSecondaryContainer = Color(0xFFC7EFD8)
val GreenOnSecondaryContainer = Color(0xFF0C3B22)
val WarmBackground = Color(0xFFFBF7F2)
val WarmOnBackground = Color(0xFF2A2019)
val WarmSurfaceVariant = Color(0xFFF2E9DF)
val WarmOnSurfaceVariant = Color(0xFF6E5D4E)
val WarmOutline = Color(0xFFD8C9B8)
val RedError = Color(0xFFE8455F)
val RedErrorContainer = Color(0xFFFFDBE0)
val RedOnErrorContainer = Color(0xFF5C1620)

private val LockInLightColors = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.White,
    primaryContainer = AmberPrimaryContainer,
    onPrimaryContainer = AmberOnPrimaryContainer,
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer,
    background = WarmBackground,
    onBackground = WarmOnBackground,
    surface = Color.White,
    onSurface = WarmOnBackground,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    outline = WarmOutline,
    error = RedError,
    onError = Color.White,
    errorContainer = RedErrorContainer,
    onErrorContainer = RedOnErrorContainer,
)

private val LockInDarkColors = darkColorScheme(
    primary = Color(0xFFFFB165),
    onPrimary = Color(0xFF4A2600),
    primaryContainer = Color(0xFF6B3D10),
    onPrimaryContainer = Color(0xFFFFE1C4),
    secondary = Color(0xFF6FDCA4),
    onSecondary = Color(0xFF0A3B22),
    secondaryContainer = Color(0xFF115535),
    onSecondaryContainer = Color(0xFFC7EFD8),
    background = Color(0xFF1A1613),
    onBackground = Color(0xFFF0E8E1),
    surface = Color(0xFF231D18),
    onSurface = Color(0xFFF0E8E1),
    surfaceVariant = Color(0xFF352C24),
    onSurfaceVariant = Color(0xFFD9CBBD),
    outline = Color(0xFF7A6A5A),
    error = Color(0xFFFFB3BE),
    onError = Color(0xFF5C1620),
    errorContainer = Color(0xFF7A2534),
    onErrorContainer = Color(0xFFFFDBE0),
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
