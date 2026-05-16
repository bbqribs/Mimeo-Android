package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color

data class MimeoAccentTokens(
    val accent: Color,
    val accentDim: Color,
    val accentOn: Color,
    val nowTint: Color,
)

enum class MimeoAccentScheme { EMBER, LILAC, FOREST, SLATE }

object MimeoAccentSchemes {
    val EmberLight = MimeoAccentTokens(
        accent = Color(0xFFC25B2E),
        accentDim = Color(0x1AC25B2E),
        accentOn = Color(0xFFFFFCF6),
        nowTint = Color(0x12C25B2E),
    )
    val EmberDark = MimeoAccentTokens(
        accent = Color(0xFFB6A1FF),
        accentDim = Color(0x24B6A1FF),
        accentOn = Color(0xFF0B0B0E),
        nowTint = Color(0x0FB6A1FF),
    )

    // Calm Lilac — quieter chip purple in light mode, softer pastel purple in dark mode.
    val LilacLight = MimeoAccentTokens(
        accent = Color(0xFF6B49CC),
        accentDim = Color(0x1A6B49CC),
        accentOn = Color(0xFFFFFFFF),
        nowTint = Color(0x126B49CC),
    )
    val LilacDark = MimeoAccentTokens(
        accent = Color(0xFFC9B8FF),
        accentDim = Color(0x24C9B8FF),
        accentOn = Color(0xFF0B0B0E),
        nowTint = Color(0x0FC9B8FF),
    )

    val ForestLight = MimeoAccentTokens(
        accent = Color(0xFF2E7A4F),
        accentDim = Color(0x1A2E7A4F),
        accentOn = Color(0xFFFFFFFF),
        nowTint = Color(0x122E7A4F),
    )
    val ForestDark = MimeoAccentTokens(
        accent = Color(0xFF72C99A),
        accentDim = Color(0x2472C99A),
        accentOn = Color(0xFF0B0B0E),
        nowTint = Color(0x0F72C99A),
    )

    val SlateLight = MimeoAccentTokens(
        accent = Color(0xFF3A6080),
        accentDim = Color(0x1A3A6080),
        accentOn = Color(0xFFFFFFFF),
        nowTint = Color(0x123A6080),
    )
    val SlateDark = MimeoAccentTokens(
        accent = Color(0xFF7BB8D4),
        accentDim = Color(0x247BB8D4),
        accentOn = Color(0xFF0B0B0E),
        nowTint = Color(0x0F7BB8D4),
    )
}

fun accentTokensFor(scheme: MimeoAccentScheme, choice: MimeoThemeChoice): MimeoAccentTokens =
    when (scheme) {
        MimeoAccentScheme.EMBER  -> if (choice == MimeoThemeChoice.LIGHT) MimeoAccentSchemes.EmberLight  else MimeoAccentSchemes.EmberDark
        MimeoAccentScheme.LILAC  -> if (choice == MimeoThemeChoice.LIGHT) MimeoAccentSchemes.LilacLight  else MimeoAccentSchemes.LilacDark
        MimeoAccentScheme.FOREST -> if (choice == MimeoThemeChoice.LIGHT) MimeoAccentSchemes.ForestLight else MimeoAccentSchemes.ForestDark
        MimeoAccentScheme.SLATE  -> if (choice == MimeoThemeChoice.LIGHT) MimeoAccentSchemes.SlateLight  else MimeoAccentSchemes.SlateDark
    }
