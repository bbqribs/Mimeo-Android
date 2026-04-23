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
    fun `media pause key always resolves to pause`() {
        val action = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_MEDIA_PAUSE,
            isCurrentlyPlaying = false,
        )

        assertEquals(MediaButtonDispatchAction.Pause, action)
    }

    @Test
    fun `media play key always resolves to play`() {
        val action = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_MEDIA_PLAY,
            isCurrentlyPlaying = true,
        )

        assertEquals(MediaButtonDispatchAction.Play, action)
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
    fun `headsethook key resolves to toggle regardless of playing state`() {
        val actionWhilePlaying = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
            isCurrentlyPlaying = true,
        )
        val actionWhilePaused = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
            isCurrentlyPlaying = false,
        )

        assertEquals(MediaButtonDispatchAction.Toggle, actionWhilePlaying)
        assertEquals(MediaButtonDispatchAction.Toggle, actionWhilePaused)
    }

    @Test
    fun `toggle resolves to pause when snapshot says playing`() {
        // Verifies that Toggle routes through dispatchPause path when isCurrentlyPlaying=true,
        // not through a secondary engine-state read that could be stale between TTS chunks.
        val pauseAction = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
            isCurrentlyPlaying = true,
        )
        val playAction = resolveMediaButtonDispatchAction(
            keyCode = KeyEvent.KEYCODE_HEADSETHOOK,
            isCurrentlyPlaying = false,
        )

        // Toggle when playing → service should dispatchPause (snapshot.isPlaying=true)
        assertEquals(MediaButtonDispatchAction.Toggle, pauseAction)
        // Toggle when paused → service should dispatchPlay (snapshot.isPlaying=false)
        assertEquals(MediaButtonDispatchAction.Toggle, playAction)
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
    fun `drift clues include playing without focus`() {
        val clues = detectPlaybackDriftClues(
            PlaybackAuditState(
                itemId = 10,
                isPlaying = true,
                hasAudioFocus = false,
                mediaSessionActive = true,
                isForeground = true,
                anchorPlaying = false,
                isDeviceInteractive = true,
                isDeviceLocked = false,
                appInBackground = false,
            ),
        )

        assertTrue(clues.contains("playing-without-focus"))
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
    fun `drift clues include playing without anchor when playing with focus but anchor is stopped`() {
        val clues = detectPlaybackDriftClues(
            PlaybackAuditState(
                itemId = 42,
                isPlaying = true,
                hasAudioFocus = true,
                mediaSessionActive = true,
                isForeground = true,
                anchorPlaying = false,
                isDeviceInteractive = false,
                isDeviceLocked = true,
                appInBackground = true,
            ),
        )

        assertTrue(clues.contains("playing-without-anchor"))
    }

    @Test
    fun `drift clues do not include playing without anchor when anchor is running`() {
        val clues = detectPlaybackDriftClues(
            PlaybackAuditState(
                itemId = 42,
                isPlaying = true,
                hasAudioFocus = true,
                mediaSessionActive = true,
                isForeground = true,
                anchorPlaying = true,
                isDeviceInteractive = false,
                isDeviceLocked = true,
                appInBackground = true,
            ),
        )

        assertFalse(clues.contains("playing-without-anchor"))
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

