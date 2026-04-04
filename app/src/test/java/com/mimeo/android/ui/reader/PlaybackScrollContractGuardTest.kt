package com.mimeo.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackScrollContractGuardTest {

    @Test
    fun manualScroll_detachesAsSoonAsAnchorIsNotFullyVisible() {
        assertTrue(
            shouldDetachOnManualScroll(
                manualScrollDetached = false,
                anchorFullyVisible = false,
            ),
        )
    }

    @Test
    fun detachedFollow_ignoresStandardAndCenterTriggersUntilReattach() {
        assertTrue(
            shouldKeepDetachedAfterTrigger(
                manualScrollDetached = true,
                triggerKind = ReaderScrollTriggerKind.STANDARD,
            ),
        )
        assertTrue(
            shouldKeepDetachedAfterTrigger(
                manualScrollDetached = true,
                triggerKind = ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN,
            ),
        )
        assertFalse(
            shouldKeepDetachedAfterTrigger(
                manualScrollDetached = true,
                triggerKind = ReaderScrollTriggerKind.FORCE_REATTACH,
            ),
        )
    }

    @Test
    fun locusTap_reattachesEvenInsideCooldownWindow() {
        assertFalse(
            shouldSuppressStandardTriggerDuringCooldown(
                triggerKind = ReaderScrollTriggerKind.FORCE_REATTACH,
                nowMs = 1000L,
                suppressUntilMs = 5000L,
            ),
        )
        assertFalse(
            nextManualDetachState(
                currentDetached = true,
                triggerKind = ReaderScrollTriggerKind.FORCE_REATTACH,
            ),
        )
    }

    @Test
    fun standardFollow_requiresBottomBoundaryAndNoDetachAndCooldownElapsed() {
        assertTrue(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                hiddenByBottom = true,
                nowMs = 4000L,
                suppressUntilMs = 3000L,
            ),
        )
        assertFalse(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = true,
                hiddenByBottom = true,
                nowMs = 4000L,
                suppressUntilMs = 3000L,
            ),
        )
        assertFalse(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                hiddenByBottom = false,
                nowMs = 4000L,
                suppressUntilMs = 3000L,
            ),
        )
        assertFalse(
            shouldAutoScrollForStandardPlayback(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                hiddenByBottom = true,
                nowMs = 2000L,
                suppressUntilMs = 3000L,
            ),
        )
    }

    @Test
    fun boundaryFollow_requiresAnchorChangeAndBottomBoundary() {
        assertTrue(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                anchorChanged = true,
                hiddenByBottom = true,
                nowMs = 6000L,
                suppressUntilMs = 3000L,
            ),
        )
        assertFalse(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                anchorChanged = false,
                hiddenByBottom = true,
                nowMs = 6000L,
                suppressUntilMs = 3000L,
            ),
        )
        assertFalse(
            shouldAutoScrollForPlaybackBoundary(
                autoScrollWhileListening = true,
                manualScrollDetached = false,
                anchorChanged = true,
                hiddenByBottom = false,
                nowMs = 6000L,
                suppressUntilMs = 3000L,
            ),
        )
    }

    @Test
    fun ffRwRecentering_onlyWhenOffscreen() {
        assertTrue(
            shouldCenterForTrigger(
                triggerKind = ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN,
                anchorOffscreen = true,
            ),
        )
        assertFalse(
            shouldCenterForTrigger(
                triggerKind = ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN,
                anchorOffscreen = false,
            ),
        )
    }

    @Test
    fun manualDetach_autoReattachesWhenAnchorBecomesVisibleAgain() {
        assertTrue(
            shouldAutoReattachAfterManualScroll(
                manualScrollDetached = true,
                anchorFullyVisible = true,
                triggerKind = ReaderScrollTriggerKind.NONE,
            ),
        )
    }

    @Test
    fun ffRwCentering_winsOverBoundaryTopFollow_whenOffscreen() {
        assertTrue(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = false,
                boundaryFollowTrigger = true,
                forceReattach = false,
            ),
        )
        assertTrue(
            shouldUseCenteredJumpAnchor(
                centerIfOffscreenTrigger = true,
                standardFollowTrigger = true,
                boundaryFollowTrigger = false,
                forceReattach = false,
            ),
        )
    }
}

