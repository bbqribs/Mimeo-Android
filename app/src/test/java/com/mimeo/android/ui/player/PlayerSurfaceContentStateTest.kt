package com.mimeo.android.ui.player

import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfaceContentStateTest {
    @Test
    fun reset_clearsSharedContentAndLoadFlags() {
        val state = PlayerSurfaceContentState()
        val payload = ItemTextResponse(
            itemId = 409,
            title = "Sample",
            url = "https://example.com/sample",
            text = "chunk one",
            contentBlocks = null,
            sourceLabel = null,
            sourceType = null,
            sourceUrl = null,
            host = null,
            activeContentVersionId = null,
            totalChars = 9,
        )
        state.textPayload.value = payload
        state.chunks.value = listOf(
            PlaybackChunk(
                index = 0,
                startChar = 0,
                endChar = 9,
                text = "chunk one",
            ),
        )
        state.usingCachedText.value = true
        state.isLoading.value = true
        state.preserveVisibleContentOnReload.value = true
        state.bodyRevealReady.value = true

        state.reset()

        assertNull(state.textPayload.value)
        assertTrue(state.chunks.value.isEmpty())
        assertFalse(state.usingCachedText.value)
        assertFalse(state.isLoading.value)
        assertFalse(state.preserveVisibleContentOnReload.value)
        assertFalse(state.bodyRevealReady.value)
    }
}
