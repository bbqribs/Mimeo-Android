package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackPosition

data class PlaybackDoneEvent(
    val utteranceId: String,
    val itemId: Int,
    val chunkIndex: Int,
)

data class DoneTransitionResult(
    val shouldHandle: Boolean,
    val nextPosition: PlaybackPosition,
    val shouldPlayNextChunk: Boolean,
    val reachedEnd: Boolean,
    val handledUtteranceId: String?,
)

fun shouldForceNearEndCommit(
    previousPercent: Int,
    currentPercent: Int,
    thresholdPercent: Int = 98,
): Boolean {
    if (thresholdPercent <= 0) return true
    if (currentPercent < thresholdPercent) return false
    return previousPercent < thresholdPercent
}

fun applyDoneTransition(
    event: PlaybackDoneEvent?,
    currentItemId: Int,
    currentPosition: PlaybackPosition,
    chunkCount: Int,
    lastHandledUtteranceId: String?,
): DoneTransitionResult {
    if (event == null || chunkCount <= 0) {
        return DoneTransitionResult(
            shouldHandle = false,
            nextPosition = currentPosition,
            shouldPlayNextChunk = false,
            reachedEnd = false,
            handledUtteranceId = lastHandledUtteranceId,
        )
    }
    if (event.utteranceId == lastHandledUtteranceId) {
        return DoneTransitionResult(
            shouldHandle = false,
            nextPosition = currentPosition,
            shouldPlayNextChunk = false,
            reachedEnd = false,
            handledUtteranceId = lastHandledUtteranceId,
        )
    }
    if (event.itemId != currentItemId || event.chunkIndex != currentPosition.chunkIndex) {
        return DoneTransitionResult(
            shouldHandle = false,
            nextPosition = currentPosition,
            shouldPlayNextChunk = false,
            reachedEnd = false,
            handledUtteranceId = lastHandledUtteranceId,
        )
    }

    if (currentPosition.chunkIndex < (chunkCount - 1)) {
        return DoneTransitionResult(
            shouldHandle = true,
            nextPosition = PlaybackPosition(chunkIndex = currentPosition.chunkIndex + 1, offsetInChunkChars = 0),
            shouldPlayNextChunk = true,
            reachedEnd = false,
            handledUtteranceId = event.utteranceId,
        )
    }

    return DoneTransitionResult(
        shouldHandle = true,
        nextPosition = currentPosition,
        shouldPlayNextChunk = false,
        reachedEnd = true,
        handledUtteranceId = event.utteranceId,
    )
}
