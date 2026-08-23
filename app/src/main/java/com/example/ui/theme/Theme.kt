package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val CleanMinimalColorScheme = darkColorScheme(
    primary = Color(0xFF00A884),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005C4B),
    onPrimaryContainer = Color(0xFFE9EDEF),
    secondary = Color(0xFF00A884),
    onSecondary = Color.White,
    background = Color(0xFF121B22),
    onBackground = Color(0xFFE9EDEF),
    surface = Color(0xFF1F2C34),
    onSurface = Color(0xFFE9EDEF),
    surfaceVariant = Color(0xFF202C33),
    onSurfaceVariant = Color(0xFF8696A0),
    outline = Color(0xFF2A3942)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force the dark, high-contrast minimal aesthetic
    dynamicColor: Boolean = false, // Disable to respect the designer-provided brand palette
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CleanMinimalColorScheme,
        typography = Typography,
        content = content
    )
}
