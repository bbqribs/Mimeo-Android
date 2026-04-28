package com.mimeo.android.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
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

    @Test
    fun encodesSmartPlaylistWritePayloadForDedicatedEndpoint() {
        val payload = SmartPlaylistWriteRequest(
            name = "Unread Python",
            filterDefinition = SmartPlaylistFilterDefinition(
                keyword = "python",
                sourceLabels = listOf("HN", "Lobsters"),
                includeArchived = "false",
                favoritesOnly = true,
                readStatus = "unread",
            ),
            sort = "saved_asc",
        )

        val encoded = json.parseToJsonElement(json.encodeToString(payload)).jsonObject
        val filter = encoded["filter_definition"]!!.jsonObject

        assertEquals("Unread Python", encoded["name"]!!.jsonPrimitive.content)
        assertEquals("python", filter["keyword"]!!.jsonPrimitive.content)
        assertEquals("unread", filter["read_status"]!!.jsonPrimitive.content)
        assertEquals("saved_asc", encoded["sort"]!!.jsonPrimitive.content)
    }
}
