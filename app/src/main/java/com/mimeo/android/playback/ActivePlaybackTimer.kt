package com.mimeo.android.playback

import android.os.SystemClock

/**
 * Process-local, monotonic accounting for time spent actively playing one queue item.
 * Paused time is deliberately not accumulated and this state is never persisted.
 */
internal class ActivePlaybackTimer(
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private var trackedItemId: Int? = null
    private var activeStartedAtMs: Long? = null
    private var elapsedForActiveItemMs: Long = 0L

    fun update(itemId: Int?, isPlaying: Boolean) {
        val nextItemId = itemId
        val now = nowMs()
        if (nextItemId != trackedItemId) {
            stopAt(now)
            trackedItemId = nextItemId
            elapsedForActiveItemMs = 0L
        }
        if (isPlaying && nextItemId != null && activeStartedAtMs == null) {
            activeStartedAtMs = now
        } else if (!isPlaying || nextItemId == null) {
            stopAt(now)
        }
    }

    fun elapsedFor(itemId: Int): Long {
        if (itemId != trackedItemId) return 0L
        val liveElapsed = activeStartedAtMs
            ?.let { startedAtMs -> (nowMs() - startedAtMs).coerceAtLeast(0L) }
            ?: 0L
        return elapsedForActiveItemMs + liveElapsed
    }

    /** Starts a fresh threshold window whenever the session's active item changes. */
    fun resetForActiveItem(itemId: Int?) {
        trackedItemId = itemId
        activeStartedAtMs = null
        elapsedForActiveItemMs = 0L
    }

    fun clear() = resetForActiveItem(itemId = null)

    private fun stopAt(now: Long) {
        val startedAtMs = activeStartedAtMs
        if (trackedItemId != null && startedAtMs != null) {
            elapsedForActiveItemMs += (now - startedAtMs).coerceAtLeast(0L)
        }
        activeStartedAtMs = null
    }
}
