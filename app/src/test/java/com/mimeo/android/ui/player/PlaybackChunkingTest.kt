package com.mimeo.android.ui.player

import com.mimeo.android.model.ItemTextChunk
import com.mimeo.android.model.ItemTextResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackChunkingTest {

    @Test
    fun buildPlaybackChunksUsesApiChunksWhenPresent() {
        val payload = ItemTextResponse(
            itemId = 1,
            url = "https://example.com",
            text = "ignored",
            chunks = listOf(
                ItemTextChunk(index = 1, startChar = 5, endChar = 10, text = "second"),
                ItemTextChunk(index = 0, startChar = 0, endChar = 5, text = "first"),
            ),
        )

        val chunks = buildPlaybackChunks(payload)

        assertEquals(2, chunks.size)
        assertEquals(0, chunks[0].index)
        assertEquals("first", chunks[0].text)
        assertEquals(1, chunks[1].index)
        assertEquals("second", chunks[1].text)
    }

    @Test
    fun buildPlaybackChunksFallsBackToSingleChunkWhenNoSeedParagraphs() {
        val payload = ItemTextResponse(
            itemId = 1,
            url = "https://example.com",
            text = "   hello   world   ",
            chunks = null,
            paragraphs = null,
        )

        val chunks = buildPlaybackChunks(payload)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].startChar)
        assertTrue(chunks[0].text.contains("hello world"))
    }
}

