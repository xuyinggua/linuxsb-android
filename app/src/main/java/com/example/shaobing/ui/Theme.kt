package com.example.shaobing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A4E22),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF351100),
    secondary = Color(0xFF77574A),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF2DFD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4B78D),
    onPrimary = Color(0xFF51230A),
    background = Color(0xFF1E110A),
    surface = Color(0xFF1E110A),
    surfaceVariant = Color(0xFF534331)
)

@Composable
fun ShaoBingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
