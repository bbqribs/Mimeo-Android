package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScreenOnPolicyTest {
    @Test
    fun `enabled keeps screen on when signed in on locus with active item`() {
        assertTrue(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = 123,
            ),
        )
    }

    @Test
    fun `disabled setting never keeps screen on`() {
        assertFalse(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = false,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = 123,
            ),
        )
    }

    @Test
    fun `off locus route clears keep screen on even with active item`() {
        assertFalse(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = false,
                requestedPlayerItemId = 123,
            ),
        )
    }

    @Test
    fun `no active item does not keep screen on`() {
        assertFalse(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = null,
            ),
        )
    }
}
