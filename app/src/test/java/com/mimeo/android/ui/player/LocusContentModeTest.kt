package com.mimeo.android.ui.player

import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.DrawerPanelSide
import com.mimeo.android.model.LocusContentMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocusContentModeTest {

    @Test
    fun appSettings_defaultLocusMode_isFullTextWithPlayer() {
        assertEquals(LocusContentMode.FULL_TEXT_WITH_PLAYER, AppSettings().locusContentMode)
    }

    @Test
    fun appSettings_defaultDrawerPanelSide_isLeft() {
        assertEquals(DrawerPanelSide.LEFT, AppSettings().drawerPanelSide)
    }

    @Test
    fun toggleMode_switchesBothWays() {
        assertEquals(
            LocusContentMode.FULL_TEXT_WITH_PLAYER,
            toggledLocusContentMode(LocusContentMode.FULL_TEXT),
        )
        assertEquals(
            LocusContentMode.PLAYBACK_FOCUSED,
            toggledLocusContentMode(LocusContentMode.FULL_TEXT_WITH_PLAYER),
        )
        assertEquals(
            LocusContentMode.FULL_TEXT,
            toggledLocusContentMode(LocusContentMode.PLAYBACK_FOCUSED),
        )
    }
}

