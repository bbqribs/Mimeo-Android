package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAppearanceTest {

    @Test
    fun defaults_matchExpectedReaderAppearanceValues() {
        val defaults = ReaderAppearanceState.DEFAULTS
        assertEquals(16, defaults.fontSizeSp)
        assertEquals(ReaderFontOption.SANS_SERIF, defaults.fontOption)
        assertEquals(160, defaults.lineHeightPercent)
        assertEquals(720, defaults.maxWidthDp)
        assertEquals(DEFAULT_PARAGRAPH_SPACING, defaults.paragraphSpacing, 0.0001f)
        assertEquals(ReaderTextAlignOption.LEFT, defaults.textAlign)
    }

    @Test
    fun appSettingsDefault_readingTextAlign_isLeft() {
        assertEquals(ReaderTextAlignOption.LEFT, AppSettings().readingTextAlign)
    }

    @Test
    fun coerceReaderFontSizeSp_clampsBelowAndAboveBounds() {
        assertEquals(ReaderAppearanceDefaults.FONT_SIZE_MIN_SP, coerceReaderFontSizeSp(2))
        assertEquals(ReaderAppearanceDefaults.FONT_SIZE_MAX_SP, coerceReaderFontSizeSp(999))
        assertEquals(22, coerceReaderFontSizeSp(22))
    }

    @Test
    fun coerceReaderLineHeightPercent_clampsBelowAndAboveBounds() {
        assertEquals(ReaderAppearanceDefaults.LINE_HEIGHT_MIN_PERCENT, coerceReaderLineHeightPercent(50))
        assertEquals(ReaderAppearanceDefaults.LINE_HEIGHT_MAX_PERCENT, coerceReaderLineHeightPercent(400))
        assertEquals(150, coerceReaderLineHeightPercent(150))
    }

    @Test
    fun coerceReaderMaxWidthDp_clampsBelowAndAboveBounds() {
        assertEquals(ReaderAppearanceDefaults.MAX_WIDTH_MIN_DP, coerceReaderMaxWidthDp(10))
        assertEquals(ReaderAppearanceDefaults.MAX_WIDTH_MAX_DP, coerceReaderMaxWidthDp(5000))
        assertEquals(600, coerceReaderMaxWidthDp(600))
    }

    @Test
    fun sanitized_clampsOutOfRangeNumericFieldsAndKeepsValidFields() {
        val raw = ReaderAppearanceState(
            fontSizeSp = 1000,
            fontOption = ReaderFontOption.MONOSPACE,
            lineHeightPercent = 1,
            maxWidthDp = 99999,
            paragraphSpacing = 99f,
            textAlign = ReaderTextAlignOption.JUSTIFIED,
        )
        val sanitized = raw.sanitized()
        assertEquals(ReaderAppearanceDefaults.FONT_SIZE_MAX_SP, sanitized.fontSizeSp)
        assertEquals(ReaderAppearanceDefaults.LINE_HEIGHT_MIN_PERCENT, sanitized.lineHeightPercent)
        assertEquals(ReaderAppearanceDefaults.MAX_WIDTH_MAX_DP, sanitized.maxWidthDp)
        assertEquals(PARAGRAPH_SPACING_PRESET_MAX, sanitized.paragraphSpacing, 0.0001f)
        assertEquals(ReaderFontOption.MONOSPACE, sanitized.fontOption)
        assertEquals(ReaderTextAlignOption.JUSTIFIED, sanitized.textAlign)
    }

    @Test
    fun sanitized_leavesInRangeStateUnchanged() {
        val inRange = ReaderAppearanceState(
            fontSizeSp = 18,
            lineHeightPercent = 150,
            maxWidthDp = 640,
        )
        assertEquals(inRange, inRange.sanitized())
    }

    @Test
    fun isDefault_trueOnlyForDefaultState() {
        assertTrue(ReaderAppearanceState().isDefault())
        assertTrue(ReaderAppearanceState.DEFAULTS.isDefault())
        assertFalse(ReaderAppearanceState(fontSizeSp = 24).isDefault())
        assertFalse(ReaderAppearanceState(textAlign = ReaderTextAlignOption.JUSTIFIED).isDefault())
    }
}
