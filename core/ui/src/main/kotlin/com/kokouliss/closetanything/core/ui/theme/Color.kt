package com.kokouliss.closetanything.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// From Panos's AnyKind kit: the ink and paper neutrals the whole family shares, and the teal of the
// logo triangle as this app's lead colour (Shelf has the amber square, Cookbook the red circle).
// The lead is darkened where it is text or a button, so it reads at 4.5 to 1 on paper; the bright
// teal of the logo itself only appears as a tint. ColorContrastTest keeps every text pair readable.
val LightColors = lightColorScheme(
    primary = Color(0xFF21677A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EBEF),
    onPrimaryContainer = Color(0xFF0F3C47),
    secondary = Color(0xFF4A4D55),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEEF2),
    onSecondaryContainer = Color(0xFF171A21),
    tertiary = Color(0xFF8A5E13),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6EBD2),
    onTertiaryContainer = Color(0xFF3E2A05),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF171A21),
    surface = Color(0xFFF7F7FA),
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFEDEEF2),
    onSurfaceVariant = Color(0xFF4A4D55),
    outline = Color(0xFF74767E),
    outlineVariant = Color(0xFFDCDEE5),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C323A),
    inverseOnSurface = Color(0xFFECEEF2),
    inversePrimary = Color(0xFF8FD0E0),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFFFFFFF),
    // Cards and sheets are white, so they stand out from the paper under them.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDEEF2),
    surfaceContainerHigh = Color(0xFFE8EAEF),
    surfaceContainerHighest = Color(0xFFE3E5EA),
)

// The same at night: the ink of the AnyKind logos as paper, bright writing, a lighter teal.
val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0E0),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF1F5C6B),
    onPrimaryContainer = Color(0xFFD4F0F6),
    secondary = Color(0xFFB9BDC6),
    onSecondary = Color(0xFF171A21),
    secondaryContainer = Color(0xFF2C323A),
    onSecondaryContainer = Color(0xFFECEEF2),
    tertiary = Color(0xFFE2B65C),
    onTertiary = Color(0xFF3E2A05),
    tertiaryContainer = Color(0xFF5A4010),
    onTertiaryContainer = Color(0xFFF6EBD2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121417),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF2C323A),
    onSurfaceVariant = Color(0xFFB9BDC6),
    outline = Color(0xFF8C9099),
    outlineVariant = Color(0xFF3A3F47),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFECEEF2),
    inverseOnSurface = Color(0xFF2C323A),
    inversePrimary = Color(0xFF21677A),
    surfaceDim = Color(0xFF121417),
    surfaceBright = Color(0xFF353A42),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF1B1E23),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF262B32),
    surfaceContainerHighest = Color(0xFF2C323A),
)
