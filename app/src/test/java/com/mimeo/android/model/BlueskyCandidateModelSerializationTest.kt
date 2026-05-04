package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyCandidateModelSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pickerResponseDecodesSourcesPinsAndCaps() {
        val payload = """
            {
              "connection": {"connected": true, "handle": "alice.bsky.social", "did": "did:plc:alice"},
              "timeline": {"available": true},
              "lists": [{"uri": "at://did:plc:alice/app.bsky.graph.list/reading", "name": "Reading", "item_count": 5}],
              "feeds": [],
              "accounts": [],
              "pins": [{"source_id": 9, "kind": "author_feed", "handle": "bob.bsky.social", "display_name": null}],
              "caps": {"max_age_hours": 24, "max_posts": 30, "max_links": 15, "max_age_hours_ceiling": 168, "max_posts_ceiling": 100, "max_links_ceiling": 50}
            }
        """.trimIndent()

        val picker = json.decodeFromString<BlueskyPickerResponse>(payload)

        assertTrue(picker.connection.connected == true)
        assertTrue(picker.timeline.available)
        assertEquals("Reading", picker.lists.single().name)
        assertEquals("@bob.bsky.social", picker.pins.single().resolvedLabel)
        assertEquals(24, picker.caps.maxAgeHours)
        assertEquals(30, picker.caps.maxPosts)
        assertEquals(15, picker.caps.maxLinks)
    }

    @Test
    fun candidateScanResponseDecodesSavedStateAndPostContext() {
        val payload = """
            {
              "source": {"source_type": "author_feed", "identifier": "alice.bsky.social", "display_label": "@alice.bsky.social", "source_id": null},
              "scan": {"max_age_hours": 24, "max_posts": 30, "max_links": 15, "posts_scanned": 1, "posts_skipped_old": 0, "stopped_reason": "cursor_exhausted"},
              "candidates": [{
                "article_url": "https://example.com/story",
                "normalized_url": "https://example.com/story",
                "title": "Candidate title",
                "domain": "example.com",
                "bluesky": {
                  "post_uri": "at://did:plc:alice/app.bsky.feed.post/abc",
                  "post_url": "https://bsky.app/profile/alice.bsky.social/post/abc",
                  "author_handle": "alice.bsky.social",
                  "author_display_name": "Alice",
                  "text_snippet": "Worth reading",
                  "indexed_at": "2026-04-30T12:00:00Z"
                },
                "source_label": "@alice.bsky.social",
                "source_type": "author_feed",
                "saved": false,
                "saved_state": "unsaved",
                "item_id": null,
                "read_link": null
              }],
              "fetched_at": "2026-04-30T12:01:00Z",
              "live": true
            }
        """.trimIndent()

        val response = json.decodeFromString<BlueskyCandidateScanResponse>(payload)
        val candidate = response.candidates.single()

        assertEquals("author_feed", response.source.sourceType)
        assertNull(response.source.sourceId)
        assertEquals(1, response.scan.postsScanned)
        assertEquals("Candidate title", candidate.title)
        assertEquals("example.com", candidate.domain)
        assertEquals("Worth reading", candidate.bluesky.textSnippet)
        assertFalse(candidate.saved)
        assertEquals("unsaved", candidate.savedState)
    }
}
