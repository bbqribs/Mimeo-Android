package com.mimeo.android.ui.player

import com.mimeo.android.model.PlayerControlsMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerChevronModeCycleTest {

    @Test
    fun longPress_fromFull_advancesToMinimal() {
        assertEquals(
            PlayerControlsMode.MINIMAL,
            nextPlayerControlsModeOnLongPress(PlayerControlsMode.FULL),
        )
    }

    @Test
    fun longPress_fromMinimal_advancesToNub() {
        assertEquals(
            PlayerControlsMode.NUB,
            nextPlayerControlsModeOnLongPress(PlayerControlsMode.MINIMAL),
        )
    }

    @Test
    fun longPress_fromNub_advancesToFull() {
        assertEquals(
            PlayerControlsMode.FULL,
            nextPlayerControlsModeOnLongPress(PlayerControlsMode.NUB),
        )
    }
}
