package com.mimeo.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.view.KeyEvent

class PlaybackServiceObservabilityTest {
    @Test
    fun `media pause key falls back to play when currently paused`() {
        val action = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
            isCurrentlyPlaying = false,
        )

        assertEquals(MediaButtonDispatchAction.Play, action)
    }

    @Test
    fun `media play key falls back to pause when currently playing`() {
        val action = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
            isCurrentlyPlaying = true,
        )

        assertEquals(MediaButtonDispatchAction.Pause, action)
    }

    @Test
    fun `media play pause key resolves to toggle`() {
        val action = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            isCurrentlyPlaying = true,
        )

        assertEquals(MediaButtonDispatchAction.Toggle, action)
    }

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
                isDeviceInteractive = true,
                isDeviceLocked = false,
                appInBackground = false,
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
            isDeviceInteractive = true,
            isDeviceLocked = false,
            appInBackground = false,
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
            isDeviceInteractive = false,
            isDeviceLocked = true,
            appInBackground = true,
        )

        val first = trail.capture("snapshot", state, nowMs = 1_000)
        val second = trail.capture("snapshot", state, nowMs = 1_200)
        val third = trail.capture("snapshot", state, nowMs = 1_700)

        assertNotNull(first)
        assertNull(second)
        assertNotNull(third)
        assertEquals("snapshot:heartbeat", third!!.reason)
    }

    @Test
    fun `capture emits transition when device state changes`() {
        val trail = PlaybackServiceAuditTrail(heartbeatIntervalMs = 60_000)
        val start = PlaybackAuditState(
            itemId = 33,
            isPlaying = true,
            hasAudioFocus = true,
            mediaSessionActive = true,
            isForeground = true,
            anchorPlaying = true,
            isDeviceInteractive = true,
            isDeviceLocked = false,
            appInBackground = false,
        )
        val screenOff = start.copy(
            isDeviceInteractive = false,
            isDeviceLocked = true,
            appInBackground = true,
        )

        trail.capture("snapshot", start, nowMs = 5_000)
        val entry = trail.capture("snapshot", screenOff, nowMs = 5_500)

        assertNotNull(entry)
        assertTrue(entry!!.reason.contains("interactive:true->false"))
        assertTrue(entry.reason.contains("locked:false->true"))
        assertTrue(entry.reason.contains("background:false->true"))
    }
}

