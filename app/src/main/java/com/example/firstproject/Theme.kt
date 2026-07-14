package com.example.firstproject

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LavenderPrimary = Color(0xFF7B6EF6)
val LavenderPrimaryContainer = Color(0xFFE8E4FF)
val LavenderOnPrimaryContainer = Color(0xFF29235C)
val SageSecondary = Color(0xFF5E9C82)
val CalmBackground = Color(0xFFFBFAFF)
val CalmOnBackground = Color(0xFF2B2640)
val CalmSurfaceVariant = Color(0xFFF1EEFB)
val CalmOnSurfaceVariant = Color(0xFF5B5570)

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
    onSurfaceVariant = Color(0xFFC6C1DA),
)

@Composable
fun LockInTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) LockInDarkColors else LockInLightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
