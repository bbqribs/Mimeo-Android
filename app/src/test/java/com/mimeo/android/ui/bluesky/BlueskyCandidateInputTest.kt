package com.mimeo.android.ui.bluesky

import com.mimeo.android.util.bluesky.normalizeBlueskyHandleInput
import com.mimeo.android.util.bluesky.parseBlueskyListIdentifierInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyCandidateInputTest {
    @Test
    fun normalizeHandleStripsAtPrefix() {
        assertEquals("alice.bsky.social", normalizeBlueskyHandleInput(" @alice.bsky.social "))
    }

    @Test
    fun parseListUrlToAtUri() {
        val result = parseBlueskyListIdentifierInput("https://bsky.app/profile/alice.bsky.social/lists/3kztkpsn5qj22")

        assertTrue(result.ok)
        assertEquals("at://alice.bsky.social/app.bsky.graph.list/3kztkpsn5qj22", result.uri)
        assertNull(result.error)
    }

    @Test
    fun parseAtUriKeepsUri() {
        val uri = "at://did:plc:abc/app.bsky.graph.list/xyz"
        val result = parseBlueskyListIdentifierInput(uri)

        assertTrue(result.ok)
        assertEquals(uri, result.uri)
    }

    @Test
    fun parseFeedGeneratorRejectsUnsupportedSource() {
        val result = parseBlueskyListIdentifierInput("https://bsky.app/profile/alice.bsky.social/feed/news")

        assertFalse(result.ok)
        assertEquals("Feed generators are not supported yet.", result.error)
    }

    @Test
    fun normalizeHandleStripsLeadingAt() {
        assertEquals("bob.bsky.social", normalizeBlueskyHandleInput("@bob.bsky.social"))
    }

    @Test
    fun normalizeHandleBlankReturnsNull() {
        assertNull(normalizeBlueskyHandleInput("   "))
    }

    @Test
    fun normalizeHandleEmptyReturnsNull() {
        assertNull(normalizeBlueskyHandleInput(""))
    }

    @Test
    fun parseListBlankInputReturnsError() {
        val result = parseBlueskyListIdentifierInput("  ")

        assertFalse(result.ok)
        assertEquals("List URL is required.", result.error)
    }

    @Test
    fun parseListInvalidUrlReturnsError() {
        val result = parseBlueskyListIdentifierInput("https://example.com/notabskylist")

        assertFalse(result.ok)
        assertEquals("Use a bsky.app list URL.", result.error)
    }

    @Test
    fun parseFeedGeneratorAtUriRejectsUnsupported() {
        val result = parseBlueskyListIdentifierInput("at://did:plc:abc/app.bsky.feed.generator/xyz")

        assertFalse(result.ok)
        assertEquals("Feed generators are not supported yet.", result.error)
    }
}
