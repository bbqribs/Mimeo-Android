package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.model.BlueskyPickerPinItem
import org.junit.Assert.assertEquals
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
}
