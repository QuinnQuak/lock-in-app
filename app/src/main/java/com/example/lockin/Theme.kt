package com.example.lockin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bubblegum palette: pink primary, orange secondary (ties to the 🔥 streak),
// cherry-red alert/break (a distinct hue from primary so it never reads as a normal button).
val BubblegumPrimary = Color(0xFFFF4F8B)
val BubblegumPrimaryContainer = Color(0xFFFFD6E4)
val BubblegumOnPrimaryContainer = Color(0xFF5C0F2E)
val BubblegumSecondary = Color(0xFFFF9142)
val BubblegumSecondaryContainer = Color(0xFFFFE0C2)
val BubblegumOnSecondaryContainer = Color(0xFF5A2800)
val BubblegumBackground = Color(0xFFFFF3F6)
val BubblegumOnBackground = Color(0xFF3A1F2E)
val BubblegumSurfaceVariant = Color(0xFFFBE4EC)
val BubblegumOnSurfaceVariant = Color(0xFF7A5566)
val BubblegumOutline = Color(0xFFE8B9CB)
val CherryError = Color(0xFFE63950)
val CherryErrorContainer = Color(0xFFFFD9DE)
val CherryOnErrorContainer = Color(0xFF5C1620)

private val LockInLightColors = lightColorScheme(
    primary = BubblegumPrimary,
    onPrimary = Color.White,
    primaryContainer = BubblegumPrimaryContainer,
    onPrimaryContainer = BubblegumOnPrimaryContainer,
    secondary = BubblegumSecondary,
    onSecondary = Color.White,
    secondaryContainer = BubblegumSecondaryContainer,
    onSecondaryContainer = BubblegumOnSecondaryContainer,
    background = BubblegumBackground,
    onBackground = BubblegumOnBackground,
    surface = Color(0xFFFFFAFB),
    onSurface = BubblegumOnBackground,
    surfaceVariant = BubblegumSurfaceVariant,
    onSurfaceVariant = BubblegumOnSurfaceVariant,
    outline = BubblegumOutline,
    error = CherryError,
    onError = Color.White,
    errorContainer = CherryErrorContainer,
    onErrorContainer = CherryOnErrorContainer,
)

private val LockInDarkColors = darkColorScheme(
    primary = Color(0xFFFF6FA3),
    onPrimary = Color(0xFF4A0E28),
    primaryContainer = Color(0xFF6B2246),
    onPrimaryContainer = Color(0xFFFFD6E4),
    secondary = Color(0xFFFFA766),
    onSecondary = Color(0xFF4A2600),
    secondaryContainer = Color(0xFF6B3F10),
    onSecondaryContainer = Color(0xFFFFE0C2),
    background = Color(0xFF241620),
    onBackground = Color(0xFFF5E6EE),
    surface = Color(0xFF331D2C),
    onSurface = Color(0xFFF5E6EE),
    surfaceVariant = Color(0xFF402A38),
    onSurfaceVariant = Color(0xFFD9BDC9),
    outline = Color(0xFF8A6575),
    error = Color(0xFFFF5C72),
    onError = Color(0xFF5C1620),
    errorContainer = Color(0xFF7A2534),
    onErrorContainer = Color(0xFFFFD9DE),
)

// Fredoka: headers, hero numbers, buttons, nav labels — bold, chunky, high-personality.
@OptIn(ExperimentalTextApi::class)
val FredokaFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Nunito: body text, chat, feed/list rows — legible at density where Fredoka's weight would hurt.
@OptIn(ExperimentalTextApi::class)
val NunitoFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Corner radii bumped up app-wide to match Fredoka's rounder letterforms.
private val LockInShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val LockInTypography = Typography().let { base ->
    Typography(
        displaySmall = base.displaySmall.copy(
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 34.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = NunitoFamily,
            fontSize = 18.sp,
            lineHeight = 26.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = NunitoFamily,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.8.sp
        ),
        // Remaining slots aren't styled with custom sizes, but still get a
        // font family so nothing silently falls back to the system default.
        // Display/headline/title = Fredoka (headers); body/labelSmall = Nunito (density).
        displayLarge = base.displayLarge.withFredoka(),
        displayMedium = base.displayMedium.withFredoka(),
        headlineLarge = base.headlineLarge.withFredoka(),
        headlineMedium = base.headlineMedium.withFredoka(),
        titleMedium = base.titleMedium.withFredoka(),
        titleSmall = base.titleSmall.withFredoka(),
        bodySmall = base.bodySmall.withNunito(),
        labelSmall = base.labelSmall.withNunito(),
    )
}

private fun TextStyle.withFredoka(): TextStyle = copy(fontFamily = FredokaFamily)
private fun TextStyle.withNunito(): TextStyle = copy(fontFamily = NunitoFamily)

@Composable
fun LockInTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) LockInDarkColors else LockInLightColors
    MaterialTheme(colorScheme = colorScheme, typography = LockInTypography, shapes = LockInShapes, content = content)
}
