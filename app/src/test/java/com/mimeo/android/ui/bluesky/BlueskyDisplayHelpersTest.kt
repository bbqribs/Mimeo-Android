package com.mimeo.android.ui.bluesky

import org.junit.Assert.assertEquals
import org.junit.Test

class BlueskyDisplayHelpersTest {

    // cleanSourceLabel

    @Test
    fun cleanSourceLabel_listFeedWithAtUri_returnsBlueskyList() {
        assertEquals("Bluesky List", cleanSourceLabel("at://did:plc:abc/app.bsky.graph.list/xyz", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedWithHttpUri_returnsBlueskyList() {
        assertEquals("Bluesky List", cleanSourceLabel("https://bsky.app/profile/alice/lists/123", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedPinnedPrefixWithAtUri_returnsBlueskyList() {
        assertEquals("Bluesky List", cleanSourceLabel("Pinned: at://did:plc:abc/app.bsky.graph.list/xyz", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_listFeedNormalLabel_returnsLabel() {
        assertEquals("My Cool List", cleanSourceLabel("My Cool List", "list_feed"))
    }

    @Test
    fun cleanSourceLabel_feedGeneratorWithAtUri_returnsBlueskyFeed() {
        assertEquals("Bluesky Feed", cleanSourceLabel("at://did:plc:abc/app.bsky.feed.generator/xyz", "feed_generator"))
    }

    @Test
    fun cleanSourceLabel_feedGeneratorWithHttpUri_returnsBlueskyFeed() {
        assertEquals("Bluesky Feed", cleanSourceLabel("https://bsky.app/profile/alice/feed/news", "feed_generator"))
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
        assertEquals("Bluesky Feed", blueskySourceDisplayName("at://did:plc:abc/app.bsky.feed.generator/xyz", "Feed"))
        // "List feed" contains "feed" which is checked first, so feed wins
        assertEquals("Bluesky Feed", blueskySourceDisplayName("at://did:plc:abc", "List feed"))
    }

    @Test
    fun blueskySourceDisplayName_atUriWithListTypeLabel_returnsBlueskyList() {
        // "List" contains "list" but not "feed", so the list branch wins
        assertEquals("Bluesky List", blueskySourceDisplayName("at://did:plc:abc/app.bsky.graph.list/xyz", "List"))
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
            "Bluesky List",
            sanitizeUserFacingSourceLabel("at://did:plc:abc/app.bsky.graph.list/xyz"),
        )
    }

    @Test
    fun sanitizeUserFacingSourceLabel_bareAtUriFeedContext_returnsBlueskyFeed() {
        assertEquals(
            "Bluesky Feed",
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

    // formatCandidateTimestamp

    @Test
    fun formatCandidateTimestamp_malformedInput_returnsTruncatedInput() {
        val result = formatCandidateTimestamp("not-a-date")
        assertEquals("not-a-date", result)
    }

    @Test
    fun formatCandidateTimestamp_recentIso_returnsRelativeLabel() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusMinutes(5)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("just now", formatCandidateTimestamp(iso))
    }

    @Test
    fun formatCandidateTimestamp_hoursAgoIso_returnsHourLabel() {
        val iso = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .minusHours(3)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertEquals("3h ago", formatCandidateTimestamp(iso))
    }
}
