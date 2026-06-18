package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyCandidate
import com.mimeo.android.model.BlueskyCandidatePostContext
import com.mimeo.android.model.BlueskyCandidateScan
import com.mimeo.android.model.BlueskyCandidateScanResponse
import com.mimeo.android.model.BlueskyCandidateSource
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.model.BlueskyPickerPinItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlueskyBrowseScreenTest {

    @Test
    fun pinnedListCanResolveFromSelectedSourceIdWithoutScanResults() {
        val sourceId = findPinnedSourceId(
            scan = null,
            selected = BlueskyCandidateSourceSelection(
                sourceKind = "list_feed",
                displayLabel = "Empty list",
                uri = "at://did:plc:example/app.bsky.graph.list/empty",
                sourceId = 42,
            ),
            pins = listOf(
                BlueskyPickerPinItem(
                    sourceId = 42,
                    kind = "list_feed",
                    uri = "at://did:plc:example/app.bsky.graph.list/empty",
                    displayName = "Empty list",
                ),
            ),
        )

        assertEquals(42, sourceId)
    }

    @Test
    fun blueskyBrowseCopyUsesUserFacingLinkLanguage() {
        val scan = BlueskyCandidateScanResponse(
            source = BlueskyCandidateSource(
                sourceType = "list_feed",
                identifier = "at://did:plc:example/app.bsky.graph.list/links",
                displayLabel = "at://did:plc:example/app.bsky.graph.list/links",
            ),
            scan = BlueskyCandidateScan(
                maxAgeHours = 24,
                maxPosts = 50,
                maxLinks = 10,
                postsScanned = 12,
                postsSkippedOld = 0,
                stoppedReason = "limit",
            ),
            candidates = listOf(
                BlueskyCandidate(
                    articleUrl = "https://example.com/story",
                    normalizedUrl = "https://example.com/story",
                    bluesky = BlueskyCandidatePostContext(
                        postUri = "at://did:plc:example/app.bsky.feed.post/abc",
                    ),
                    sourceLabel = "at://did:plc:example/app.bsky.graph.list/links",
                    sourceType = "list_feed",
                ),
            ),
        )
        val renderedCopy = listOf(
            blueskyScanStatsCopy(scan),
            blueskyPinShortcutCopy(),
            blueskyCandidateSourceLine(scan.source.displayLabel, scan.source.sourceType),
        )

        renderedCopy.forEach { copy ->
            assertFalse(copy, copy.contains("candidate", ignoreCase = true))
            assertFalse(copy, copy.contains("at://", ignoreCase = true))
            assertFalse(copy, copy.contains("harvest", ignoreCase = true))
            assertFalse(copy, copy.contains("max age", ignoreCase = true))
            assertFalse(copy, copy.contains("max posts", ignoreCase = true))
            assertFalse(copy, copy.contains("max links", ignoreCase = true))
            assertFalse(copy, copy.contains("operator", ignoreCase = true))
        }
        assertEquals("Checked 12 posts, found 1 links", renderedCopy[0])
        assertEquals("Bluesky list · List", renderedCopy[2])
    }

    @Test
    fun emptySourceAndNoLinkStatesUseUserFacingCopy() {
        // The "no source chosen yet" and "no links found" empty states must read as plain
        // user language — never raw provider/operator vocabulary.
        val emptyStateCopy = listOf(blueskyChooseSourceCopy(), blueskyNoLinksCopy())
        emptyStateCopy.forEach { copy ->
            assertFalse(copy.isBlank())
            assertFalse(copy, copy.contains("candidate", ignoreCase = true))
            assertFalse(copy, copy.contains("at://", ignoreCase = true))
            assertFalse(copy, copy.contains("harvest", ignoreCase = true))
            assertFalse(copy, copy.contains("did:", ignoreCase = true))
            assertFalse(copy, copy.contains("job", ignoreCase = true))
        }
        assertEquals("Choose a source to scan for links from Bluesky.", emptyStateCopy[0])
    }
}
