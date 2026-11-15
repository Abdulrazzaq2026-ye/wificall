package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HisnDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = TextPrimaryDark,
    secondary = CyanCipher,
    onSecondary = Color(0xFF02232B),
    secondaryContainer = CyanDeep,
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = AmberSecurity,
    onTertiary = Color(0xFF2B1300),
    tertiaryContainer = AmberDeep,
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonDeep,
    onErrorContainer = Color(0xFFFEE2E2),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1C302B)
)

private val HisnLightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = TextPrimaryLight,
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF083344),
    tertiary = AmberSecurity,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = MintBackground,
    onBackground = TextPrimaryLight,
    surface = MintSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = MintSurfaceContainer,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = MintSurface,
    surfaceContainer = MintSurfaceContainer,
    surfaceContainerHigh = MintSurfaceElevated,
    outline = MintOutline,
    outlineVariant = Color(0xFFD1E8DF)
)

val HisnShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HisnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HisnDarkColorScheme else HisnLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HisnTypography,
        shapes = HisnShapes,
        content = content
    )
}
