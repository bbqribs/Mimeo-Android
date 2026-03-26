package com.mimeo.android.player

internal data class PlaybackAuditState(
    val itemId: Int?,
    val isPlaying: Boolean,
    val hasAudioFocus: Boolean,
    val mediaSessionActive: Boolean,
    val isForeground: Boolean,
    val anchorPlaying: Boolean,
    val isDeviceInteractive: Boolean,
    val isDeviceLocked: Boolean,
    val appInBackground: Boolean,
)

internal data class PlaybackAuditEntry(
    val reason: String,
    val state: PlaybackAuditState,
    val clues: List<String>,
)

internal fun detectPlaybackDriftClues(state: PlaybackAuditState): List<String> {
    val clues = mutableListOf<String>()
    if (state.itemId == null && state.mediaSessionActive) clues += "session-active-without-item"
    if (state.itemId == null && state.hasAudioFocus) clues += "focus-without-item"
    if (state.itemId == null && state.isForeground) clues += "foreground-without-item"
    if (state.itemId != null && !state.hasAudioFocus) clues += "loaded-item-without-focus"
    if (state.itemId != null && state.isPlaying && !state.mediaSessionActive) clues += "playing-with-inactive-session"
    if (state.itemId != null && state.isPlaying && !state.isForeground) clues += "playing-without-foreground-service"
    if (state.anchorPlaying && !state.hasAudioFocus) clues += "anchor-playing-without-focus"
    if (state.anchorPlaying && state.itemId == null) clues += "anchor-playing-without-item"
    return clues
}

internal fun shouldEmitAuditHeartbeat(
    nowMs: Long,
    lastHeartbeatMs: Long?,
    intervalMs: Long,
): Boolean {
    if (lastHeartbeatMs == null) return true
    return nowMs - lastHeartbeatMs >= intervalMs
}

internal class PlaybackServiceAuditTrail(
    private val heartbeatIntervalMs: Long = 180_000L,
) {
    private var lastState: PlaybackAuditState? = null
    private var lastHeartbeatMs: Long? = null

    fun capture(
        event: String,
        state: PlaybackAuditState,
        nowMs: Long,
    ): PlaybackAuditEntry? {
        val previous = lastState
        val transitionReason = buildTransitionReason(previous, state)
        val clues = detectPlaybackDriftClues(state)
        val shouldHeartbeat = state.itemId != null && shouldEmitAuditHeartbeat(nowMs, lastHeartbeatMs, heartbeatIntervalMs)
        val reason = when {
            transitionReason != null -> "$event:$transitionReason"
            clues.isNotEmpty() -> "$event:drift-clue"
            shouldHeartbeat -> "$event:heartbeat"
            else -> null
        }
        lastState = state
        if (reason == null) return null
        if (state.itemId != null) {
            lastHeartbeatMs = nowMs
        }
        return PlaybackAuditEntry(reason = reason, state = state, clues = clues)
    }

    private fun buildTransitionReason(previous: PlaybackAuditState?, current: PlaybackAuditState): String? {
        if (previous == null) return "initial"
        val changes = mutableListOf<String>()
        if (previous.itemId != current.itemId) changes += "item:${previous.itemId ?: "none"}->${current.itemId ?: "none"}"
        if (previous.isPlaying != current.isPlaying) changes += "playing:${previous.isPlaying}->${current.isPlaying}"
        if (previous.hasAudioFocus != current.hasAudioFocus) changes += "focus:${previous.hasAudioFocus}->${current.hasAudioFocus}"
        if (previous.mediaSessionActive != current.mediaSessionActive) {
            changes += "sessionActive:${previous.mediaSessionActive}->${current.mediaSessionActive}"
        }
        if (previous.isForeground != current.isForeground) changes += "foreground:${previous.isForeground}->${current.isForeground}"
        if (previous.anchorPlaying != current.anchorPlaying) changes += "anchor:${previous.anchorPlaying}->${current.anchorPlaying}"
        if (previous.isDeviceInteractive != current.isDeviceInteractive) {
            changes += "interactive:${previous.isDeviceInteractive}->${current.isDeviceInteractive}"
        }
        if (previous.isDeviceLocked != current.isDeviceLocked) {
            changes += "locked:${previous.isDeviceLocked}->${current.isDeviceLocked}"
        }
        if (previous.appInBackground != current.appInBackground) {
            changes += "background:${previous.appInBackground}->${current.appInBackground}"
        }
        return if (changes.isEmpty()) null else changes.joinToString(",")
    }
}

internal fun formatPlaybackAuditEntry(entry: PlaybackAuditEntry): String {
    val state = entry.state
    val cluePart = if (entry.clues.isEmpty()) "" else " clues=${entry.clues.joinToString("|")}"
    return "audit=${entry.reason} item=${state.itemId ?: "none"} playing=${state.isPlaying} " +
        "focus=${state.hasAudioFocus} sessionActive=${state.mediaSessionActive} " +
        "foreground=${state.isForeground} anchor=${state.anchorPlaying} " +
        "interactive=${state.isDeviceInteractive} locked=${state.isDeviceLocked} " +
        "background=${state.appInBackground}$cluePart"
}

internal enum class MediaButtonDispatchAction {
    Play,
    Pause,
    Toggle,
    None,
}

internal fun resolveMediaButtonDispatchAction(
    keyCode: Int,
    isCurrentlyPlaying: Boolean,
): MediaButtonDispatchAction {
    return when (keyCode) {
        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            if (isCurrentlyPlaying) MediaButtonDispatchAction.Pause else MediaButtonDispatchAction.Play
        }
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
            if (isCurrentlyPlaying) MediaButtonDispatchAction.Pause else MediaButtonDispatchAction.Play
        }
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        android.view.KeyEvent.KEYCODE_HEADSETHOOK,
        -> MediaButtonDispatchAction.Toggle
        else -> MediaButtonDispatchAction.None
    }
}
