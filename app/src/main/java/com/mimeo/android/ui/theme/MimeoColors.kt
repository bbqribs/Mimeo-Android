package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color

data class MimeoColorTokens(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val lineSoft: Color,
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val fg4: Color,
    val accent: Color,
    val accentDim: Color,
    val accentOn: Color,
    val nowTint: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
)

object MimeoColors {
    val PaperLight = MimeoColorTokens(
        bg = Color(0xFFF4EFE7),
        surface = Color(0xFFFAF7F1),
        surfaceHi = Color(0xFFFFFCF6),
        line = Color(0xFFE2DACB),
        lineSoft = Color(0xFFECE5D6),
        fg = Color(0xFF1B1A17),
        fg2 = Color(0xFF67625A),
        fg3 = Color(0xFF9A9388),
        fg4 = Color(0xFFC7BEAE),
        accent = MimeoAccentSchemes.EmberLight.accent,
        accentDim = MimeoAccentSchemes.EmberLight.accentDim,
        accentOn = MimeoAccentSchemes.EmberLight.accentOn,
        nowTint = MimeoAccentSchemes.EmberLight.nowTint,
        success = Color(0xFF3F7A52),
        warn = Color(0xFFB7892A),
        danger = Color(0xFFB84A3F),
    )

    val EmberDark = MimeoColorTokens(
        bg = Color(0xFF0B0B0E),
        surface = Color(0xFF121217),
        surfaceHi = Color(0xFF181822),
        line = Color(0xFF1F1F2A),
        lineSoft = Color(0xFF15151D),
        fg = Color(0xFFECECF1),
        fg2 = Color(0xFF9A99A6),
        fg3 = Color(0xFF5E5D6B),
        fg4 = Color(0xFF3A3947),
        accent = MimeoAccentSchemes.EmberDark.accent,
        accentDim = MimeoAccentSchemes.EmberDark.accentDim,
        accentOn = MimeoAccentSchemes.EmberDark.accentOn,
        nowTint = MimeoAccentSchemes.EmberDark.nowTint,
        success = Color(0xFF7FD1A8),
        warn = Color(0xFFE8C26A),
        danger = Color(0xFFF26E6E),
    )
}
