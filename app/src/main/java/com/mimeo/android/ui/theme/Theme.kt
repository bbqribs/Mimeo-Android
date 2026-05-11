package com.mimeo.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mimeo.android.model.VisualDensityPreference
import com.mimeo.android.model.VisualThemePreference

// Emergency compile-time override for local debug builds.
// Primary control is the debug-only Developer Settings toggle.
internal const val VISUAL_DESIGN_V1_FORCE_ENABLED = false

internal enum class MimeoThemeRuntimePath {
    LEGACY,
    VISUAL_V1,
}

internal fun resolveThemeRuntimePath(enableVisualDesignV1: Boolean): MimeoThemeRuntimePath =
    if (enableVisualDesignV1) MimeoThemeRuntimePath.VISUAL_V1 else MimeoThemeRuntimePath.LEGACY

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
fun MimeoAppTheme(
    visualThemePreference: VisualThemePreference,
    visualDensityPreference: VisualDensityPreference,
    enableVisualDesignV1: Boolean = false,
    content: @Composable () -> Unit,
) {
    when (resolveThemeRuntimePath(enableVisualDesignV1 || VISUAL_DESIGN_V1_FORCE_ENABLED)) {
        MimeoThemeRuntimePath.LEGACY -> MimeoTheme(content = content)
        MimeoThemeRuntimePath.VISUAL_V1 -> MimeoThemeV1(
            visualThemePreference = visualThemePreference,
            visualDensityPreference = visualDensityPreference,
            content = content,
        )
    }
}

@Composable
fun MimeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MimeoDarkColors,
        content = content,
    )
}

@Composable
private fun MimeoThemeV1(
    visualThemePreference: VisualThemePreference,
    visualDensityPreference: VisualDensityPreference,
    content: @Composable () -> Unit,
) {
    val systemIsDarkTheme = isSystemInDarkTheme()
    val densityTokens = remember(visualDensityPreference) {
        densityTokensFor(visualDensityPreference)
    }
    val themeTokens = remember(visualThemePreference, systemIsDarkTheme, densityTokens) {
        mimeoThemeTokens(
            preference = visualThemePreference,
            systemIsDarkTheme = systemIsDarkTheme,
            density = densityTokens,
        )
    }
    val useDarkMaterialColors = remember(visualThemePreference, systemIsDarkTheme) {
        resolveThemeChoice(visualThemePreference, systemIsDarkTheme) == MimeoThemeChoice.DARK
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkMaterialColors
        }
    }
    val colorScheme = remember(themeTokens.colors, useDarkMaterialColors) {
        materialColorSchemeFrom(themeTokens.colors, useDarkMaterialColors)
    }
    val typography = remember(themeTokens.typography) {
        materialTypographyFrom(themeTokens.typography)
    }
    val shapes = remember(themeTokens.shapes) {
        materialShapesFrom(themeTokens.shapes)
    }

    CompositionLocalProvider(
        LocalMimeoColorTokens provides themeTokens.colors,
        LocalMimeoTypographyTokens provides themeTokens.typography,
        LocalMimeoSpacingTokens provides themeTokens.spacing,
        LocalMimeoDensityTokens provides themeTokens.density,
        LocalMimeoShapeTokens provides themeTokens.shapes,
        LocalMimeoV1Active provides true,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

private fun materialColorSchemeFrom(colors: MimeoColorTokens, darkMode: Boolean): ColorScheme {
    return if (darkMode) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentOn,
            primaryContainer = colors.surfaceHi,
            onPrimaryContainer = colors.fg,
            secondary = colors.fg2,
            onSecondary = colors.bg,
            secondaryContainer = colors.accentDim,
            onSecondaryContainer = colors.fg,
            tertiary = colors.success,
            onTertiary = colors.bg,
            tertiaryContainer = colors.nowTint,
            onTertiaryContainer = colors.fg,
            background = colors.bg,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.surfaceHi,
            onSurfaceVariant = colors.fg2,
            surfaceTint = colors.accent,
            outline = colors.line,
            outlineVariant = colors.lineSoft,
            error = colors.danger,
            onError = colors.accentOn,
            errorContainer = colors.danger.copy(alpha = 0.2f),
            onErrorContainer = colors.fg,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentOn,
            primaryContainer = colors.surfaceHi,
            onPrimaryContainer = colors.fg,
            secondary = colors.fg2,
            onSecondary = colors.bg,
            secondaryContainer = colors.accentDim,
            onSecondaryContainer = colors.fg,
            tertiary = colors.success,
            onTertiary = colors.bg,
            tertiaryContainer = colors.nowTint,
            onTertiaryContainer = colors.fg,
            background = colors.bg,
            onBackground = colors.fg,
            surface = colors.surface,
            onSurface = colors.fg,
            surfaceVariant = colors.surfaceHi,
            onSurfaceVariant = colors.fg2,
            surfaceTint = colors.accent,
            outline = colors.line,
            outlineVariant = colors.lineSoft,
            error = colors.danger,
            onError = colors.accentOn,
            errorContainer = colors.danger.copy(alpha = 0.2f),
            onErrorContainer = colors.fg,
        )
    }
}

private fun materialTypographyFrom(tokens: MimeoTypographyTokens): Typography {
    return Typography(
        displayLarge = tokens.display,
        displayMedium = tokens.display,
        displaySmall = tokens.display,
        headlineLarge = tokens.title,
        headlineMedium = tokens.title,
        headlineSmall = tokens.title,
        titleLarge = tokens.title,
        titleMedium = tokens.row,
        titleSmall = tokens.row,
        bodyLarge = tokens.body,
        bodyMedium = tokens.body,
        bodySmall = tokens.meta,
        labelLarge = tokens.button,
        labelMedium = tokens.caption,
        labelSmall = tokens.caption,
    )
}

private fun materialShapesFrom(tokens: MimeoShapeTokens): Shapes {
    return Shapes(
        small = cornerShapeOrDefault(tokens.input, RoundedCornerShape(8.dp)),
        medium = cornerShapeOrDefault(tokens.card, RoundedCornerShape(12.dp)),
        large = cornerShapeOrDefault(tokens.sheet, RoundedCornerShape(16.dp)),
    )
}

private fun cornerShapeOrDefault(shape: Shape, fallback: CornerBasedShape): CornerBasedShape {
    return shape as? CornerBasedShape ?: fallback
}
