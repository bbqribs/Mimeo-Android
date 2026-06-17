package com.mimeo.android.ui.playlists

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copy-audit guards for the smart playlist filter summary. Normal reader surfaces must use
 * user vocabulary (Bin, Archive) and avoid internal store terms like "trashed".
 */
class SmartPlaylistFilterCopyTest {

    @Test
    fun defaultFilterLabelUsesReaderVocabulary() {
        val label = SMART_PLAYLIST_DEFAULT_FILTER_LABEL.lowercase()

        assertFalse("must not surface internal 'trash' term", label.contains("trash"))
        assertTrue("should reference the Bin", label.contains("bin"))
        assertTrue("should reference the Archive", label.contains("archive"))
    }

    @Test
    fun filterPartsDescribeArchiveWithoutTrashTerms() {
        val filter = buildJsonObject {
            put("include_archived", "only")
            put("keyword", "rust")
        }

        val parts = smartFilterParts(filter)

        assertEquals(listOf("keyword: rust", "archived only"), parts)
        assertFalse(parts.any { it.lowercase().contains("trash") })
    }
}
