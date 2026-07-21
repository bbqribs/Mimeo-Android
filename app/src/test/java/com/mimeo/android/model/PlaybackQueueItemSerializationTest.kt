package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueItemSerializationTest {
    @Test
    fun decodesSmartQueueRevisionWithItsResponseSnapshot() {
        val response = json.decodeFromString<PlaybackQueueResponse>(
            """
            {
              "count": 2,
              "revision": "sq1.account.7",
              "reorder_allowed": true,
              "items": [
                {"item_id": 20, "url": "https://example.com/twenty"},
                {"item_id": 10, "url": "https://example.com/ten"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("sq1.account.7", response.revision)
        assertEquals(listOf(20, 10), response.items.map { it.itemId })
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesSmartQueueReorderMetadata() {
        val response = json.decodeFromString<PlaybackQueueResponse>(
            """
            {
              "count": 1,
              "total_count": 1,
              "reorder_allowed": true,
              "reorder_unavailable_reason": null,
              "items": [
                {
                  "item_id": 7,
                  "url": "https://example.com/item",
                  "smart_queue_position": 1.0
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(true, response.reorderAllowed)
        assertEquals(1.0, response.items.single().smartQueuePosition ?: -1.0, 0.0)
    }

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
