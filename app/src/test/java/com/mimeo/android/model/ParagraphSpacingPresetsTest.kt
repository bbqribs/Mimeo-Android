package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Pure unit tests for the paragraph-spacing preset helpers — no Android dependency.
 */
class ParagraphSpacingPresetsTest {

    /** Compare spacing lists by hundredths so assertions never depend on float bit equality. */
    private fun assertSpacings(expected: List<Float>, actual: List<Float>) {
        assertEquals(
            expected.map { (it * 100f).roundToInt() },
            actual.map { (it * 100f).roundToInt() },
        )
    }

    @Test
    fun defaultPresets_matchHistoricSmallMediumLargeGaps() {
        assertSpacings(listOf(0.35f, 1.0f, 2.0f), DEFAULT_PARAGRAPH_SPACING_PRESETS)
    }

    @Test
    fun sanitize_sortsAscendingAndDeduplicates() {
        assertSpacings(
            listOf(0.5f, 1.0f, 2.0f),
            sanitizeParagraphSpacingPresets(listOf(2.0f, 0.5f, 1.0f, 0.5f)),
        )
    }

    @Test
    fun sanitize_dropsOutOfRangeValues() {
        assertSpacings(
            listOf(0.0f, 1.0f, 4.0f),
            sanitizeParagraphSpacingPresets(listOf(-1.0f, 0.0f, 1.0f, 4.0f, 9.0f)),
        )
    }

    @Test
    fun sanitize_capsCountToMaximum() {
        val result = sanitizeParagraphSpacingPresets(
            listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f),
        )
        assertEquals(MAX_PARAGRAPH_SPACING_PRESETS, result.size)
        assertSpacings(listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f), result)
    }

    @Test
    fun sanitize_roundsToHundredthsAndDeduplicates() {
        // 0.351 and 0.354 both round to 0.35.
        assertSpacings(listOf(0.35f), sanitizeParagraphSpacingPresets(listOf(0.351f, 0.354f)))
    }

    @Test
    fun sanitize_dropsNonFiniteValues() {
        assertSpacings(
            listOf(1.0f),
            sanitizeParagraphSpacingPresets(listOf(Float.NaN, Float.POSITIVE_INFINITY, 1.0f)),
        )
    }

    @Test
    fun entryValid_acceptsBlankAndInBoundsNumbers() {
        assertTrue(isParagraphSpacingPresetEntryValid(""))
        assertTrue(isParagraphSpacingPresetEntryValid("   "))
        assertTrue(isParagraphSpacingPresetEntryValid("0"))
        assertTrue(isParagraphSpacingPresetEntryValid("4"))
        assertTrue(isParagraphSpacingPresetEntryValid(" 0.35 "))
    }

    @Test
    fun entryValid_rejectsOutOfBoundsAndNonNumericInput() {
        assertFalse(isParagraphSpacingPresetEntryValid("-0.1"))
        assertFalse(isParagraphSpacingPresetEntryValid("4.5"))
        assertFalse(isParagraphSpacingPresetEntryValid("wide"))
        assertFalse(isParagraphSpacingPresetEntryValid("1.2.3"))
    }

    @Test
    fun parse_blankOrNull_returnsDefaults() {
        assertSpacings(DEFAULT_PARAGRAPH_SPACING_PRESETS, parseParagraphSpacingPresets(null))
        assertSpacings(DEFAULT_PARAGRAPH_SPACING_PRESETS, parseParagraphSpacingPresets("   "))
    }

    @Test
    fun parse_garbageString_returnsDefaults() {
        assertSpacings(DEFAULT_PARAGRAPH_SPACING_PRESETS, parseParagraphSpacingPresets("wide, ???"))
    }

    @Test
    fun parse_allOutOfRange_returnsDefaults() {
        assertSpacings(DEFAULT_PARAGRAPH_SPACING_PRESETS, parseParagraphSpacingPresets("-1, 9.0"))
    }

    @Test
    fun parse_validString_sortsAndDeduplicates() {
        assertSpacings(listOf(0.5f, 1.0f, 2.0f), parseParagraphSpacingPresets("2.0, 0.5, 1.0, 0.5"))
    }

    @Test
    fun format_trimsTrailingZeros() {
        assertEquals("0.35, 1, 2", formatParagraphSpacingPresets(DEFAULT_PARAGRAPH_SPACING_PRESETS))
    }

    @Test
    fun formatAndParse_roundTrip() {
        val presets = listOf(0.25f, 1.0f, 3.5f)
        assertSpacings(presets, parseParagraphSpacingPresets(formatParagraphSpacingPresets(presets)))
    }

    @Test
    fun coerceParagraphSpacing_clampsToValidRange() {
        assertEquals(PARAGRAPH_SPACING_PRESET_MIN, coerceParagraphSpacing(-5f), 0.0001f)
        assertEquals(PARAGRAPH_SPACING_PRESET_MAX, coerceParagraphSpacing(99f), 0.0001f)
        assertEquals(1.25f, coerceParagraphSpacing(1.25f), 0.0001f)
        assertEquals(DEFAULT_PARAGRAPH_SPACING, coerceParagraphSpacing(Float.NaN), 0.0001f)
    }

    @Test
    fun parseStoredParagraphSpacing_readsNumericValue() {
        assertEquals(0.5f, parseStoredParagraphSpacing("0.5"), 0.0001f)
        assertEquals(PARAGRAPH_SPACING_PRESET_MAX, parseStoredParagraphSpacing("99"), 0.0001f)
    }

    @Test
    fun parseStoredParagraphSpacing_migratesLegacyEnumNames() {
        assertEquals(0.35f, parseStoredParagraphSpacing("SMALL"), 0.0001f)
        assertEquals(1.0f, parseStoredParagraphSpacing("MEDIUM"), 0.0001f)
        assertEquals(2.0f, parseStoredParagraphSpacing("LARGE"), 0.0001f)
    }

    @Test
    fun parseStoredParagraphSpacing_blankOrGarbage_returnsDefault() {
        assertEquals(DEFAULT_PARAGRAPH_SPACING, parseStoredParagraphSpacing(null), 0.0001f)
        assertEquals(DEFAULT_PARAGRAPH_SPACING, parseStoredParagraphSpacing("   "), 0.0001f)
        assertEquals(DEFAULT_PARAGRAPH_SPACING, parseStoredParagraphSpacing("wide"), 0.0001f)
    }
}
