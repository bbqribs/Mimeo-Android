package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.AccentSchemePreference
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.model.VisualDensityPreference
import com.mimeo.android.model.VisualThemePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class SettingsStoreVisualPreferencesTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    @Test
    fun appSettings_defaults_useVisualV1Defaults() {
        val defaults = AppSettings()
        assertEquals(VisualThemePreference.FOLLOW_SYSTEM, defaults.visualThemePreference)
        assertEquals(VisualDensityPreference.DEFAULT, defaults.visualDensityPreference)
        assertEquals(AccentSchemePreference.LILAC, defaults.accentSchemePreference)
    }

    @Test
    fun emptyDataStore_readsVisualV1Defaults() = runBlocking {
        val settings = store.settingsFlow.first()
        assertEquals(VisualThemePreference.FOLLOW_SYSTEM, settings.visualThemePreference)
        assertEquals(VisualDensityPreference.DEFAULT, settings.visualDensityPreference)
        assertEquals(AccentSchemePreference.LILAC, settings.accentSchemePreference)
    }

    @Test
    fun saveVisualThemePreference_roundTrips() = runBlocking {
        store.saveVisualThemePreference(VisualThemePreference.DARK)
        val settings = store.settingsFlow.first()
        assertEquals(VisualThemePreference.DARK, settings.visualThemePreference)
    }

    @Test
    fun saveVisualDensityPreference_roundTrips() = runBlocking {
        store.saveVisualDensityPreference(VisualDensityPreference.COMPACT)
        val settings = store.settingsFlow.first()
        assertEquals(VisualDensityPreference.COMPACT, settings.visualDensityPreference)
    }

    @Test
    fun saveAccentSchemePreference_roundTrips() = runBlocking {
        store.saveAccentSchemePreference(AccentSchemePreference.FOREST)
        val settings = store.settingsFlow.first()
        assertEquals(AccentSchemePreference.FOREST, settings.accentSchemePreference)
    }

    @Test
    fun invalidStoredThemeString_fallsBackToFollowSystem() {
        assertEquals(
            VisualThemePreference.FOLLOW_SYSTEM,
            store.parseVisualThemePreference("not-a-theme"),
        )
    }

    @Test
    fun invalidStoredDensityString_fallsBackToDefault() {
        assertEquals(
            VisualDensityPreference.DEFAULT,
            store.parseVisualDensityPreference("not-a-density"),
        )
    }

    @Test
    fun invalidStoredAccentSchemeString_fallsBackToLilac() {
        assertEquals(
            AccentSchemePreference.LILAC,
            store.parseAccentSchemePreference("not-an-accent"),
        )
    }

    @Test
    fun existingReaderAppearanceSettings_roundTripUnchanged() = runBlocking {
        store.saveReadingPreferences(
            readingFontSizeSp = 19,
            readingFontOption = ReaderFontOption.MONOSPACE,
            readingLineHeightPercent = 175,
            readingMaxWidthDp = 680,
            readingParagraphSpacing = ParagraphSpacingOption.LARGE,
        )

        val settings = store.settingsFlow.first()
        assertEquals(19, settings.readingFontSizeSp)
        assertEquals(ReaderFontOption.MONOSPACE, settings.readingFontOption)
        assertEquals(175, settings.readingLineHeightPercent)
        assertEquals(680, settings.readingMaxWidthDp)
        assertEquals(ParagraphSpacingOption.LARGE, settings.readingParagraphSpacing)
    }
}
