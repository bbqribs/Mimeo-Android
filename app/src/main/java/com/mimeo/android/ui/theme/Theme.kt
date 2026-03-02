package com.mimeo.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MimeoDarkColors = darkColorScheme(
    primary = Color(0xFF9C27B0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1F0A25),
    onPrimaryContainer = Color(0xFFF6D6FF),
    secondary = Color(0xFFC48AE0),
    onSecondary = Color(0xFF1A1020),
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
