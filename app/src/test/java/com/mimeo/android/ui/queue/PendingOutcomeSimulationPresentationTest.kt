package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingOutcomeSimulationPresentationTest {

    @Test
    fun `simulated pending outcome messages are stable`() {
        assertEquals(
            "Saved. Available offline.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.CACHED),
        )
        assertEquals(
            "Saved, but unavailable offline for this item.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.NO_ACTIVE_CONTENT),
        )
        assertEquals(
            "Saved, but offline processing failed.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.FAILED_PROCESSING),
        )
    }

    @Test
    fun `simulated pending outcome presentation maps icons and severity`() {
        val cached = pendingOutcomeSimulationPresentation(PendingOutcomeSimulation.CACHED)
        assertEquals("Offline ready", cached.title)
        assertEquals(com.mimeo.android.R.drawable.ic_book_closed_24, cached.iconRes)
        assertFalse(cached.isError)

        val unavailable = pendingOutcomeSimulationPresentation(PendingOutcomeSimulation.NO_ACTIVE_CONTENT)
        assertEquals("Unavailable offline", unavailable.title)
        assertEquals(com.mimeo.android.R.drawable.msr_error_circle_24, unavailable.iconRes)
        assertTrue(unavailable.isError)

        val failed = pendingOutcomeSimulationPresentation(PendingOutcomeSimulation.FAILED_PROCESSING)
        assertEquals("Offline processing failed", failed.title)
        assertEquals(com.mimeo.android.R.drawable.msr_error_circle_24, failed.iconRes)
        assertTrue(failed.isError)
    }
}
