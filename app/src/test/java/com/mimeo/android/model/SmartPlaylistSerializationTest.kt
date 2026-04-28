package com.mimeo.android.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SmartPlaylistSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesDedicatedSmartPlaylistListShape() {
        val payload = """
            [
              {
                "id": 7,
                "name": "Unread Python",
                "kind": "smart",
                "filter_definition": {
                  "keyword": "python",
                  "read_status": "unread",
                  "favorites_only": true
                },
                "sort": "saved_desc",
                "pin_count": 2,
                "created_at": "2026-04-28T10:00:00Z",
                "updated_at": "2026-04-28T10:30:00Z"
              }
            ]
        """.trimIndent()

        val playlists = json.decodeFromString(
            ListSerializer(SmartPlaylistSummary.serializer()),
            payload,
        )

        assertEquals(1, playlists.size)
        val playlist = playlists.single()
        assertEquals(7, playlist.id)
        assertEquals("Unread Python", playlist.name)
        assertEquals("smart", playlist.kind)
        assertEquals("python", playlist.filterDefinition["keyword"]?.jsonPrimitive?.content)
        assertEquals(2, playlist.pinCount)
    }

    @Test
    fun manualPlaylistKindDefaultsToManualForLegacyResponses() {
        val playlist = json.decodeFromString(
            PlaylistSummary.serializer(),
            """{"id":3,"name":"Manual","entries":[]}""",
        )

        assertEquals("manual", playlist.kind)
        assertFalse(playlist.kind.equals("smart", ignoreCase = true))
    }
}
