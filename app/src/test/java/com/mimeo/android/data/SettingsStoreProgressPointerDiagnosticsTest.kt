package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreProgressPointerDiagnosticsTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking {
            store.clearAllSettingsForTesting()
            store.saveTokenOnly("")
        }
    }

    @Test
    fun defaultsToFalse() = runBlocking {
        val settings = store.settingsFlow.first()
        assertFalse(settings.showProgressPointerDiagnostics)
    }

    @Test
    fun dedicatedSetterPersistsIndependentlyOfPlaybackDiagnostics() = runBlocking {
        store.saveShowPlaybackDiagnostics(true)
        store.saveShowProgressPointerDiagnostics(true)

        val settings = store.settingsFlow.first()
        assertEquals(true, settings.showPlaybackDiagnostics)
        assertEquals(true, settings.showProgressPointerDiagnostics)

        store.saveShowPlaybackDiagnostics(false)
        val afterPlaybackOff = store.settingsFlow.first()
        assertEquals(false, afterPlaybackOff.showPlaybackDiagnostics)
        assertEquals(true, afterPlaybackOff.showProgressPointerDiagnostics)

        store.saveShowProgressPointerDiagnostics(false)
        val afterBothOff = store.settingsFlow.first()
        assertEquals(false, afterBothOff.showPlaybackDiagnostics)
        assertEquals(false, afterBothOff.showProgressPointerDiagnostics)
    }

    @Test
    fun togglingPlaybackDiagnosticsDoesNotEnableProgressPointerDiagnostics() = runBlocking {
        store.saveShowPlaybackDiagnostics(true)
        val settings = store.settingsFlow.first()
        assertEquals(true, settings.showPlaybackDiagnostics)
        assertFalse(settings.showProgressPointerDiagnostics)
    }
}
