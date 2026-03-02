package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueItemSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun prefersAliasFieldsWhenPresent() {
        val item = json.decodeFromString<PlaybackQueueItem>(
            """
            {
              "item_id": 7,
              "url": "https://example.com/item",
              "resume_read_percent": 30,
              "last_read_percent": 80,
              "progress_percent": 35,
              "furthest_percent": 85
            }
            """.trimIndent(),
        )

        assertEquals(35, item.progressPercent)
        assertEquals(85, item.furthestPercent)
    }

    @Test
    fun fallsBackToLegacyFieldsWhenAliasesAbsent() {
        val item = json.decodeFromString<PlaybackQueueItem>(
            """
            {
              "item_id": 7,
              "url": "https://example.com/item",
              "resume_read_percent": 30,
              "last_read_percent": 80
            }
            """.trimIndent(),
        )

        assertEquals(30, item.progressPercent)
        assertEquals(80, item.furthestPercent)
    }

    @Test
    fun clampsProgressToFurthest() {
        val item = json.decodeFromString<PlaybackQueueItem>(
            """
            {
              "item_id": 7,
              "url": "https://example.com/item",
              "progress_percent": 90,
              "furthest_percent": 40
            }
            """.trimIndent(),
        )

        assertEquals(40, item.progressPercent)
        assertEquals(40, item.furthestPercent)
    }
}
