package com.mimeo.android.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

data class MimeoTypographyTokens(
    val display: TextStyle,
    val title: TextStyle,
    val row: TextStyle,
    val body: TextStyle,
    val bodyRead: TextStyle,
    val meta: TextStyle,
    val section: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
)

object MimeoTypography {
    private val serif = FontFamily.Serif
    private val sans = FontFamily.SansSerif
    private val mono = FontFamily.Monospace

    val PaperEmber = MimeoTypographyTokens(
        display = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Medium,
            fontSize = 26.sp,
            lineHeight = 30.68.sp,
            letterSpacing = (-0.012).em,
        ),
        title = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.008).em,
        ),
        row = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 19.8.sp,
            letterSpacing = (-0.005).em,
        ),
        body = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.7.sp,
        ),
        bodyRead = TextStyle(
            fontFamily = serif,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 27.2.sp,
        ),
        meta = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.4.sp,
        ),
        section = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.4.sp,
            letterSpacing = 0.08.em,
        ),
        caption = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.4.sp,
            letterSpacing = 0.04.em,
        ),
        button = TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            letterSpacing = (-0.005).em,
        ),
        mono = TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 20.4.sp,
        ),
    )
}
