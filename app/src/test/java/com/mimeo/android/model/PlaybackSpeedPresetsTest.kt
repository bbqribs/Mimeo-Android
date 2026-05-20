package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Pure unit tests for the playback speed preset helpers — no Android dependency.
 */
class PlaybackSpeedPresetsTest {

    /** Compare speed lists by hundredths so the assertion never depends on float bit equality. */
    private fun assertSpeeds(expected: List<Float>, actual: List<Float>) {
        assertEquals(
            expected.map { (it * 100f).roundToInt() },
            actual.map { (it * 100f).roundToInt() },
        )
    }

    @Test
    fun defaultPresets_matchHistoricHardCodedList() {
        assertSpeeds(listOf(1.0f, 1.25f, 1.4f, 1.75f, 2.0f), DEFAULT_PLAYBACK_SPEED_PRESETS)
    }

    @Test
    fun sanitize_sortsAscendingAndDeduplicates() {
        assertSpeeds(
            listOf(1.0f, 1.25f, 2.0f),
            sanitizePlaybackSpeedPresets(listOf(2.0f, 1.0f, 1.25f, 1.0f)),
        )
    }

    @Test
    fun sanitize_dropsOutOfRangeValues() {
        assertSpeeds(
            listOf(0.5f, 1.0f, 4.0f),
            sanitizePlaybackSpeedPresets(listOf(0.1f, 0.5f, 1.0f, 4.0f, 9.0f)),
        )
    }

    @Test
    fun sanitize_capsCountToMaximum() {
        val result = sanitizePlaybackSpeedPresets(
            listOf(0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f),
        )
        assertEquals(MAX_PLAYBACK_SPEED_PRESETS, result.size)
        assertSpeeds(listOf(0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f), result)
    }

    @Test
    fun sanitize_roundsToHundredthsAndDeduplicates() {
        // 1.401 and 1.404 both round to 1.40.
        assertSpeeds(listOf(1.4f), sanitizePlaybackSpeedPresets(listOf(1.401f, 1.404f)))
    }

    @Test
    fun sanitize_dropsNonFiniteValues() {
        assertSpeeds(
            listOf(1.0f),
            sanitizePlaybackSpeedPresets(listOf(Float.NaN, Float.POSITIVE_INFINITY, 1.0f)),
        )
    }

    @Test
    fun parse_blankOrNull_returnsDefaults() {
        assertSpeeds(DEFAULT_PLAYBACK_SPEED_PRESETS, parsePlaybackSpeedPresets(null))
        assertSpeeds(DEFAULT_PLAYBACK_SPEED_PRESETS, parsePlaybackSpeedPresets("   "))
    }

    @Test
    fun parse_garbageString_returnsDefaults() {
        assertSpeeds(DEFAULT_PLAYBACK_SPEED_PRESETS, parsePlaybackSpeedPresets("fast, slow, ???"))
    }

    @Test
    fun parse_allOutOfRange_returnsDefaults() {
        assertSpeeds(DEFAULT_PLAYBACK_SPEED_PRESETS, parsePlaybackSpeedPresets("0.1, 9.0"))
    }

    @Test
    fun parse_validString_sortsAndDeduplicates() {
        assertSpeeds(listOf(1.0f, 1.5f, 2.0f), parsePlaybackSpeedPresets("2.0, 1.0, 1.5, 1.0"))
    }

    @Test
    fun parse_mixedValidAndInvalidTokens_keepsValidOnly() {
        assertSpeeds(listOf(1.0f, 1.25f), parsePlaybackSpeedPresets("1.0, oops, 1.25, 99"))
    }

    @Test
    fun format_trimsTrailingZeros() {
        assertEquals(
            "1, 1.25, 1.4, 1.75, 2",
            formatPlaybackSpeedPresets(DEFAULT_PLAYBACK_SPEED_PRESETS),
        )
    }

    @Test
    fun formatAndParse_roundTrip() {
        val presets = listOf(0.75f, 1.0f, 1.85f)
        assertSpeeds(presets, parsePlaybackSpeedPresets(formatPlaybackSpeedPresets(presets)))
    }
}
