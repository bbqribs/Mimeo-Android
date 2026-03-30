package com.mimeo.android.ui.player

import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.LocusContentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocusContentModeTest {

    @Test
    fun appSettings_defaultLocusMode_isFullText() {
        assertEquals(LocusContentMode.FULL_TEXT, AppSettings().locusContentMode)
    }

    @Test
    fun toggleMode_switchesBothWays() {
        assertEquals(
            LocusContentMode.PLAYBACK_FOCUSED,
            toggledLocusContentMode(LocusContentMode.FULL_TEXT),
        )
        assertEquals(
            LocusContentMode.FULL_TEXT,
            toggledLocusContentMode(LocusContentMode.PLAYBACK_FOCUSED),
        )
    }
}

