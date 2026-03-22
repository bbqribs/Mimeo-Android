package com.mimeo.android.player

data class PlaybackServiceSnapshot(
    val itemId: Int? = null,
    val title: String = "Mimeo",
    val isPlaying: Boolean = false,
)

/**
 * Process-local bridge between UI/ViewModel playback commands and PlaybackService
 * media controls. This keeps service wiring bounded while PlaybackEngine remains
 * the single runtime playback owner.
 */
object PlaybackServiceBridge {
    @Volatile
    var onPlay: (() -> Unit)? = null

    @Volatile
    var onPause: (() -> Unit)? = null

    @Volatile
    var onTogglePlayPause: (() -> Unit)? = null

    @Volatile
    var snapshotProvider: (() -> PlaybackServiceSnapshot)? = null

    fun clear() {
        onPlay = null
        onPause = null
        onTogglePlayPause = null
        snapshotProvider = null
    }
}

