package com.mimeo.android.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingOutcomeSimulationPresentationTest {

    @Test
    fun `simulated pending outcome messages are stable`() {
        assertEquals(
            "Saved and downloaded for offline reading.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.CACHED),
        )
        assertEquals(
            "Saved, but not available offline for this item.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.NO_ACTIVE_CONTENT),
        )
        assertEquals(
            "Saved, but processing failed for offline cache.",
            pendingOutcomeSimulationMessage(PendingOutcomeSimulation.FAILED_PROCESSING),
        )
    }
}
