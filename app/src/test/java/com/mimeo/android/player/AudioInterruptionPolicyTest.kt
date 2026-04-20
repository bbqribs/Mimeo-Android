package com.mimeo.android.player

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioInterruptionPolicyTest {
    @Test
    fun `transient loss while playing pauses without releasing focus`() {
        val policy = AudioInterruptionPolicy()

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.PauseKeepFocus, action)
    }

    @Test
    fun `transient gain after transient loss resumes playback`() {
        val policy = AudioInterruptionPolicy()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.ResumePlayback, action)
    }

    @Test
    fun `transient gain does not resume when interruption started while not playing`() {
        val policy = AudioInterruptionPolicy()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.None, action)
    }

    @Test
    fun `permanent loss pauses and releases focus`() {
        val policy = AudioInterruptionPolicy()

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.PauseReleaseFocus, action)
    }

    @Test
    fun `noisy route change pauses and releases focus when playing`() {
        val policy = AudioInterruptionPolicy()

        val action = policy.onBecomingNoisy(isCurrentlyPlaying = true)

        assertEquals(AudioInterruptionAction.PauseReleaseFocus, action)
    }

    @Test
    fun `ducking interruption is treated as pause instead of duck`() {
        val policy = AudioInterruptionPolicy()

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.PauseReleaseFocus, action)
    }

    @Test
    fun `gain after ducking loss resumes when interruption started while playing`() {
        val policy = AudioInterruptionPolicy()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.ResumePlayback, action)
    }

    @Test
    fun `stacked transient loss while already paused preserves resume expectation`() {
        val policy = AudioInterruptionPolicy()
        // First interrupt while playing: sets resumeAfterTransientGain = true
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )
        // Second interrupt arrives while paused: must not clear the expectation
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.ResumePlayback, action)
    }

    @Test
    fun `stacked duck loss while already paused preserves resume expectation`() {
        val policy = AudioInterruptionPolicy()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.ResumePlayback, action)
    }

    @Test
    fun `explicit clear between stacked interruptions suppresses auto resume`() {
        val policy = AudioInterruptionPolicy()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = true,
            hasLoadedItem = true,
        )
        // User explicitly paused (e.g. tapped Pause button in Mimeo UI)
        policy.clearResumeExpectation()
        policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        val action = policy.onAudioFocusChange(
            focusChange = AudioManager.AUDIOFOCUS_GAIN,
            isCurrentlyPlaying = false,
            hasLoadedItem = true,
        )

        assertEquals(AudioInterruptionAction.None, action)
    }
}
