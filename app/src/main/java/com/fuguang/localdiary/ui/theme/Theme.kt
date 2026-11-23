package com.fuguang.localdiary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
private fun colors() = lightColorScheme(
    primary = Sage,
    onPrimary = OnSage,
    secondary = Sage,
    onSecondary = OnSage,
    tertiary = Sage,
    primaryContainer = SageLight,
    onPrimaryContainer = Ink,
    secondaryContainer = SageLight,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkSoft,
    outline = Line
)

@Composable
fun FuguangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors(), typography = MaterialTheme.typography) { AppBackground(content) }
}
