package com.mimeo.android.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.UpNextVersionConflictException
import com.mimeo.android.model.UpNextSession
import com.mimeo.android.model.UpNextSessionItem
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingUpNextMovePublisherTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PlaybackRepository
    private lateinit var server: MockWebServer
    private lateinit var publisher: PendingUpNextMovePublisher

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val apiClient = ApiClient(
            OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .build(),
        )
        repository = PlaybackRepository(apiClient, database, context)
        server = MockWebServer()
        server.start()
        publisher = PendingUpNextMovePublisher(repository, apiClient)
        stageBacMove()
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun reportedOfflineNavigationAndRecreationKeepMoveQueuedWhenPreflightFails() = runBlocking {
        // Recreate the repository after the local ABC -> BAC move, matching Inbox -> Up Next
        // navigation plus process recreation while the configured route remains unavailable.
        val restarted = PlaybackRepository(ApiClient(), database, context)
        publisher = PendingUpNextMovePublisher(
            restarted,
            ApiClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build()),
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val result = publisher.publish(
            server.url("/").toString(),
            TOKEN,
            requestStillCurrent = { true },
        )

        assertTrue(result is PendingUpNextMovePublicationResult.PreflightFailed)
        assertTrue((result as PendingUpNextMovePublicationResult.PreflightFailed).error is IOException)
        assertEquals(PendingUpNextMovePhase.QUEUED, restarted.pendingUpNextSemanticMove()?.phase)
        assertEquals(listOf(1, 3, 2), restarted.localUpNextSnapshot()?.itemIds)
        assertOriginalIntent(restarted.pendingUpNextSemanticMove())
        assertEquals(1, server.requestCount)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/up-next/session", request.path)
        assertEquals("Bearer $TOKEN", request.getHeader("Authorization"))
    }

    @Test
    fun successfulPreflightIsReadOnlyThenPublishesOriginalIntentExactlyOnce() = runBlocking {
        server.enqueue(sessionResponse(version = 77, itemIds = listOf(1, 2, 3)))
        server.enqueue(sessionResponse(version = 5, itemIds = listOf(1, 3, 2)))
        var phaseWhenPostStarted: PendingUpNextMovePhase? = null
        var orderWhenPostStarted: List<Int>? = null
        var intentWhenPostStarted: PendingUpNextSemanticMove? = null

        val result = publisher.publish(
            baseUrl = server.url("/").toString(),
            token = TOKEN,
            requestStillCurrent = { true },
            onPostStarting = {
                intentWhenPostStarted = repository.pendingUpNextSemanticMove()
                phaseWhenPostStarted = intentWhenPostStarted?.phase
                orderWhenPostStarted = repository.localUpNextSnapshot()?.itemIds
            },
        )

        assertTrue(result is PendingUpNextMovePublicationResult.Acknowledged)
        assertEquals(PendingUpNextMovePhase.IN_FLIGHT, phaseWhenPostStarted)
        assertEquals(listOf(1, 3, 2), orderWhenPostStarted)
        assertOriginalIntent(intentWhenPostStarted)

        val preflight = server.takeRequest(1, TimeUnit.SECONDS)!!
        val move = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", preflight.method)
        assertEquals("/up-next/session", preflight.path)
        assertEquals("Bearer $TOKEN", preflight.getHeader("Authorization"))
        assertEquals("POST", move.method)
        assertEquals("/up-next/session/move", move.path)
        assertEquals("Bearer $TOKEN", move.getHeader("Authorization"))
        assertEquals(
            "{\"expected_version\":4,\"item_id\":3,\"to_position\":1}",
            move.body.readUtf8(),
        )
        assertEquals(2, server.requestCount)

        val acknowledged = (result as PendingUpNextMovePublicationResult.Acknowledged).session
        repository.applyAuthoritativeUpNextSession(
            session = acknowledged,
            ownerKey = OWNER,
            serverIdentity = SERVER_IDENTITY,
            semanticMoveCompletion = true,
            moveDiagnostic = UpNextMoveDiagnostic(outcome = "applied"),
        )
        assertEquals(listOf(1, 3, 2), repository.localUpNextSnapshot()?.itemIds)
        assertNull(repository.pendingUpNextSemanticMove())

        assertTrue(
            publisher.publish(
                server.url("/").toString(),
                TOKEN,
                requestStillCurrent = { true },
            ) is
                PendingUpNextMovePublicationResult.None,
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun unknownTransportOutcomeAfterPostStartsBecomesAmbiguousAndCannotReplay() = runBlocking {
        server.enqueue(sessionResponse(version = 4, itemIds = listOf(1, 2, 3)))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val failed = publisher.publish(
            server.url("/").toString(),
            TOKEN,
            requestStillCurrent = { true },
        )

        assertTrue(failed is PendingUpNextMovePublicationResult.PostFailed)
        val inFlight = (failed as PendingUpNextMovePublicationResult.PostFailed).inFlight
        assertEquals(PendingUpNextMovePhase.IN_FLIGHT, repository.pendingUpNextSemanticMove()?.phase)
        repository.updatePendingUpNextSemanticMovePhase(inFlight, PendingUpNextMovePhase.AMBIGUOUS)
        assertEquals(PendingUpNextMovePhase.AMBIGUOUS, repository.pendingUpNextSemanticMove()?.phase)
        assertEquals(listOf(1, 3, 2), repository.localUpNextSnapshot()?.itemIds)

        assertTrue(
            publisher.publish(
                server.url("/").toString(),
                TOKEN,
                requestStillCurrent = { true },
            ) is
                PendingUpNextMovePublicationResult.RequiresReconciliation,
        )
        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
        assertEquals("POST", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
    }

    @Test
    fun structureConflictAdoptsServerOrderAndDiscardsMoveWithoutRetry() = runBlocking {
        server.enqueue(sessionResponse(version = 5, itemIds = listOf(1, 2, 3)))
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"up_next_structure_version_conflict","message":"refresh","domain":"structure","expected_version":4,"actual_version":5},"current_session":${sessionJson(5, listOf(1, 2, 3))}}""",
            ),
        )

        val result = publisher.publish(
            server.url("/").toString(),
            TOKEN,
            requestStillCurrent = { true },
        )

        assertTrue(result is PendingUpNextMovePublicationResult.PostFailed)
        val conflict = (result as PendingUpNextMovePublicationResult.PostFailed).error
            as UpNextVersionConflictException
        repository.applyAuthoritativeUpNextSession(
            session = conflict.currentSession,
            ownerKey = OWNER,
            serverIdentity = SERVER_IDENTITY,
            semanticMoveCompletion = true,
            moveDiagnostic = UpNextMoveDiagnostic(outcome = "conflict_discarded"),
        )
        assertEquals(listOf(1, 2, 3), repository.localUpNextSnapshot()?.itemIds)
        assertNull(repository.pendingUpNextSemanticMove())
        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
        assertEquals("POST", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
    }

    @Test
    fun accountOrEndpointChangeAfterPreflightCannotStartPostAndQuarantinesIntent() = runBlocking {
        server.enqueue(sessionResponse(version = 4, itemIds = listOf(1, 2, 3)))

        val result = publisher.publish(
            server.url("/").toString(),
            TOKEN,
            requestStillCurrent = { false },
        )

        assertTrue(result is PendingUpNextMovePublicationResult.Blocked)
        assertEquals(PendingUpNextMovePhase.QUEUED, repository.pendingUpNextSemanticMove()?.phase)
        assertEquals(1, server.requestCount)
        assertEquals("GET", server.takeRequest(1, TimeUnit.SECONDS)!!.method)

        repository.prepareUpNextSyncScope("owner-b", "https://other.example.com")
        assertNull(repository.pendingUpNextSemanticMove())
        assertNull(repository.localUpNextSnapshot())
        assertEquals("owner-b", repository.readUpNextSyncMetadata()?.ownerKey)
        assertEquals("https://other.example.com", repository.readUpNextSyncMetadata()?.serverIdentity)
    }

    private suspend fun stageBacMove() {
        repository.prepareUpNextSyncScope(OWNER, SERVER_IDENTITY)
        repository.applyAuthoritativeUpNextSession(
            session = session(version = 4, itemIds = listOf(1, 2, 3)),
            ownerKey = OWNER,
            serverIdentity = SERVER_IDENTITY,
        )
        val staged = repository.stageUpNextSemanticMove(
            ownerKey = OWNER,
            serverIdentity = SERVER_IDENTITY,
            itemId = 3,
            toPosition = 1,
        )
        assertTrue(staged is StageUpNextSemanticMoveResult.Staged)
    }

    private fun assertOriginalIntent(intent: PendingUpNextSemanticMove?) {
        requireNotNull(intent)
        assertEquals(19L, intent.sessionId)
        assertEquals(4L, intent.expectedStructureVersion)
        assertEquals(3, intent.itemId)
        assertEquals(2, intent.originalPosition)
        assertEquals(1, intent.toPosition)
    }

    private fun sessionResponse(version: Long, itemIds: List<Int>) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"session":${sessionJson(version, itemIds)}}""")

    private fun sessionJson(version: Long, itemIds: List<Int>): String {
        val items = itemIds.mapIndexed { index, itemId ->
            """{"item_id":$itemId,"position":$index,"title":"Item $itemId","url":"https://example.com/$itemId","host":"example.com","status":"ready","has_active_content":true,"created_at":"2026-09-04T10:00:00Z","is_archived":false,"is_muted":false}"""
        }.joinToString(",")
        return """{"version":$version,"structure_version":$version,"session_id":19,"pointer_version":9,"items":[$items],"current_item_id":1,"seed_source_kind":"playlist","seed_source_label":"Reading list","seeded_at":"2026-09-04T10:00:00Z","updated_at":"2026-09-04T10:01:00Z","dirty_since_seed":true}"""
    }

    private fun session(version: Long, itemIds: List<Int>) = UpNextSession(
        version = version,
        structureVersion = version,
        sessionId = 19,
        pointerVersion = 9,
        items = itemIds.mapIndexed { index, itemId ->
            UpNextSessionItem(
                itemId = itemId,
                position = index,
                title = "Item $itemId",
                url = "https://example.com/$itemId",
                host = "example.com",
                status = "ready",
                hasActiveContent = true,
                createdAt = "2026-09-04T10:00:00Z",
                isArchived = false,
                isMuted = false,
            )
        },
        currentItemId = 1,
        seedSourceKind = "playlist",
        seedSourceLabel = "Reading list",
        seededAt = "2026-09-04T10:00:00Z",
        updatedAt = "2026-09-04T10:01:00Z",
        dirtySinceSeed = true,
    )

    private companion object {
        const val OWNER = "owner-a"
        const val SERVER_IDENTITY = "https://reader.example.com"
        const val TOKEN = "device-token"
    }
}
