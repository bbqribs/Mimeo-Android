package com.mimeo.android.ui.theme

import androidx.compose.ui.graphics.Color

data class MimeoAccentTokens(
    val accent: Color,
    val accentDim: Color,
    val accentOn: Color,
    val nowTint: Color,
)

// Named accent schemes. Only Ember ships now; Lilac, Forest, Slate are reserved for later.
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
}
