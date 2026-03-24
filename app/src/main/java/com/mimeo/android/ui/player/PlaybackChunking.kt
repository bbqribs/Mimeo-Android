package com.mimeo.android.ui.player

import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk

private const val FALLBACK_CHUNK_MAX_CHARS = 900

internal fun buildPlaybackChunks(payload: ItemTextResponse): List<PlaybackChunk> {
    val apiChunks = payload.chunks.orEmpty()
    if (apiChunks.isNotEmpty()) {
        return apiChunks
            .sortedBy { it.index }
            .map { chunk ->
                val cleanText = normalizeChunkWhitespace(chunk.text)
                val safeStart = chunk.startChar.coerceAtLeast(0)
                val safeEnd = maxOf(safeStart, chunk.endChar)
                PlaybackChunk(
                    index = chunk.index,
                    startChar = safeStart,
                    endChar = safeEnd,
                    text = if (cleanText.isBlank()) chunk.text.trim() else cleanText,
                )
            }
            .filter { it.text.isNotBlank() && it.length > 0 }
    }

    val seeds = payload.paragraphs
        ?.map(::normalizeChunkWhitespace)
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .ifEmpty {
            payload.text
                .split(Regex("\\n\\s*\\n+"))
                .map(::normalizeChunkWhitespace)
                .filter { it.isNotBlank() }
        }

    val chunks = mutableListOf<PlaybackChunk>()
    var cursor = 0
    var index = 0
    for (seed in seeds) {
        for (part in splitChunkByLength(seed, FALLBACK_CHUNK_MAX_CHARS)) {
            val start = cursor
            val end = start + part.length
            chunks += PlaybackChunk(
                index = index,
                startChar = start,
                endChar = end,
                text = part,
            )
            cursor = end + 1
            index += 1
        }
    }
    if (chunks.isNotEmpty()) return chunks

    val fallback = normalizeChunkWhitespace(payload.text)
    if (fallback.isBlank()) return emptyList()
    return listOf(
        PlaybackChunk(
            index = 0,
            startChar = 0,
            endChar = fallback.length,
            text = fallback,
        ),
    )
}

private fun normalizeChunkWhitespace(value: String): String {
    return value.replace(Regex("\\s+"), " ").trim()
}

private fun splitChunkByLength(value: String, maxChars: Int): List<String> {
    if (value.length <= maxChars) return listOf(value)
    val result = mutableListOf<String>()
    val words = value.split(" ")
    val sb = StringBuilder()
    for (word in words) {
        if (sb.isEmpty()) {
            sb.append(word)
            continue
        }
        if (sb.length + 1 + word.length > maxChars) {
            result += sb.toString().trim()
            sb.clear()
            sb.append(word)
        } else {
            sb.append(' ').append(word)
        }
    }
    if (sb.isNotEmpty()) {
        result += sb.toString().trim()
    }
    return result
}

