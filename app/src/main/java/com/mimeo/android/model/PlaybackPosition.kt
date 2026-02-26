package com.mimeo.android.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class PlaybackPosition(
    val chunkIndex: Int = 0,
    val offsetInChunkChars: Int = 0,
)

data class PlaybackChunk(
    val index: Int,
    val startChar: Int,
    val endChar: Int,
    val text: String,
) {
    val length: Int
        get() = max(0, endChar - startChar)
}

fun calculateCanonicalPercent(
    totalChars: Int,
    chunks: List<PlaybackChunk>,
    position: PlaybackPosition,
): Int {
    if (totalChars <= 0 || chunks.isEmpty()) return 0
    val safeChunkIndex = min(max(position.chunkIndex, 0), chunks.lastIndex)
    val chunk = chunks[safeChunkIndex]
    val safeOffset = min(max(position.offsetInChunkChars, 0), chunk.length)
    val absolute = min(totalChars, max(0, chunk.startChar + safeOffset))
    return floor((absolute.toDouble() / totalChars.toDouble()) * 100.0).toInt().coerceIn(0, 100)
}

fun absoluteCharOffset(
    totalChars: Int,
    chunks: List<PlaybackChunk>,
    position: PlaybackPosition,
): Int {
    if (totalChars <= 0 || chunks.isEmpty()) return 0
    val safeChunkIndex = min(max(position.chunkIndex, 0), chunks.lastIndex)
    val chunk = chunks[safeChunkIndex]
    val safeOffset = min(max(position.offsetInChunkChars, 0), chunk.length)
    return min(totalChars, max(0, chunk.startChar + safeOffset))
}
