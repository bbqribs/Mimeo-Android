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

    @Test
    fun manualScroll_doesNotDetachWhenAnchorStillVisible() {
        assertFalse(
            shouldDetachOnManualScroll(
                manualScrollDetached = false,
                anchorFullyVisible = true,
            ),
        )
    }

    @Test
    fun manualScroll_detachesWhenAnchorNotFullyVisible() {
        assertTrue(
            shouldDetachOnManualScroll(
                manualScrollDetached = false,
                anchorFullyVisible = false,
            ),
        )
    }

    @Test
    fun manualScrollDetached_autoReattachesWhenAnchorVisibleAgain() {
        assertFalse(
            shouldAutoReattachAfterManualScroll(
                manualScrollDetached = true,
                anchorFullyVisible = true,
                triggerKind = ReaderScrollTriggerKind.NONE,
            ),
        )
    }

    @Test
    fun manualScrollDetached_autoReattachesOnlyForForceReattachTrigger() {
        assertTrue(
            shouldAutoReattachAfterManualScroll(
                manualScrollDetached = true,
                anchorFullyVisible = true,
                triggerKind = ReaderScrollTriggerKind.FORCE_REATTACH,
            ),
        )
    }

    @Test
    fun manualScrollDetached_doesNotAutoReattachWhenAnchorStillOffscreen() {
        assertFalse(
            shouldAutoReattachAfterManualScroll(
                manualScrollDetached = true,
                anchorFullyVisible = false,
                triggerKind = ReaderScrollTriggerKind.NONE,
            ),
        )
    }

    @Test
    fun standardPlayback_autoscrollsWhenHiddenByBottomAndNotDetached() {
        assertTrue(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                hiddenByBottom = true,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun standardPlayback_doesNotAutoscrollWhenNotPastBottom() {
        assertFalse(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                hiddenByBottom = false,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun standardPlayback_doesNotAutoscrollWhenDetached() {
        assertFalse(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = true,
                hiddenByBottom = true,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun boundaryFollow_autoscrollsOnAnchorChangeWhenPastBottom() {
        assertTrue(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                anchorChanged = true,
                hiddenByBottom = true,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun boundaryFollow_doesNotAutoscrollWhenDetached() {
        assertFalse(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = true,
                anchorChanged = true,
                hiddenByBottom = true,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun boundaryFollow_doesNotAutoscrollWithoutAnchorChange() {
        assertFalse(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                anchorChanged = false,
                hiddenByBottom = true,
                nowMs = 5_000L,
                suppressUntilMs = 4_000L,
            ),
        )
    }

    @Test
    fun centeredAnchor_usedOnlyForPureCenterTrigger() {
        assertTrue(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = false,
                boundaryFollowTrigger = false,
                forceReattach = false,
            ),
        )
    }

    @Test
    fun centeredAnchor_stillUsedWhenStandardOrBoundaryAlsoActive_butNotForceReattach() {
        assertTrue(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = true,
                boundaryFollowTrigger = false,
                forceReattach = false,
            ),
        )
        assertTrue(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = false,
                boundaryFollowTrigger = true,
                forceReattach = false,
            ),
        )
        assertFalse(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = false,
                boundaryFollowTrigger = false,
                forceReattach = true,
            ),
        )
    }
}

