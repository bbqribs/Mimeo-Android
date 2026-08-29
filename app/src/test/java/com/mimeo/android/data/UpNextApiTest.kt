package com.mimeo.android.data

import com.mimeo.android.model.UpNextSessionWriteRequest
import com.mimeo.android.model.UpNextPointerAdvanceRequest
import com.mimeo.android.model.UpNextHistoryRemovalTarget
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpNextApiTest {
    private fun client() = ApiClient(OkHttpClient.Builder().followRedirects(false).build())

    private val populatedSession = """
        {
          "version": 4, "session_id": 19, "pointer_version": 7,
          "items": [{
            "item_id": 22, "position": 0, "title": "Article", "url": "https://example.com/a",
            "host": "example.com", "status": "ready", "active_content_version_id": 9,
            "strategy_used": "readability_http", "word_count": 321,
            "estimated_listen_minutes": 3, "has_active_content": true,
            "resume_read_percent": 12, "last_read_percent": 40,
            "progress_percent": 12, "furthest_percent": 40,
            "last_opened_at": null, "created_at": "2026-07-17T10:00:00Z",
            "archived_at": "2026-07-17T11:00:00Z", "is_archived": true, "is_muted": true
          }],
          "current_item_id": 22, "seed_source_kind": "playlist",
          "seed_source_label": "Reading list", "seeded_at": "2026-07-17T10:00:00Z",
          "updated_at": "2026-07-17T11:00:00Z", "dirty_since_seed": false
        }
    """.trimIndent()

    @Test
    fun readDecodesAbsentAndPopulatedSessions() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":null}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            assertNull(client().getUpNextSession(server.url("/").toString(), "token"))
            val session = client().getUpNextSession(server.url("/").toString(), "token")!!
            assertEquals(4L, session.version)
            assertEquals(19L, session.sessionId)
            assertEquals(7L, session.pointerVersion)
            assertEquals(listOf(22), session.items.map { it.itemId })
            assertEquals(22, session.currentItemId)
            assertEquals("Reading list", session.seedSourceLabel)
            assertTrue(session.items.single().isArchived)
            assertTrue(session.items.single().isMuted)
            repeat(2) {
                val request = server.takeRequest()
                assertEquals("GET", request.method)
                assertEquals("/up-next/session", request.path)
                assertEquals("Bearer token", request.getHeader("Authorization"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun createAndReplacementSendExactExpectedVersion() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            val api = client()
            api.putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(null, listOf(22), 22, "playlist", "Reading list"),
            )
            api.putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(4, listOf(22), 22, "playlist", "Reading list"),
            )
            val create = server.takeRequest()
            val replace = server.takeRequest()
            assertEquals("PUT", create.method)
            val createBody = create.body.readUtf8()
            assertTrue(createBody.contains("\"expected_version\":null"))
            assertFalse(createBody.contains("history", ignoreCase = true))
            assertTrue(replace.body.readUtf8().contains("\"expected_version\":4"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun clearSendsObservedVersionInDeleteBody() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            client().clearUpNextSession(server.url("/").toString(), "token", 4)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/up-next/session", request.path)
            assertEquals("{\"expected_version\":4,\"clear_history\":false}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun pointerAdvanceUsesTheAtomicHistoryEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            client().advanceUpNextPointer(
                server.url("/").toString(),
                "token",
                UpNextPointerAdvanceRequest(7, 19, 22, 23),
            )
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/up-next/session/advance", request.path)
            assertEquals("Bearer token", request.getHeader("Authorization"))
            assertEquals(
                "{\"expected_pointer_version\":7,\"session_id\":19,\"from_item_id\":22,\"to_item_id\":23}",
                request.body.readUtf8(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun historyReadUsesAccountLimitIncludesBinnedRowsAndRetainsProjectionOrder() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{
                  "history": {
                    "entries": [
                      {
                        "entry_id": 101, "item_id": 22, "played_at": "2026-08-24T10:00:00Z",
                        "title": "First play", "url": "https://example.com/a", "host": "example.com",
                        "status": "ready", "active_content_version_id": 9,
                        "strategy_used": "readability_http", "word_count": 321,
                        "estimated_listen_minutes": 3, "has_active_content": true,
                        "resume_read_percent": 12, "last_read_percent": 40,
                        "progress_percent": 12, "furthest_percent": 40,
                        "last_opened_at": null, "created_at": "2026-07-17T10:00:00Z",
                        "archived_at": null, "is_archived": false, "is_muted": false,
                        "still_in_session": true, "is_trashed": false,
                        "future_additive_field": "ignored"
                      },
                      {
                        "entry_id": 205, "item_id": 22, "played_at": "2026-08-24T11:00:00Z",
                        "title": "Binned play", "url": "https://example.com/b", "host": "example.com",
                        "status": "ready", "active_content_version_id": 9,
                        "strategy_used": "readability_http", "word_count": 321,
                        "estimated_listen_minutes": 3, "has_active_content": true,
                        "resume_read_percent": 12, "last_read_percent": 40,
                        "progress_percent": 12, "furthest_percent": 40,
                        "last_opened_at": null, "created_at": "2026-07-17T10:00:00Z",
                        "archived_at": null, "is_archived": false, "is_muted": false,
                        "still_in_session": false, "is_trashed": true
                      }
                    ],
                    "has_more": true,
                    "recording_since": "2026-08-24T00:00:00Z",
                    "last_removal_at": "2026-08-25T00:00:00Z",
                    "snapshot_through_entry_id": 205,
                    "configured_limit": 10,
                    "effective_limit": 10,
                    "unknown_projection_field": true
                  }
                }""".trimIndent(),
            ),
        )
        server.start()
        try {
            val history = client().getUpNextHistory(server.url("/").toString(), "token")

            assertEquals(listOf(22, 22), history.entries.map { it.itemId })
            assertEquals(listOf(101L, 205L), history.entries.map { it.entryId })
            assertEquals(
                listOf("2026-08-24T10:00:00Z", "2026-08-24T11:00:00Z"),
                history.entries.map { it.playedAt },
            )
            assertTrue(history.entries.first().stillInSession)
            assertFalse(history.entries.last().stillInSession)
            assertTrue(history.entries.last().isTrashed)
            assertTrue(history.hasMore)
            assertEquals("2026-08-24T00:00:00Z", history.recordingSince)
            assertEquals("2026-08-25T00:00:00Z", history.lastRemovalAt)
            assertEquals(205L, history.snapshotThroughEntryId)
            assertEquals(10, history.configuredLimit)
            assertEquals(10, history.effectiveLimit)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/up-next/history?include_trashed=true", request.path)
            assertFalse(request.path.orEmpty().contains("limit="))
            assertEquals("Bearer token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun historyMutationsAndPreferencesSerializeExactFencesAndAtomicClear() = runBlocking {
        val historyBody = """{
          "entries": [], "has_more": false,
          "recording_since": "2026-08-24T00:00:00Z", "last_removal_at": null,
          "snapshot_through_entry_id": 205, "configured_limit": 10, "effective_limit": 10
        }""".trimIndent()
        val mutationBody = """{
          "result": {"operation":"remove","requested_count":2,"changed_count":2,"removed_occurrence_count":3},
          "history": $historyBody
        }""".trimIndent()
        val preferencesBody = """{"preferences":{
          "history_display_limit":10,
          "history_recording_since":"2026-08-24T00:00:00Z",
          "history_last_removal_at":null,
          "future_field":"ignored"
        }}""".trimIndent()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mutationBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(mutationBody.replace("\"remove\"", "\"clear\"")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(preferencesBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody(preferencesBody.replace("10", "50")))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"session":$populatedSession}"""))
        server.start()
        try {
            val api = client()
            api.removeUpNextHistory(
                server.url("/").toString(),
                "token",
                listOf(UpNextHistoryRemovalTarget(22, 101), UpNextHistoryRemovalTarget(33, 205)),
            )
            api.clearUpNextHistory(server.url("/").toString(), "token", 205)
            assertEquals(10, api.getUpNextPreferences(server.url("/").toString(), "token").historyDisplayLimit)
            assertEquals(50, api.patchUpNextPreferences(server.url("/").toString(), "token", 50).historyDisplayLimit)
            api.clearUpNextSession(
                server.url("/").toString(),
                "token",
                expectedVersion = 4,
                clearHistory = true,
                historyThroughEntryId = 205,
            )

            val remove = server.takeRequest()
            assertEquals("POST", remove.method)
            assertEquals("/up-next/history/remove", remove.path)
            assertEquals(
                "{\"entries\":[{\"item_id\":22,\"through_entry_id\":101},{\"item_id\":33,\"through_entry_id\":205}]}",
                remove.body.readUtf8(),
            )
            val clear = server.takeRequest()
            assertEquals("/up-next/history/clear", clear.path)
            assertEquals("{\"snapshot_through_entry_id\":205}", clear.body.readUtf8())
            assertEquals("/up-next/preferences", server.takeRequest().path)
            val patch = server.takeRequest()
            assertEquals("PATCH", patch.method)
            assertEquals("{\"history_display_limit\":50}", patch.body.readUtf8())
            val combined = server.takeRequest()
            assertEquals("DELETE", combined.method)
            assertEquals("/up-next/session", combined.path)
            assertEquals(
                "{\"expected_version\":4,\"clear_history\":true,\"history_through_entry_id\":205}",
                combined.body.readUtf8(),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun invalidHistoryPreferenceNeverReachesNetwork() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            try {
                client().patchUpNextPreferences(server.url("/").toString(), "token", 0)
                fail("Expected invalid limit")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, server.requestCount)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun staleCombinedClearReturnsAuthoritativeTruthWithoutAutomaticReplay() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"up_next_version_conflict","message":"refresh"},"current_session":$populatedSession}""",
            ),
        )
        server.start()
        try {
            try {
                client().clearUpNextSession(
                    server.url("/").toString(),
                    "token",
                    expectedVersion = 3,
                    clearHistory = true,
                    historyThroughEntryId = 205,
                )
                fail("Expected version conflict")
            } catch (error: UpNextVersionConflictException) {
                assertEquals(4L, error.currentSession?.version)
                assertEquals(1, server.requestCount)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun conflictDecodesAuthoritativeCurrentSessionWithoutBecomingApiFailure() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"up_next_pointer_version_conflict","message":"refresh"},"current_session":$populatedSession}""",
            ),
        )
        server.start()
        try {
            client().putUpNextSession(
                server.url("/").toString(),
                "token",
                UpNextSessionWriteRequest(3, listOf(22), 22, "custom", "Android Up Next"),
            )
            fail("Expected version conflict")
        } catch (error: UpNextVersionConflictException) {
            assertEquals(4L, error.currentSession?.version)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun authenticationFailureRemainsDistinctFromVersionConflict() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))
        server.start()
        try {
            client().getUpNextSession(server.url("/").toString(), "stale-token")
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(401, error.statusCode)
        } finally {
            server.shutdown()
        }
    }
}
