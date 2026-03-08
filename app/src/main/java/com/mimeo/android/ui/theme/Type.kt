package com.mimeo.android.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.mimeo.android.R
import com.mimeo.android.model.ReaderFontOption

val ReaderLiterataFontFamily = FontFamily(
    Font(R.font.literata_regular),
    Font(R.font.literata_italic),
)

fun ReaderFontOption.toFontFamily(): FontFamily = when (this) {
    ReaderFontOption.LITERATA -> ReaderLiterataFontFamily
    ReaderFontOption.SERIF -> FontFamily.Serif
    ReaderFontOption.SANS_SERIF -> FontFamily.SansSerif
    ReaderFontOption.MONOSPACE -> FontFamily.Monospace
}
