package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScreenOnPolicyTest {
    @Test
    fun `enabled keeps screen on for playback-active session`() {
        assertTrue(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = 123,
                playbackActive = true,
                manualReadingActive = false,
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
                playbackActive = true,
                manualReadingActive = true,
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
                playbackActive = true,
                manualReadingActive = true,
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
                playbackActive = true,
                manualReadingActive = true,
            ),
        )
    }

    @Test
    fun `enabled keeps screen on for manual-reading-active session`() {
        assertTrue(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = 321,
                playbackActive = false,
                manualReadingActive = true,
            ),
        )
    }

    @Test
    fun `reader-only inactive session does not keep screen on`() {
        assertFalse(
            shouldKeepScreenOnForSession(
                keepScreenOnEnabled = true,
                requiresSignIn = false,
                isOnLocusRoute = true,
                requestedPlayerItemId = 321,
                playbackActive = false,
                manualReadingActive = false,
            ),
        )
    }
}
