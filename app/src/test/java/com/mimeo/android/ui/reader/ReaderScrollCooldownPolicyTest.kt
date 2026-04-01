package com.mimeo.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollCooldownPolicyTest {

    @Test
    fun suppressesStandardTriggerWithinCooldownWindow() {
        assertTrue(
            shouldSuppressStandardTriggerDuringCooldown(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                nowMs = 1_000L,
                suppressUntilMs = 2_000L,
            ),
        )
    }

    @Test
    fun doesNotSuppressForceReattachWithinCooldownWindow() {
        assertFalse(
            shouldSuppressStandardTriggerDuringCooldown(
                triggerKind = ReaderScrollTriggerKind.FORCE_REATTACH,
                nowMs = 1_000L,
                suppressUntilMs = 2_000L,
            ),
        )
    }

    @Test
    fun doesNotSuppressStandardTriggerAfterCooldownWindow() {
        assertFalse(
            shouldSuppressStandardTriggerDuringCooldown(
                triggerKind = ReaderScrollTriggerKind.STANDARD,
                nowMs = 2_000L,
                suppressUntilMs = 2_000L,
            ),
        )
    }
}
