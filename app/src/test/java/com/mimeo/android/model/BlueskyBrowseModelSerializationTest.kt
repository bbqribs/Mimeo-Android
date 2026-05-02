package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlueskyBrowseModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun blueskyBrowseResponseDecodesItems() {
        val payload = """
            {
              "items": [
                {
                  "id": 42,
                  "url": "https://example.com/article",
                  "title": "Test Article",
                  "source_type": "bluesky_harvest",
                  "source_label": "my-list",
                  "created_at": "2026-04-01T12:00:00Z",
                  "status": "ready",
                  "word_count": 800,
                  "bluesky": {
                    "source_id": 7,
                    "source_label": "my-list",
                    "source_type": "list_feed",
                    "post_url": "https://bsky.app/profile/user.bsky.social/post/abc",
                    "author_handle": "user.bsky.social",
                    "post_indexed_at": "2026-04-01T11:55:00Z"
                  }
                }
              ],
              "next_cursor": "eyJvZmZzZXQiOiA1MH0",
              "total_known": null
            }
        """.trimIndent()

        val result = json.decodeFromString<BlueskyBrowseResponse>(payload)

        assertEquals(1, result.items.size)
        assertEquals("eyJvZmZzZXQiOiA1MH0", result.nextCursor)
        assertNull(result.totalKnown)

        val item = result.items[0]
        assertEquals(42, item.id)
        assertEquals("Test Article", item.title)
        assertEquals(7, item.bluesky.sourceId)
        assertEquals("user.bsky.social", item.bluesky.authorHandle)
    }

    @Test
    fun blueskyBrowseItemHandlesOptionalFields() {
        val payload = """
            {
              "id": 10,
              "url": "https://example.com/b",
              "bluesky": {
                "source_id": 3,
                "source_label": "feed",
                "source_type": "following"
              }
            }
        """.trimIndent()

        val item = json.decodeFromString<BlueskyBrowseItem>(payload)

        assertNull(item.title)
        assertNull(item.bluesky.authorHandle)
        assertNull(item.bluesky.postUrl)
        assertNull(item.bluesky.postIndexedAt)
        assertEquals("following", item.bluesky.sourceType)
    }

    @Test
    fun blueskyBrowsePinResponseDecodesWithSource() {
        val payload = """
            {
              "id": 1,
              "source_id": 5,
              "position": 1,
              "created_at": "2026-04-01T10:00:00Z",
              "source": {
                "id": 5,
                "source_type": "following",
                "actor": "user.bsky.social",
                "display_name": "My Follow Feed",
                "enabled": true,
                "poll_interval_minutes": 60,
                "next_harvest_at": null,
                "last_attempted_at": null,
                "last_harvested_at": null,
                "created_at": "2026-03-01T00:00:00Z",
                "updated_at": "2026-03-15T00:00:00Z"
              }
            }
        """.trimIndent()

        val pin = json.decodeFromString<BlueskyBrowsePinResponse>(payload)

        assertEquals(1, pin.id)
        assertEquals(5, pin.sourceId)
        assertEquals(1, pin.position)
        assertEquals("My Follow Feed", pin.source?.displayName)
        assertEquals("My Follow Feed", pin.source?.resolvedName)
    }

    @Test
    fun blueskySourceInfoFallsBackToActorWhenNoDisplayName() {
        val payload = """
            {
              "id": 9,
              "source_type": "following",
              "actor": "fallback.bsky.social",
              "enabled": false,
              "poll_interval_minutes": 120,
              "created_at": "2026-01-01T00:00:00Z",
              "updated_at": "2026-01-01T00:00:00Z"
            }
        """.trimIndent()

        val source = json.decodeFromString<BlueskySourceInfo>(payload)

        assertNull(source.displayName)
        assertEquals("fallback.bsky.social", source.resolvedName)
    }
}
