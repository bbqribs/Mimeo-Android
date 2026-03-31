package com.mimeo.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollPolicyTest {

    @Test
    fun manualScroll_detachesAndStaysDetached_forStandardPlaybackProgressTrigger() {
        val triggerKind = classifyReaderScrollTrigger(
            scrollTriggerSignal = 2,
            lastHandledScrollTrigger = 0,
        )

        assertEquals(ReaderScrollTriggerKind.STANDARD, triggerKind)
        assertTrue(shouldKeepDetachedAfterTrigger(manualScrollDetached = true, triggerKind = triggerKind))
        assertTrue(nextManualDetachState(currentDetached = true, triggerKind = triggerKind))
    }

    @Test
    fun locusTapTrigger_reattachesReaderFollow() {
        val triggerKind = classifyReaderScrollTrigger(
            scrollTriggerSignal = 3,
            lastHandledScrollTrigger = 2,
        )

        assertEquals(ReaderScrollTriggerKind.FORCE_REATTACH, triggerKind)
        assertFalse(shouldKeepDetachedAfterTrigger(manualScrollDetached = true, triggerKind = triggerKind))
        assertFalse(nextManualDetachState(currentDetached = true, triggerKind = triggerKind))
    }

    @Test
    fun ffRwTrigger_centersOnlyWhenAnchorWouldBeOffscreen() {
        val triggerKind = classifyReaderScrollTrigger(
            scrollTriggerSignal = -5,
            lastHandledScrollTrigger = 4,
        )

        assertEquals(ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN, triggerKind)
        assertTrue(shouldCenterForTrigger(triggerKind, anchorOffscreen = true))
        assertFalse(shouldCenterForTrigger(triggerKind, anchorOffscreen = false))
    }

    @Test
    fun unchangedSignal_hasNoTriggerSideEffects() {
        val triggerKind = classifyReaderScrollTrigger(
            scrollTriggerSignal = 8,
            lastHandledScrollTrigger = 8,
        )

        assertEquals(ReaderScrollTriggerKind.NONE, triggerKind)
        assertFalse(shouldCenterForTrigger(triggerKind, anchorOffscreen = true))
        assertTrue(nextManualDetachState(currentDetached = true, triggerKind = triggerKind))
    }
}

