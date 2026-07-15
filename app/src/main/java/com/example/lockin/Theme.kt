package com.example.lockin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

// Theme picker skins (decided 2026-07-15): a curated set of accent skins, not an
// open picker, to keep the system cohesive. Each has its own light+dark pair.
// Error/break stays the same cherry red across every skin -- it's a functional
// signal (the break/alarm state), not a brand color, and re-skinning it per
// theme would mean relearning "what red means" on every switch.
enum class AppTheme(val label: String) {
    BUBBLEGUM("Bubblegum"),
    PEACH("Peach"),
    BERRY("Berry"),
    SUNSET("Sunset"),
}

val CherryError = Color(0xFFE63950)
val CherryErrorContainer = Color(0xFFFFD9DE)
val CherryOnErrorContainer = Color(0xFF5C1620)
private val CherryErrorDark = Color(0xFFFF5C72)
private val CherryOnErrorDark = Color(0xFF5C1620)
private val CherryErrorContainerDark = Color(0xFF7A2534)
private val CherryOnErrorContainerDark = Color(0xFFFFD9DE)

// Bubblegum (default): pink primary, orange secondary -- ties to the 🔥 streak.
val BubblegumPrimary = Color(0xFFFF4F8B)
private val BubblegumLightColors = lightColorScheme(
    primary = BubblegumPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6E4),
    onPrimaryContainer = Color(0xFF5C0F2E),
    secondary = Color(0xFFFF9142),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0C2),
    onSecondaryContainer = Color(0xFF5A2800),
    background = Color(0xFFFFF3F6),
    onBackground = Color(0xFF3A1F2E),
    surface = Color(0xFFFFFAFB),
    onSurface = Color(0xFF3A1F2E),
    surfaceVariant = Color(0xFFFBE4EC),
    onSurfaceVariant = Color(0xFF7A5566),
    outline = Color(0xFFE8B9CB),
    error = CherryError,
    onError = Color.White,
    errorContainer = CherryErrorContainer,
    onErrorContainer = CherryOnErrorContainer,
)
private val BubblegumDarkColors = darkColorScheme(
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
    error = CherryErrorDark,
    onError = CherryOnErrorDark,
    errorContainer = CherryErrorContainerDark,
    onErrorContainer = CherryOnErrorContainerDark,
)

// Peach: orange primary / pink secondary -- Bubblegum's roles swapped.
private val PeachLightColors = lightColorScheme(
    primary = Color(0xFFFF9142),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0C2),
    onPrimaryContainer = Color(0xFF5A2800),
    secondary = Color(0xFFFF4F8B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD6E4),
    onSecondaryContainer = Color(0xFF5C0F2E),
    background = Color(0xFFFFF4EC),
    onBackground = Color(0xFF3A2A1F),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF3A2A1F),
    surfaceVariant = Color(0xFFFCEBDD),
    onSurfaceVariant = Color(0xFF7A6152),
    outline = Color(0xFFE8CBB0),
    error = CherryError,
    onError = Color.White,
    errorContainer = CherryErrorContainer,
    onErrorContainer = CherryOnErrorContainer,
)
private val PeachDarkColors = darkColorScheme(
    primary = Color(0xFFFFA766),
    onPrimary = Color(0xFF4A2600),
    primaryContainer = Color(0xFF6B3F10),
    onPrimaryContainer = Color(0xFFFFE0C2),
    secondary = Color(0xFFFF6FA3),
    onSecondary = Color(0xFF4A0E28),
    secondaryContainer = Color(0xFF6B2246),
    onSecondaryContainer = Color(0xFFFFD6E4),
    background = Color(0xFF241A14),
    onBackground = Color(0xFFF5EAE0),
    surface = Color(0xFF33251C),
    onSurface = Color(0xFFF5EAE0),
    surfaceVariant = Color(0xFF402F24),
    onSurfaceVariant = Color(0xFFD9C4B3),
    outline = Color(0xFF8A7361),
    error = CherryErrorDark,
    onError = CherryOnErrorDark,
    errorContainer = CherryErrorContainerDark,
    onErrorContainer = CherryOnErrorContainerDark,
)

// Berry: deeper magenta-pink primary / coral secondary.
private val BerryLightColors = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD0E0),
    onPrimaryContainer = Color(0xFF4A0A28),
    secondary = Color(0xFFFF7A5C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDD2),
    onSecondaryContainer = Color(0xFF5C2416),
    background = Color(0xFFFDF0F5),
    onBackground = Color(0xFF351A2A),
    surface = Color(0xFFFFFAFC),
    onSurface = Color(0xFF351A2A),
    surfaceVariant = Color(0xFFF7E3ED),
    onSurfaceVariant = Color(0xFF7A5568),
    outline = Color(0xFFE0B8CC),
    error = CherryError,
    onError = Color.White,
    errorContainer = CherryErrorContainer,
    onErrorContainer = CherryOnErrorContainer,
)
private val BerryDarkColors = darkColorScheme(
    primary = Color(0xFFFF6FA8),
    onPrimary = Color(0xFF4A0A28),
    primaryContainer = Color(0xFF6B1F45),
    onPrimaryContainer = Color(0xFFFFD0E0),
    secondary = Color(0xFFFFA084),
    onSecondary = Color(0xFF4A1C0E),
    secondaryContainer = Color(0xFF6B3624),
    onSecondaryContainer = Color(0xFFFFDDD2),
    background = Color(0xFF1F1420),
    onBackground = Color(0xFFF5E3EE),
    surface = Color(0xFF2E1E2E),
    onSurface = Color(0xFFF5E3EE),
    surfaceVariant = Color(0xFF3D2A38),
    onSurfaceVariant = Color(0xFFD6BBCB),
    outline = Color(0xFF866478),
    error = CherryErrorDark,
    onError = CherryOnErrorDark,
    errorContainer = CherryErrorContainerDark,
    onErrorContainer = CherryOnErrorContainerDark,
)

// Sunset: red-orange forward, hotter/punchier -- golden-amber secondary.
private val SunsetLightColors = lightColorScheme(
    primary = Color(0xFFFF5A36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9CC),
    onPrimaryContainer = Color(0xFF5C1C0A),
    secondary = Color(0xFFFFB100),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF4A3300),
    background = Color(0xFFFFF1EC),
    onBackground = Color(0xFF3A1F14),
    surface = Color(0xFFFFFAF7),
    onSurface = Color(0xFF3A1F14),
    surfaceVariant = Color(0xFFFCE3D6),
    onSurfaceVariant = Color(0xFF7A5A48),
    outline = Color(0xFFE8BEA6),
    error = CherryError,
    onError = Color.White,
    errorContainer = CherryErrorContainer,
    onErrorContainer = CherryOnErrorContainer,
)
private val SunsetDarkColors = darkColorScheme(
    primary = Color(0xFFFF8560),
    onPrimary = Color(0xFF4A1C0A),
    primaryContainer = Color(0xFF6B2C14),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF4A3300),
    secondaryContainer = Color(0xFF6B4E10),
    onSecondaryContainer = Color(0xFFFFECB3),
    background = Color(0xFF241209),
    onBackground = Color(0xFFF5E5DC),
    surface = Color(0xFF332014),
    onSurface = Color(0xFFF5E5DC),
    surfaceVariant = Color(0xFF402C1F),
    onSurfaceVariant = Color(0xFFD9BBA8),
    outline = Color(0xFF8A6852),
    error = CherryErrorDark,
    onError = CherryOnErrorDark,
    errorContainer = CherryErrorContainerDark,
    onErrorContainer = CherryOnErrorContainerDark,
)

// Not private: the theme-picker UI (ProfileScreen) uses this to preview each
// skin's own primary color as a swatch, and LockInTheme below applies it live.
fun AppTheme.colorScheme(dark: Boolean): ColorScheme = when (this) {
    AppTheme.BUBBLEGUM -> if (dark) BubblegumDarkColors else BubblegumLightColors
    AppTheme.PEACH -> if (dark) PeachDarkColors else PeachLightColors
    AppTheme.BERRY -> if (dark) BerryDarkColors else BerryLightColors
    AppTheme.SUNSET -> if (dark) SunsetDarkColors else SunsetLightColors
}

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
fun LockInTheme(theme: AppTheme = AppTheme.BUBBLEGUM, content: @Composable () -> Unit) {
    val colorScheme = theme.colorScheme(dark = isSystemInDarkTheme())
    MaterialTheme(colorScheme = colorScheme, typography = LockInTypography, shapes = LockInShapes, content = content)
}
