package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.DEFAULT_PLAYBACK_SPEED_PRESETS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class SettingsStorePlaybackPresetsTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    /** Compare speed lists by hundredths so assertions never depend on float bit equality. */
    private fun hundredths(values: List<Float>): List<Int> = values.map { (it * 100f).roundToInt() }

    @Test
    fun appSettingsDefault_matchesHardCodedPresetList() {
        assertEquals(listOf(100, 125, 140, 175, 200), hundredths(AppSettings().playbackSpeedPresets))
    }

    @Test
    fun emptyDataStore_readsDefaultPresets() = runBlocking {
        val settings = store.settingsFlow.first()
        assertEquals(
            hundredths(DEFAULT_PLAYBACK_SPEED_PRESETS),
            hundredths(settings.playbackSpeedPresets),
        )
    }

    @Test
    fun savePresets_roundTripsThroughDataStore() = runBlocking {
        store.savePlaybackSpeedPresets(listOf(1.5f, 1.0f, 2.5f))
        val settings = store.settingsFlow.first()
        assertEquals(listOf(100, 150, 250), hundredths(settings.playbackSpeedPresets))
    }

    @Test
    fun savePresets_sanitizesOutOfRangeDuplicateAndUnsortedInput() = runBlocking {
        store.savePlaybackSpeedPresets(listOf(2.0f, 9.0f, 1.0f, 0.1f, 1.0f))
        val settings = store.settingsFlow.first()
        // 9.0 and 0.1 are out of range; the duplicate 1.0 is collapsed; result is sorted.
        assertEquals(listOf(100, 200), hundredths(settings.playbackSpeedPresets))
    }

    @Test
    fun savePresets_emptyInput_fallsBackToDefaults() = runBlocking {
        store.savePlaybackSpeedPresets(emptyList())
        val settings = store.settingsFlow.first()
        assertEquals(
            hundredths(DEFAULT_PLAYBACK_SPEED_PRESETS),
            hundredths(settings.playbackSpeedPresets),
        )
    }

    @Test
    fun savePresets_allInvalidInput_fallsBackToDefaults() = runBlocking {
        store.savePlaybackSpeedPresets(listOf(99.0f, 0.01f, Float.NaN))
        val settings = store.settingsFlow.first()
        assertEquals(
            hundredths(DEFAULT_PLAYBACK_SPEED_PRESETS),
            hundredths(settings.playbackSpeedPresets),
        )
    }
}
