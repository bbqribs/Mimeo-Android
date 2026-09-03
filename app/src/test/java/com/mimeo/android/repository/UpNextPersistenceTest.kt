package com.mimeo.android.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.entities.NowPlayingEntity
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.UpNextSession
import com.mimeo.android.model.UpNextSessionItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpNextPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PlaybackRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaybackRepository(ApiClient(), database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dirtyVersionAndSourceSurviveRepositoryRecreation() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(server(version = 4), "owner-a", "https://reader.example.com")
        repository.startSession(
            queueItems = listOf(queueItem(2), queueItem(1)),
            startItemId = 2,
            sourcePlaylistId = 9,
            seedSourceKind = "playlist",
            seedSourceLabel = "Reading list",
        )
        repository.markUpNextDirty()

        val restarted = PlaybackRepository(ApiClient(), database, context)
        val metadata = restarted.readUpNextSyncMetadata()!!
        val snapshot = restarted.localUpNextSnapshot()!!

        assertEquals(4L, metadata.serverVersion)
        assertEquals(4L, metadata.serverStructureVersion)
        assertTrue(metadata.dirty)
        assertEquals(listOf(2, 1), snapshot.itemIds)
        assertEquals(2, snapshot.currentItemId)
        assertEquals("playlist", snapshot.seedSourceKind)
        assertEquals("Reading list", snapshot.seedSourceLabel)
    }

    @Test
    fun oneOfflineSemanticMovePersistsOriginalStructureIntentAndBlocksASecondMove() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(version = 4, pointerVersion = 9, itemIds = listOf(1, 2, 3), currentItemId = 1),
            "owner-a",
            "https://reader.example.com",
        )

        val staged = repository.stageUpNextSemanticMove(
            ownerKey = "owner-a",
            serverIdentity = "https://reader.example.com",
            itemId = 3,
            toPosition = 1,
        ) as StageUpNextSemanticMoveResult.Staged
        assertEquals(listOf(1, 3, 2), staged.session.items.map { it.itemId })
        assertEquals(1, staged.session.currentItem?.itemId)
        assertFalse(repository.readUpNextSyncMetadata()!!.dirty)

        val restarted = PlaybackRepository(ApiClient(), database, context)
        val pending = restarted.pendingUpNextSemanticMove()!!
        assertEquals(19L, pending.sessionId)
        assertEquals(4L, pending.expectedStructureVersion)
        assertEquals(3, pending.itemId)
        assertEquals(2, pending.originalPosition)
        assertEquals(1, pending.toPosition)
        assertEquals(PendingUpNextMovePhase.QUEUED, pending.phase)
        assertTrue(
            restarted.stageUpNextSemanticMove(
                ownerKey = "owner-a",
                serverIdentity = "https://reader.example.com",
                itemId = 2,
                toPosition = 1,
            ) is StageUpNextSemanticMoveResult.PendingIntentExists,
        )

        val inFlight = restarted.updatePendingUpNextSemanticMovePhase(
            pending,
            PendingUpNextMovePhase.IN_FLIGHT,
        )!!
        assertEquals(PendingUpNextMovePhase.IN_FLIGHT, inFlight.phase)
        assertEquals(
            PendingUpNextMovePhase.IN_FLIGHT,
            PlaybackRepository(ApiClient(), database, context).pendingUpNextSemanticMove()?.phase,
        )
    }

    @Test
    fun semanticMoveResultAtomicallyAdoptsOrderWithoutRegressingPointerProgressOrProvenance() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(version = 4, pointerVersion = 7, itemIds = listOf(1, 2, 3, 4), currentItemId = 1),
            "owner-a",
            "https://reader.example.com",
        )
        val pointer = repository.enqueueUpNextPointerTransition(1, 2)!!
        repository.setCurrentIndex(1)
        repository.setNowPlayingItemProgress(2, 64)
        assertTrue(
            repository.acknowledgeUpNextPointerTransition(
                pointer,
                server(version = 4, pointerVersion = 8, itemIds = listOf(1, 2, 3, 4), currentItemId = 2),
            ),
        )
        val stagedMove = repository.stageUpNextSemanticMove(
            ownerKey = "owner-a",
            serverIdentity = "https://reader.example.com",
            itemId = 4,
            toPosition = 2,
        ) as StageUpNextSemanticMoveResult.Staged
        assertEquals(4L, stagedMove.intent.expectedStructureVersion)
        assertEquals(8L, repository.readUpNextSyncMetadata()!!.serverPointerVersion)

        val applied = repository.applyAuthoritativeUpNextSession(
            session = server(
                version = 5,
                pointerVersion = 7,
                itemIds = listOf(1, 2, 4, 3),
                currentItemId = 1,
            ),
            ownerKey = "owner-a",
            serverIdentity = "https://reader.example.com",
            semanticMoveCompletion = true,
            moveDiagnostic = UpNextMoveDiagnostic(outcome = "applied"),
        )!!

        assertEquals(listOf(1, 2, 4, 3), applied.items.map { it.itemId })
        assertEquals(2, applied.currentItem?.itemId)
        assertEquals(64, applied.currentItem?.lastReadPercent)
        assertEquals("Reading list", applied.seedSourceLabel)
        val metadata = repository.readUpNextSyncMetadata()!!
        assertEquals(5L, metadata.serverStructureVersion)
        assertEquals(8L, metadata.serverPointerVersion)
        assertFalse(metadata.dirty)
        assertNull(repository.pendingUpNextSemanticMove())
        assertTrue(repository.pendingUpNextPointerTransitions().isEmpty())
    }

    @Test
    fun rejectedSemanticMoveRollsBackDurableOrderAndKeepsSanitizedDiagnosticOnly() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(version = 4, itemIds = listOf(1, 2, 3), currentItemId = 1),
            "owner-a",
            "https://reader.example.com",
        )
        repository.stageUpNextSemanticMove("owner-a", "https://reader.example.com", 3, 1)

        val rolledBack = repository.rollbackPendingUpNextSemanticMove(
            UpNextMoveDiagnostic(outcome = "rejected", code = "http_400"),
        )!!

        assertEquals(listOf(1, 2, 3), rolledBack.items.map { it.itemId })
        assertNull(repository.pendingUpNextSemanticMove())
        val diagnostic = repository.readUpNextSyncMetadata()!!.lastSemanticMoveDiagnosticJson
        assertTrue(diagnostic.contains("rejected"))
        assertTrue(diagnostic.contains("http_400"))
        assertFalse(diagnostic.contains("example.com"))
    }

    @Test
    fun unsupportedActiveMissingAndForeignMoveTargetsNeverMutateDurableSession() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(version = 4, itemIds = listOf(1, 2, 3), currentItemId = 1),
            "owner-a",
            "https://reader.example.com",
        )
        val original = repository.getSession()!!.items.map { it.itemId }

        assertTrue(
            repository.stageUpNextSemanticMove(
                "owner-a",
                "https://reader.example.com",
                itemId = 1,
                toPosition = 2,
            ) is StageUpNextSemanticMoveResult.InvalidTarget,
        )
        assertTrue(
            repository.stageUpNextSemanticMove(
                "owner-a",
                "https://reader.example.com",
                itemId = 99,
                toPosition = 2,
            ) is StageUpNextSemanticMoveResult.InvalidTarget,
        )
        assertTrue(
            repository.stageUpNextSemanticMove(
                "owner-b",
                "https://reader.example.com",
                itemId = 3,
                toPosition = 1,
            ) is StageUpNextSemanticMoveResult.MissingServerContext,
        )
        assertEquals(original, repository.getSession()!!.items.map { it.itemId })
        assertNull(repository.pendingUpNextSemanticMove())

        repository.markUpNextSemanticMoveUnsupported(UpNextMoveDiagnostic(outcome = "unsupported"))
        assertTrue(
            repository.stageUpNextSemanticMove(
                "owner-a",
                "https://reader.example.com",
                itemId = 3,
                toPosition = 1,
            ) is StageUpNextSemanticMoveResult.Unsupported,
        )
        assertEquals(original, repository.getSession()!!.items.map { it.itemId })
        assertNull(repository.pendingUpNextSemanticMove())
    }

    @Test
    fun pendingPointerOccurrencesSurviveRestartAndAcknowledgeInOrder() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://reader.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(
                version = 4,
                pointerVersion = 7,
                itemIds = listOf(1, 2),
                currentItemId = 1,
            ),
            "owner-a",
            "https://reader.example.com",
        )

        val first = repository.enqueueUpNextPointerTransition(fromItemId = 1, toItemId = 2)!!
        val second = repository.enqueueUpNextPointerTransition(fromItemId = 2, toItemId = 1)!!
        val restarted = PlaybackRepository(ApiClient(), database, context)

        assertEquals(listOf(1, 2), restarted.pendingUpNextPointerTransitions().map { it.fromItemId })
        assertEquals(listOf(7L, 8L), restarted.pendingUpNextPointerTransitions().map { it.expectedPointerVersion })
        assertTrue(
            restarted.acknowledgeUpNextPointerTransition(
                first,
                server(
                    version = 4,
                    pointerVersion = 8,
                    itemIds = listOf(1, 2),
                    currentItemId = 2,
                ),
            ),
        )
        assertEquals(listOf(second), restarted.pendingUpNextPointerTransitions())
        assertEquals(8L, restarted.readUpNextSyncMetadata()!!.serverPointerVersion)
    }

    @Test
    fun accountSwitchEndpointSwitchAndSignOutClearContinuityState() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://one.example.com")
        repository.applyAuthoritativeUpNextSession(
            server(version = 4, itemIds = listOf(1, 2, 3), currentItemId = 1),
            "owner-a",
            "https://one.example.com",
        )
        repository.stageUpNextSemanticMove("owner-a", "https://one.example.com", 3, 1)

        repository.prepareUpNextSyncScope("owner-b", "https://one.example.com")
        assertNull(repository.getSession())
        assertFalse(repository.readUpNextSyncMetadata()!!.dirty)
        assertTrue(repository.pendingUpNextPointerTransitions().isEmpty())
        assertNull(repository.pendingUpNextSemanticMove())
        assertEquals("owner-b", repository.readUpNextSyncMetadata()!!.ownerKey)

        repository.startSession(listOf(queueItem(2)), 2, null)
        repository.markUpNextDirty()
        repository.prepareUpNextSyncScope("owner-b", "https://two.example.com")
        assertNull(repository.getSession())
        assertEquals("https://two.example.com", repository.readUpNextSyncMetadata()!!.serverIdentity)

        repository.clearAccountScopedLocalState()
        assertNull(repository.readUpNextSyncMetadata())
        assertNull(repository.getSession())
    }

    @Test
    fun authoritativeProjectionPreservesOrderDeduplicatesAndHandlesActiveRemoval() = runBlocking {
        val first = server(
            version = 4,
            itemIds = listOf(3, 1, 2),
            currentItemId = 1,
            archivedIds = setOf(3),
        )
        val adopted = repository.applyAuthoritativeUpNextSession(
            first,
            "owner-a",
            "https://reader.example.com",
        )!!
        assertEquals(listOf(3, 1, 2), adopted.items.map { it.itemId })
        assertEquals(1, adopted.currentItem?.itemId)
        assertTrue(adopted.items.first { it.itemId == 3 }.isArchived)
        assertEquals("Reading list", adopted.seedSourceLabel)

        val activeRemoved = server(
            version = 5,
            itemIds = listOf(3, 2),
            currentItemId = null,
            archivedIds = setOf(3),
        )
        val compacted = repository.applyAuthoritativeUpNextSession(
            activeRemoved,
            "owner-a",
            "https://reader.example.com",
        )!!
        assertEquals(listOf(3, 2), compacted.items.map { it.itemId })
        assertNull(compacted.currentItem)
        assertEquals(-1, compacted.currentIndex)
        assertEquals(5L, repository.readUpNextSyncMetadata()!!.serverVersion)
        assertFalse(repository.readUpNextSyncMetadata()!!.dirty)
    }

    @Test
    fun legacyPersistedHistoryIsDroppedAndNeverEntersTheContinuitySnapshot() = runBlocking {
        database.nowPlayingDao().upsert(
            NowPlayingEntity(
                queueJson = """{
                    "items":[{"itemId":1,"url":"https://example.com/1"}],
                    "historyItems":[{"itemId":9,"url":"https://example.com/9"}]
                }""",
                currentIndex = 0,
                updatedAt = 1L,
            ),
        )

        val restored = repository.getSession()!!
        assertEquals(listOf(1), restored.items.map { it.itemId })
        assertTrue(restored.historyItems.isEmpty())

        repository.setCurrentIndex(0)
        assertFalse(database.nowPlayingDao().getSession()!!.queueJson.contains("historyItems"))
        assertEquals(listOf(1), repository.localUpNextSnapshot()!!.itemIds)
    }

    @Test
    fun archivedMembershipRemainsWhileUnavailableReferencesDisappear() = runBlocking {
        val adopted = repository.applyAuthoritativeUpNextSession(
            server(version = 8, itemIds = listOf(7, 8), currentItemId = 7, archivedIds = setOf(8)),
            "owner-a",
            "https://reader.example.com",
        )!!
        assertEquals(listOf(7, 8), adopted.items.map { it.itemId })

        val projectedAfterPurge = repository.applyAuthoritativeUpNextSession(
            server(version = 9, itemIds = listOf(8), currentItemId = null, archivedIds = setOf(8)),
            "owner-a",
            "https://reader.example.com",
        )!!
        assertEquals(listOf(8), projectedAfterPurge.items.map { it.itemId })
        assertNull(projectedAfterPurge.currentItem)
    }

    @Test
    fun archivedCurrentRetainsProgressAndOwnerAcrossArchiveRefreshAndUnarchive() = runBlocking {
        repository.startSession(listOf(queueItem(7), queueItem(8)), startItemId = 7, sourcePlaylistId = null)

        val archived = repository.setNowPlayingItemArchived(itemId = 7, archived = true)!!
        assertEquals(7, archived.currentItem?.itemId)
        assertTrue(archived.currentItem!!.isArchived)

        val progressed = repository.setNowPlayingItemProgress(itemId = 7, percent = 64)!!
        assertEquals(64, progressed.currentItem?.lastReadPercent)
        assertTrue(progressed.currentItem!!.isArchived)

        val refreshed = repository.applyAuthoritativeUpNextSession(
            server(version = 10, itemIds = listOf(7, 8), currentItemId = 7, archivedIds = setOf(7)),
            "owner-a",
            "https://reader.example.com",
        )!!
        assertEquals(7, refreshed.currentItem?.itemId)
        assertTrue(refreshed.currentItem!!.isArchived)
        assertEquals(64, refreshed.currentItem?.lastReadPercent)

        val unarchived = repository.setNowPlayingItemArchived(itemId = 7, archived = false)!!
        assertEquals(7, unarchived.currentItem?.itemId)
        assertFalse(unarchived.currentItem!!.isArchived)
        assertEquals(64, unarchived.currentItem?.lastReadPercent)
    }

    @Test
    fun archiveRefreshForOffSessionItemCannotReplaceSessionMembership() = runBlocking {
        repository.startSession(
            listOf(queueItem(7), queueItem(8), queueItem(9)),
            startItemId = 7,
            sourcePlaylistId = null,
        )
        repository.setNowPlayingItemProgress(itemId = 7, percent = 64)

        val unchanged = repository.setNowPlayingItemArchived(itemId = 99, archived = false)!!

        assertEquals(listOf(7, 8, 9), unchanged.items.map { it.itemId })
        assertEquals(7, unchanged.currentItem?.itemId)
        assertEquals(64, unchanged.currentItem?.lastReadPercent)
    }

    @Test
    fun removingArchivedUpcomingMembershipPreservesCurrentProgressAndNextEligibleOrder() = runBlocking {
        repository.startSession(
            listOf(queueItem(7), queueItem(8), queueItem(9)),
            startItemId = 7,
            sourcePlaylistId = null,
        )
        repository.setNowPlayingItemProgress(itemId = 7, percent = 64)
        repository.setNowPlayingItemArchived(itemId = 8, archived = true)

        val remaining = repository.removeItemFromSession(itemId = 8)!!

        assertEquals(listOf(7, 9), remaining.items.map { it.itemId })
        assertEquals(7, remaining.currentItem?.itemId)
        assertEquals(64, remaining.currentItem?.lastReadPercent)
    }

    @Test
    fun sessionCanEndWithNoActiveItemWithoutDiscardingRemainingMembership() = runBlocking {
        repository.startSession(listOf(queueItem(7), queueItem(8)), startItemId = 7, sourcePlaylistId = null)

        val noActive = repository.setCurrentIndex(-1)!!

        assertNull(noActive.currentItem)
        assertEquals(-1, noActive.currentIndex)
        assertEquals(listOf(7, 8), noActive.items.map { it.itemId })
    }

    private fun queueItem(id: Int) = PlaybackQueueItem(
        itemId = id,
        title = "Item $id",
        url = "https://example.com/$id",
        sourceLabel = "Source $id",
    )

    private fun server(
        version: Long,
        pointerVersion: Long = version,
        itemIds: List<Int> = listOf(1),
        currentItemId: Int? = itemIds.firstOrNull(),
        archivedIds: Set<Int> = emptySet(),
    ) = UpNextSession(
        version = version,
        sessionId = 19,
        pointerVersion = pointerVersion,
        items = itemIds.distinct().mapIndexed { index, id ->
            UpNextSessionItem(
                itemId = id,
                position = index,
                title = "Item $id",
                url = "https://example.com/$id",
                host = "example.com",
                status = "ready",
                hasActiveContent = true,
                createdAt = "2026-07-17T10:00:00Z",
                archivedAt = if (id in archivedIds) "2026-07-17T11:00:00Z" else null,
                isArchived = id in archivedIds,
                isMuted = id in archivedIds,
            )
        },
        currentItemId = currentItemId,
        seedSourceKind = "playlist",
        seedSourceLabel = "Reading list",
        seededAt = "2026-07-17T10:00:00Z",
        updatedAt = "2026-07-17T11:00:00Z",
        dirtySinceSeed = true,
    )
}
