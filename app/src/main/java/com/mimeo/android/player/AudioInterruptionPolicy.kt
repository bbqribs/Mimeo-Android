package com.mimeo.android.player

import android.media.AudioManager

internal enum class AudioInterruptionAction {
    None,
    PauseReleaseFocus,
    PauseKeepFocus,
    ResumePlayback,
}

internal class AudioInterruptionPolicy {
    private var resumeAfterTransientGain: Boolean = false

    fun onAudioFocusChange(
        focusChange: Int,
        isCurrentlyPlaying: Boolean,
        hasLoadedItem: Boolean,
    ): AudioInterruptionAction {
        return when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeAfterTransientGain = false
                AudioInterruptionAction.PauseReleaseFocus
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // For TTS we do not duck; we pause and only auto-resume if interruption
                // is transient and we were actively playing.
                // Preserve an existing resume expectation across stacked interruptions:
                // a second interrupt while already paused (isCurrentlyPlaying=false) must
                // not clear a resume expectation set by the first interrupt.
                if (!resumeAfterTransientGain) {
                    resumeAfterTransientGain = isCurrentlyPlaying
                }
                AudioInterruptionAction.PauseKeepFocus
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Navigation prompts commonly come through this path. We choose to pause
                // rather than duck the TTS volume. We KEEP focus (same as LOSS_TRANSIENT)
                // so the OS sends AUDIOFOCUS_GAIN when the prompt ends and auto-resume fires.
                // Releasing focus here would prevent the GAIN callback and break auto-resume.
                if (!resumeAfterTransientGain) {
                    resumeAfterTransientGain = isCurrentlyPlaying
                }
                AudioInterruptionAction.PauseKeepFocus
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                val shouldResume = resumeAfterTransientGain && hasLoadedItem && !isCurrentlyPlaying
                resumeAfterTransientGain = false
                if (shouldResume) AudioInterruptionAction.ResumePlayback else AudioInterruptionAction.None
            }
            else -> AudioInterruptionAction.None
        }
    }

    fun onBecomingNoisy(isCurrentlyPlaying: Boolean): AudioInterruptionAction {
        resumeAfterTransientGain = false
        return if (isCurrentlyPlaying) AudioInterruptionAction.PauseReleaseFocus else AudioInterruptionAction.None
    }

    fun clearResumeExpectation() {
        resumeAfterTransientGain = false
    }
}
