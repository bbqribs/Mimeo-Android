package com.mimeo.android.model

import kotlinx.serialization.Serializable

/** Horizontal alignment applied to reader/article body text. */
@Serializable
enum class ReaderTextAlignOption {
    LEFT,
    JUSTIFIED,
}

/** Default values and valid bounds for the reader-local appearance controls. */
object ReaderAppearanceDefaults {
    const val FONT_SIZE_SP: Int = 16
    const val LINE_HEIGHT_PERCENT: Int = 160
    const val MAX_WIDTH_DP: Int = 720
    val FONT_OPTION: ReaderFontOption = ReaderFontOption.SANS_SERIF

    /** Selected paragraph spacing, as a multiple of the reader body line height. */
    const val PARAGRAPH_SPACING: Float = DEFAULT_PARAGRAPH_SPACING
    val TEXT_ALIGN: ReaderTextAlignOption = ReaderTextAlignOption.LEFT

    const val FONT_SIZE_MIN_SP: Int = 6
    const val FONT_SIZE_MAX_SP: Int = 40
    const val LINE_HEIGHT_MIN_PERCENT: Int = 120
    const val LINE_HEIGHT_MAX_PERCENT: Int = 180
    const val MAX_WIDTH_MIN_DP: Int = 320
    const val MAX_WIDTH_MAX_DP: Int = 1000
}

fun coerceReaderFontSizeSp(value: Int): Int =
    value.coerceIn(ReaderAppearanceDefaults.FONT_SIZE_MIN_SP, ReaderAppearanceDefaults.FONT_SIZE_MAX_SP)

fun coerceReaderLineHeightPercent(value: Int): Int =
    value.coerceIn(ReaderAppearanceDefaults.LINE_HEIGHT_MIN_PERCENT, ReaderAppearanceDefaults.LINE_HEIGHT_MAX_PERCENT)

fun coerceReaderMaxWidthDp(value: Int): Int =
    value.coerceIn(ReaderAppearanceDefaults.MAX_WIDTH_MIN_DP, ReaderAppearanceDefaults.MAX_WIDTH_MAX_DP)

/** Full set of reader-local appearance controls surfaced in the reader Aa panel. */
data class ReaderAppearanceState(
    val fontSizeSp: Int = ReaderAppearanceDefaults.FONT_SIZE_SP,
    val fontOption: ReaderFontOption = ReaderAppearanceDefaults.FONT_OPTION,
    val lineHeightPercent: Int = ReaderAppearanceDefaults.LINE_HEIGHT_PERCENT,
    val maxWidthDp: Int = ReaderAppearanceDefaults.MAX_WIDTH_DP,
    val paragraphSpacing: Float = ReaderAppearanceDefaults.PARAGRAPH_SPACING,
    val textAlign: ReaderTextAlignOption = ReaderAppearanceDefaults.TEXT_ALIGN,
) {
    /** Clamp numeric fields into their valid ranges. */
    fun sanitized(): ReaderAppearanceState = copy(
        fontSizeSp = coerceReaderFontSizeSp(fontSizeSp),
        lineHeightPercent = coerceReaderLineHeightPercent(lineHeightPercent),
        maxWidthDp = coerceReaderMaxWidthDp(maxWidthDp),
        paragraphSpacing = coerceParagraphSpacing(paragraphSpacing),
    )

    /** True when every field already matches the reader appearance defaults. */
    fun isDefault(): Boolean = this == DEFAULTS

    companion object {
        val DEFAULTS: ReaderAppearanceState = ReaderAppearanceState()
    }
}
