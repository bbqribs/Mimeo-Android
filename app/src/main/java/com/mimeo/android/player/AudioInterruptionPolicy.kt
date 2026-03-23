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
                resumeAfterTransientGain = isCurrentlyPlaying
                AudioInterruptionAction.PauseKeepFocus
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Navigation prompts commonly come through this path. For TTS we pause
                // and release focus so the other speaker path can run immediately.
                // Auto-resume on gain only if we were actively playing at interruption.
                resumeAfterTransientGain = isCurrentlyPlaying
                AudioInterruptionAction.PauseReleaseFocus
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
