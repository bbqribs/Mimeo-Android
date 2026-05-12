package com.mimeo.android.util.bluesky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyInputTest {

    // --- normalizeBlueskyHandleInput ---

    @Test
    fun `normalizeHandle returns handle without leading at`() {
        assertEquals("alice.bsky.social", normalizeBlueskyHandleInput("alice.bsky.social"))
    }

    @Test
    fun `normalizeHandle strips single leading at sign`() {
        assertEquals("alice.bsky.social", normalizeBlueskyHandleInput("@alice.bsky.social"))
    }

    @Test
    fun `normalizeHandle strips leading at and surrounding whitespace`() {
        assertEquals("alice.bsky.social", normalizeBlueskyHandleInput("  @alice.bsky.social  "))
    }

    @Test
    fun `normalizeHandle returns null for blank input`() {
        assertNull(normalizeBlueskyHandleInput("   "))
    }

    @Test
    fun `normalizeHandle returns null for empty string`() {
        assertNull(normalizeBlueskyHandleInput(""))
    }

    @Test
    fun `normalizeHandle returns null for at sign only`() {
        assertNull(normalizeBlueskyHandleInput("@"))
    }

    @Test
    fun `normalizeHandle returns null for at sign with spaces`() {
        assertNull(normalizeBlueskyHandleInput("  @  "))
    }

    @Test
    fun `normalizeHandle preserves custom domain handle`() {
        assertEquals("bob.example.com", normalizeBlueskyHandleInput("@bob.example.com"))
    }

    // --- parseBlueskyListIdentifierInput ---

    @Test
    fun `parseList accepts bsky app list url and converts to at uri`() {
        val result = parseBlueskyListIdentifierInput("https://bsky.app/profile/alice.bsky.social/lists/3kztkpsn5qj22")

        assertTrue(result.ok)
        assertEquals("at://alice.bsky.social/app.bsky.graph.list/3kztkpsn5qj22", result.uri)
        assertNull(result.error)
    }

    @Test
    fun `parseList accepts at uri directly`() {
        val uri = "at://did:plc:abc123/app.bsky.graph.list/xyz"
        val result = parseBlueskyListIdentifierInput(uri)

        assertTrue(result.ok)
        assertEquals(uri, result.uri)
    }

    @Test
    fun `parseList accepts bsky app url with trailing slash`() {
        val result = parseBlueskyListIdentifierInput("https://bsky.app/profile/alice.bsky.social/lists/abc123/")

        assertTrue(result.ok)
        assertEquals("at://alice.bsky.social/app.bsky.graph.list/abc123", result.uri)
    }

    @Test
    fun `parseList rejects blank input`() {
        val result = parseBlueskyListIdentifierInput("  ")

        assertFalse(result.ok)
        assertEquals("List URL is required.", result.error)
    }

    @Test
    fun `parseList rejects empty input`() {
        val result = parseBlueskyListIdentifierInput("")

        assertFalse(result.ok)
        assertEquals("List URL is required.", result.error)
    }

    @Test
    fun `parseList rejects bsky app feed generator url`() {
        val result = parseBlueskyListIdentifierInput("https://bsky.app/profile/alice.bsky.social/feed/my-feed")

        assertFalse(result.ok)
        assertEquals("Feed generators are not supported yet.", result.error)
    }

    @Test
    fun `parseList rejects at uri for feed generator`() {
        val result = parseBlueskyListIdentifierInput("at://did:plc:abc/app.bsky.feed.generator/xyz")

        assertFalse(result.ok)
        assertEquals("Feed generators are not supported yet.", result.error)
    }

    @Test
    fun `parseList rejects non-bsky url`() {
        val result = parseBlueskyListIdentifierInput("https://example.com/profile/alice/lists/abc")

        assertFalse(result.ok)
        assertEquals("Use a bsky.app list URL.", result.error)
    }

    @Test
    fun `parseList rejects plain text`() {
        val result = parseBlueskyListIdentifierInput("not a url")

        assertFalse(result.ok)
        assertEquals("Use a bsky.app list URL.", result.error)
    }

    @Test
    fun `parseList trims surrounding whitespace before parsing`() {
        val result = parseBlueskyListIdentifierInput("  https://bsky.app/profile/alice.bsky.social/lists/abc123  ")

        assertTrue(result.ok)
        assertEquals("at://alice.bsky.social/app.bsky.graph.list/abc123", result.uri)
    }
}
