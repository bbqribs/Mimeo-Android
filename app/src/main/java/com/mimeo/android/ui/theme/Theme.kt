package com.mimeo.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MimeoDarkColors = darkColorScheme(
    primary = Color(0xFFC6A7FF),
    onPrimary = Color(0xFF24143D),
    primaryContainer = Color(0xFF2C1A45),
    onPrimaryContainer = Color(0xFFF2E6FF),
    secondary = Color(0xFFD6BFFF),
    onSecondary = Color(0xFF2A1B40),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F1F8),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF4F1F8),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFB9B3C2),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
)

@Composable
fun MimeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MimeoDarkColors,
        content = content,
    )
}
