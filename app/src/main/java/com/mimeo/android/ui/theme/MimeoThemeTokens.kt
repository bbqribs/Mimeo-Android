package com.mimeo.android.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.mimeo.android.model.VisualThemePreference

enum class MimeoThemeChoice {
    LIGHT,
    DARK,
}

data class MimeoThemeTokens(
    val colors: MimeoColorTokens,
    val typography: MimeoTypographyTokens,
    val spacing: MimeoSpacingTokens,
    val density: MimeoDensityTokens,
    val shapes: MimeoShapeTokens,
)

fun resolveThemeChoice(
    preference: VisualThemePreference,
    systemIsDarkTheme: Boolean,
): MimeoThemeChoice = when (preference) {
    VisualThemePreference.FOLLOW_SYSTEM -> if (systemIsDarkTheme) MimeoThemeChoice.DARK else MimeoThemeChoice.LIGHT
    VisualThemePreference.LIGHT -> MimeoThemeChoice.LIGHT
    VisualThemePreference.DARK -> MimeoThemeChoice.DARK
}

fun colorTokensFor(
    choice: MimeoThemeChoice,
    scheme: MimeoAccentScheme = MimeoAccentScheme.EMBER,
): MimeoColorTokens {
    val accent = accentTokensFor(scheme, choice)
    val base = when (choice) {
        MimeoThemeChoice.LIGHT -> MimeoColors.PaperLight
        MimeoThemeChoice.DARK  -> MimeoColors.EmberDark
    }
    return base.copy(
        accent    = accent.accent,
        accentDim = accent.accentDim,
        accentOn  = accent.accentOn,
        nowTint   = accent.nowTint,
    )
}

fun mimeoThemeTokens(
    preference: VisualThemePreference,
    systemIsDarkTheme: Boolean,
    density: MimeoDensityTokens,
    scheme: MimeoAccentScheme = MimeoAccentScheme.EMBER,
): MimeoThemeTokens = MimeoThemeTokens(
    colors = colorTokensFor(resolveThemeChoice(preference, systemIsDarkTheme), scheme),
    typography = MimeoTypography.PaperEmber,
    spacing = MimeoSpacing,
    density = density,
    shapes = MimeoShapes,
)

val LocalMimeoColorTokens = staticCompositionLocalOf { MimeoColors.PaperLight }
val LocalMimeoTypographyTokens = staticCompositionLocalOf { MimeoTypography.PaperEmber }
val LocalMimeoSpacingTokens = staticCompositionLocalOf { MimeoSpacing }
val LocalMimeoDensityTokens = staticCompositionLocalOf { MimeoDensityDefault }
val LocalMimeoShapeTokens = staticCompositionLocalOf { MimeoShapes }
val LocalMimeoV1Active = staticCompositionLocalOf { false }
val LocalMimeoAccentScheme = staticCompositionLocalOf { MimeoAccentScheme.EMBER }
