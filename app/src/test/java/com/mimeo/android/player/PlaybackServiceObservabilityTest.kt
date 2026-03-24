package com.mimeo.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceObservabilityTest {
    @Test
    fun `drift clues include focus without item`() {
        val clues = detectPlaybackDriftClues(
            PlaybackAuditState(
                itemId = null,
                isPlaying = false,
                hasAudioFocus = true,
                mediaSessionActive = true,
                isForeground = false,
                anchorPlaying = false,
            ),
        )

        assertTrue(clues.contains("focus-without-item"))
        assertTrue(clues.contains("session-active-without-item"))
    }

    @Test
    fun `heartbeat emits only after interval`() {
        assertTrue(shouldEmitAuditHeartbeat(nowMs = 10_000, lastHeartbeatMs = null, intervalMs = 1_000))
        assertFalse(shouldEmitAuditHeartbeat(nowMs = 10_200, lastHeartbeatMs = 10_000, intervalMs = 1_000))
        assertTrue(shouldEmitAuditHeartbeat(nowMs = 11_100, lastHeartbeatMs = 10_000, intervalMs = 1_000))
    }

    @Test
    fun `capture emits transition entry on state change`() {
        val trail = PlaybackServiceAuditTrail(heartbeatIntervalMs = 1_000_000)
        val initial = PlaybackAuditState(
            itemId = 10,
            isPlaying = false,
            hasAudioFocus = false,
            mediaSessionActive = true,
            isForeground = true,
            anchorPlaying = false,
        )
        val changed = initial.copy(isPlaying = true, hasAudioFocus = true)

        val first = trail.capture("snapshot", initial, nowMs = 1_000)
        val second = trail.capture("snapshot", changed, nowMs = 1_500)

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(second!!.reason.contains("playing:false->true"))
        assertTrue(second.reason.contains("focus:false->true"))
    }

    @Test
    fun `capture emits heartbeat for active item without transition`() {
        val trail = PlaybackServiceAuditTrail(heartbeatIntervalMs = 500)
        val state = PlaybackAuditState(
            itemId = 21,
            isPlaying = true,
            hasAudioFocus = true,
            mediaSessionActive = true,
            isForeground = true,
            anchorPlaying = true,
        )

        val first = trail.capture("snapshot", state, nowMs = 1_000)
        val second = trail.capture("snapshot", state, nowMs = 1_200)
        val third = trail.capture("snapshot", state, nowMs = 1_700)

        assertNotNull(first)
        assertNull(second)
        assertNotNull(third)
        assertEquals("snapshot:heartbeat", third!!.reason)
    }
}

