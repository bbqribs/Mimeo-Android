package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyCandidatePostContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyDisplayHelpersTest {

    // cleanSourceLabel

    @Test
    fun cleanSourceLabel_listFeedWithAtUri_returnsBlueskyList() {
        assertEquals("Bluesky list", cleanSourceLabel("at://did:plc:abc/app.bsky.graph.list/xyz", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedWithHttpUri_returnsBlueskyList() {
        assertEquals("Bluesky list", cleanSourceLabel("https://bsky.app/profile/alice/lists/123", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedPinnedPrefixWithAtUri_returnsBlueskyList() {
        assertEquals("Bluesky list", cleanSourceLabel("Pinned: at://did:plc:abc/app.bsky.graph.list/xyz", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedNormalLabel_returnsLabel() {
        assertEquals("My Cool List", cleanSourceLabel("My Cool List", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_feedGeneratorWithAtUri_returnsBlueskyFeed() {
        assertEquals("Bluesky feed", cleanSourceLabel("at://did:plc:abc/app.bsky.feed.generator/xyz", "feed_generator"))
    }

    @Test
    fun cleanSourceLabel_feedGeneratorWithHttpUri_returnsBlueskyFeed() {
        assertEquals("Bluesky feed", cleanSourceLabel("https://bsky.app/profile/alice/feed/news", "feed_generator"))
    }

    @Test
    fun cleanSourceLabel_feedGeneratorNormalLabel_returnsLabel() {
        assertEquals("Tech News", cleanSourceLabel("Tech News", "feed_generator"))
    }

    @Test
    fun cleanSourceLabel_authorFeedAtUri_returnsFormattedSourceType() {
        assertEquals("Account", cleanSourceLabel("at://did:plc:abc", "author_feed"))
    }

    @Test
    fun cleanSourceLabel_homeTimelinePlainLabel_returnsLabel() {
        assertEquals("Bluesky Home Timeline", cleanSourceLabel("Bluesky Home Timeline", "home_timeline"))
    }

    @Test
    fun cleanSourceLabel_blankLabel_returnsFormattedSourceType() {
        assertEquals("Home Timeline", cleanSourceLabel("  ", "home_timeline"))
    }

    @Test
    fun cleanSourceLabel_unknownSourceType_returnsBluesky() {
        assertEquals("Bluesky", cleanSourceLabel("  ", null))
    }

    // formatSourceType

    @Test
    fun formatSourceType_homeTimeline() {
        assertEquals("Home Timeline", formatSourceType("home_timeline"))
    }

    @Test
    fun formatSourceType_listFeed() {
        assertEquals("List", formatSourceType("list_feed"))
    }

    @Test
    fun formatSourceType_authorFeed() {
        assertEquals("Account", formatSourceType("author_feed"))
    }

    @Test
    fun formatSourceType_account() {
        assertEquals("Account", formatSourceType("account"))
    }

    @Test
    fun formatSourceType_unknown() {
        assertEquals("Bluesky", formatSourceType("something_else"))
    }

    @Test
    fun formatSourceType_null() {
        assertEquals("Bluesky", formatSourceType(null))
    }

    // blueskySourceDisplayName

    @Test
    fun blueskySourceDisplayName_plainName_returnsName() {
        assertEquals("My Source", blueskySourceDisplayName("My Source", null))
    }

    @Test
    fun blueskySourceDisplayName_atUriWithFeedTypeLabel_returnsBlueskyFeed() {
        assertEquals("Bluesky feed", blueskySourceDisplayName("at://did:plc:abc/app.bsky.feed.generator/xyz", "Feed"))
        // "List feed" contains "feed" which is checked first, so feed wins
        assertEquals("Bluesky feed", blueskySourceDisplayName("at://did:plc:abc", "List feed"))
    }

    @Test
    fun blueskySourceDisplayName_atUriWithListTypeLabel_returnsBlueskyList() {
        // "List" contains "list" but not "feed", so the list branch wins
        assertEquals("Bluesky list", blueskySourceDisplayName("at://did:plc:abc/app.bsky.graph.list/xyz", "List"))
    }

    @Test
    fun blueskySourceDisplayName_atUriWithNullTypeLabel_returnsBlueskySource() {
        assertEquals("Bluesky Source", blueskySourceDisplayName("at://did:plc:abc", null))
    }

    @Test
    fun blueskySourceDisplayName_atUriWithUnknownTypeLabel_returnsBlueskySource() {
        assertEquals("Bluesky Source", blueskySourceDisplayName("at://did:plc:abc", "home_timeline"))
    }

    @Test
    fun blueskySourceDisplayName_whitespaceOnlyName_returnsEmpty() {
        assertEquals("", blueskySourceDisplayName("  ", null))
    }

    // sanitizeUserFacingSourceLabel

    @Test
    fun sanitizeUserFacingSourceLabel_plainDomain_passesThrough() {
        assertEquals("nytimes.com", sanitizeUserFacingSourceLabel("nytimes.com"))
    }

    @Test
    fun sanitizeUserFacingSourceLabel_prefixWithAtUri_keepsPrefix() {
        assertEquals(
            "Bluesky List",
            sanitizeUserFacingSourceLabel("Bluesky List: at://did:plc:3ydpg/app.bsky.graph.list/xyz"),
        )
    }

    @Test
    fun sanitizeUserFacingSourceLabel_bareAtUriListContext_returnsBlueskyList() {
        assertEquals(
            "Bluesky list",
            sanitizeUserFacingSourceLabel("at://did:plc:abc/app.bsky.graph.list/xyz"),
        )
    }

    @Test
    fun sanitizeUserFacingSourceLabel_bareAtUriFeedContext_returnsBlueskyFeed() {
        assertEquals(
            "Bluesky feed",
            sanitizeUserFacingSourceLabel("at://did:plc:abc/app.bsky.feed.generator/xyz"),
        )
    }

    @Test
    fun sanitizeUserFacingSourceLabel_bareAtUriNoContext_returnsBluesky() {
        assertEquals("Bluesky", sanitizeUserFacingSourceLabel("at://did:plc:abc"))
    }

    @Test
    fun sanitizeUserFacingSourceLabel_blankOrNull_returnsNull() {
        assertEquals(null, sanitizeUserFacingSourceLabel("   "))
        assertEquals(null, sanitizeUserFacingSourceLabel(null))
    }

    // resolvePickerSourceLabel

    @Test
    fun resolvePickerSourceLabel_listRawScanLabel_prefersSelectedName() {
        // Backend scan returns a raw AT-URI for the list; the picked name should win.
        assertEquals(
            "My Cool List",
            resolvePickerSourceLabel(
                scanLabel = "at://did:plc:abc/app.bsky.graph.list/xyz",
                scanType = "list_feed",
                selectedLabel = "My Cool List",
                selectedKind = "list_feed",
            ),
        )
    }

    @Test
    fun resolvePickerSourceLabel_listRawScanAndHttpSelected_fallsBackToGeneric() {
        // Both labels are raw addresses, so the generic list label is the best we can do.
        assertEquals(
            "Bluesky list",
            resolvePickerSourceLabel(
                scanLabel = "at://did:plc:abc/app.bsky.graph.list/xyz",
                scanType = "list_feed",
                selectedLabel = "https://bsky.app/profile/alice/lists/123",
                selectedKind = "list_feed",
            ),
        )
    }

    @Test
    fun resolvePickerSourceLabel_feedNamedScanLabel_usesScanLabel() {
        assertEquals(
            "Tech News",
            resolvePickerSourceLabel(
                scanLabel = "Tech News",
                scanType = "feed_generator",
                selectedLabel = "Tech News",
                selectedKind = "feed_generator",
            ),
        )
    }

    @Test
    fun resolvePickerSourceLabel_nullScan_usesSelectedName() {
        assertEquals(
            "My Cool List",
            resolvePickerSourceLabel(
                scanLabel = null,
                scanType = null,
                selectedLabel = "My Cool List",
                selectedKind = "list_feed",
            ),
        )
    }

    @Test
    fun resolvePickerSourceLabel_bothNull_returnsSelectedSourceFallback() {
        assertEquals(
            "Selected source",
            resolvePickerSourceLabel(
                scanLabel = null,
                scanType = null,
                selectedLabel = null,
                selectedKind = null,
            ),
        )
    }

    @Test
    fun userFacingSourceLabels_hideRawAtUri() {
        val labels = listOf(
            cleanSourceLabel("at://did:plc:abc/app.bsky.graph.list/xyz", "list_feed"),
            blueskySourceDisplayName("at://did:plc:abc/app.bsky.feed.generator/xyz", "Feed"),
            sanitizeUserFacingSourceLabel("at://did:plc:abc/app.bsky.feed.generator/xyz").orEmpty(),
            resolvePickerSourceLabel(
                scanLabel = "at://did:plc:abc/app.bsky.graph.list/xyz",
                scanType = "list_feed",
                selectedLabel = "at://did:plc:abc/app.bsky.graph.list/xyz",
                selectedKind = "list_feed",
            ),
        )

        labels.forEach { label ->
            assertFalse(label, label.contains("at://", ignoreCase = true))
        }
    }

    // formatCandidateTimestamp

    @Test
    fun formatCandidateTimestamp_malformedInput_returnsTruncatedInput() {
        val result = formatCandidateTimestamp("not-a-date")
        assertEquals("not-a-date", result)
    }

    @Test
    fun formatCandidateTimestamp_secondsAgo_returnsCompactSeconds() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusSeconds(20)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("20s", formatCandidateTimestamp(iso))
    }

    @Test
    fun formatCandidateTimestamp_minutesAgo_returnsCompactMinutes() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusMinutes(5)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("5m", formatCandidateTimestamp(iso))
    }

    @Test
    fun formatCandidateTimestamp_hoursAgo_returnsCompactHours() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusHours(4)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("4h", formatCandidateTimestamp(iso))
    }

    @Test
    fun formatCandidateTimestamp_daysAgo_returnsCompactDays() {
        // Days run past a week (Bluesky shows "8d", not "1w"), up to 30 days.
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusDays(8)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("8d", formatCandidateTimestamp(iso))
    }

    @Test
    fun formatCandidateTimestamp_neverUsesAgoSuffix() {
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val iso = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val rendered = listOf(
            formatCandidateTimestamp(now.minusSeconds(20).format(iso)),
            formatCandidateTimestamp(now.minusMinutes(5).format(iso)),
            formatCandidateTimestamp(now.minusHours(4).format(iso)),
            formatCandidateTimestamp(now.minusDays(8).format(iso)),
        )
        rendered.forEach { value -> assertFalse(value, value.contains("ago", ignoreCase = true)) }
        assertEquals(listOf("20s", "5m", "4h", "8d"), rendered)
    }

    // blueskyTextLinkRanges

    @Test
    fun blueskyTextLinkRanges_plainText_returnsEmpty() {
        assertTrue(blueskyTextLinkRanges("Just a plain sentence with no links").isEmpty())
        assertTrue(blueskyTextLinkRanges("").isEmpty())
    }

    @Test
    fun blueskyTextLinkRanges_url_isMatched() {
        val text = "Read it at https://example.com/story today"
        val ranges = blueskyTextLinkRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("https://example.com/story", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun blueskyTextLinkRanges_mentionAndHashtag_areMatched() {
        val text = "thanks @alice.bsky.social for the #news"
        val matched = blueskyTextLinkRanges(text)
            .sortedBy { it.first }
            .map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("@alice.bsky.social", "#news"), matched)
    }

    // sanitizeBlueskyDisplayName

    @Test
    fun sanitizeBlueskyDisplayName_plainName_passesThrough() {
        assertEquals("Alice Example", sanitizeBlueskyDisplayName("  Alice Example  "))
    }

    @Test
    fun sanitizeBlueskyDisplayName_blank_returnsNull() {
        assertNull(sanitizeBlueskyDisplayName("   "))
        assertNull(sanitizeBlueskyDisplayName(null))
    }

    @Test
    fun sanitizeBlueskyDisplayName_dropsNameCarryingAtUri() {
        assertNull(sanitizeBlueskyDisplayName("at://did:plc:abc/post"))
    }

    @Test
    fun sanitizeBlueskyDisplayName_dropsNameCarryingDid() {
        assertNull(sanitizeBlueskyDisplayName("did:plc:abc123"))
    }

    // sanitizeBlueskyHandle

    @Test
    fun sanitizeBlueskyHandle_stripsLeadingAt() {
        assertEquals("alice.bsky.social", sanitizeBlueskyHandle("@alice.bsky.social"))
        assertEquals("alice.bsky.social", sanitizeBlueskyHandle("alice.bsky.social"))
    }

    @Test
    fun sanitizeBlueskyHandle_blank_returnsNull() {
        assertNull(sanitizeBlueskyHandle("   "))
        assertNull(sanitizeBlueskyHandle(null))
    }

    @Test
    fun sanitizeBlueskyHandle_dropsHandleCarryingDid() {
        // A handle should never be a raw DID; drop it rather than leak the identifier.
        assertNull(sanitizeBlueskyHandle("did:plc:abc123"))
    }

    @Test
    fun sanitizeBlueskyHandle_dropsHandleWithWhitespace() {
        // Real handles never contain spaces; an embedded space signals an untrustworthy value.
        assertNull(sanitizeBlueskyHandle("not a handle"))
    }

    // sanitizeBlueskyText

    @Test
    fun sanitizeBlueskyText_plainText_passesThrough() {
        assertEquals("Check out this article", sanitizeBlueskyText("Check out this article"))
    }

    @Test
    fun sanitizeBlueskyText_mentionsAndHashtags_preserved() {
        assertEquals(
            "Great read from @alice.bsky.social #news",
            sanitizeBlueskyText("Great read from @alice.bsky.social #news"),
        )
    }

    @Test
    fun sanitizeBlueskyText_stripsRawAtUri() {
        val result = sanitizeBlueskyText("See at://did:plc:abc/app.bsky.feed.post/xyz now")
        assertNotNull(result)
        assertFalse(result!!, result.contains("at://"))
        assertEquals("See now", result)
    }

    @Test
    fun sanitizeBlueskyText_stripsBareDid() {
        val result = sanitizeBlueskyText("Posted by did:plc:abc123 today")
        assertNotNull(result)
        assertFalse(result!!, result.contains("did:"))
        assertEquals("Posted by today", result)
    }

    @Test
    fun sanitizeBlueskyText_blankOrNull_returnsNull() {
        assertNull(sanitizeBlueskyText("   "))
        assertNull(sanitizeBlueskyText(null))
    }

    @Test
    fun sanitizeBlueskyText_onlyIdentifier_returnsNull() {
        assertNull(sanitizeBlueskyText("at://did:plc:abc/app.bsky.feed.post/xyz"))
    }

    // buildBlueskyPostPreview

    @Test
    fun buildBlueskyPostPreview_fullContext_rendersAllFields() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusHours(2)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(
                postUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
                postUrl = "https://bsky.app/profile/alice.bsky.social/post/xyz",
                authorHandle = "alice.bsky.social",
                authorDisplayName = "Alice Example",
                textSnippet = "A fascinating story worth saving",
                indexedAt = iso,
            ),
        )

        assertNotNull(preview)
        assertEquals("Alice Example", preview!!.displayName)
        assertEquals("alice.bsky.social", preview.handle)
        assertEquals("2h", preview.timestamp)
        assertEquals("A fascinating story worth saving", preview.snippet)
        assertEquals("https://bsky.app/profile/alice.bsky.social/post/xyz", preview.postUrl)
    }

    @Test
    fun buildBlueskyPostPreview_missingPostContext_returnsNull() {
        // Only the internal post URI is present (no author, no text) — there is nothing
        // user-facing to show, so the preview degrades to nothing rather than an empty box.
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(postUri = "at://did:plc:abc/app.bsky.feed.post/xyz"),
        )
        assertNull(preview)
    }

    @Test
    fun buildBlueskyPostPreview_timestampOnly_returnsNull() {
        // A bare timestamp with no attribution or text does not justify a "Bluesky post" box.
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(
                postUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
                indexedAt = iso,
            ),
        )
        assertNull(preview)
    }

    @Test
    fun buildBlueskyPostPreview_snippetOnly_rendersDegraded() {
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(
                postUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
                textSnippet = "Just the text, no author",
            ),
        )
        assertNotNull(preview)
        assertNull(preview!!.displayName)
        assertNull(preview.handle)
        assertNull(preview.timestamp)
        assertNull(preview.postUrl)
        assertEquals("Just the text, no author", preview.snippet)
    }

    @Test
    fun buildBlueskyPostPreview_rejectsAtUriPostUrl() {
        // The post link must be a safe web URL; an at:// value is suppressed.
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(
                postUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
                postUrl = "at://did:plc:abc/app.bsky.feed.post/xyz",
                authorHandle = "alice.bsky.social",
                textSnippet = "Some text",
            ),
        )
        assertNotNull(preview)
        assertNull(preview!!.postUrl)
    }

    @Test
    fun buildBlueskyPostPreview_neverLeaksRawIdentifiers() {
        // Even when the backend stuffs identifiers into every field, the rendered preview
        // must not surface at://, did:, or other backend identifiers on an ordinary card.
        val preview = buildBlueskyPostPreview(
            BlueskyCandidatePostContext(
                postUri = "at://did:plc:abc/app.bsky.feed.post/xyz",
                postUrl = "at://did:plc:abc/app.bsky.feed.post/xyz",
                authorHandle = "did:plc:leak",
                authorDisplayName = "Real Name",
                textSnippet = "Body with at://did:plc:abc/app.bsky.feed.post/xyz inside",
                indexedAt = "not-a-date",
            ),
        )
        assertNotNull(preview)
        val rendered = listOfNotNull(
            preview!!.displayName,
            preview.handle,
            preview.timestamp,
            preview.snippet,
            preview.postUrl,
        )
        rendered.forEach { value ->
            assertFalse(value, value.contains("at://", ignoreCase = true))
            assertFalse(value, value.contains("did:", ignoreCase = true))
        }
        assertEquals("Real Name", preview.displayName)
        // The DID-shaped handle is dropped rather than rendered.
        assertNull(preview.handle)
        assertTrue(preview.snippet!!.startsWith("Body with"))
    }
}
