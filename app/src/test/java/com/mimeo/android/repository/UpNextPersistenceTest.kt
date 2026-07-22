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
        assertTrue(metadata.dirty)
        assertEquals(listOf(2, 1), snapshot.itemIds)
        assertEquals(2, snapshot.currentItemId)
        assertEquals("playlist", snapshot.seedSourceKind)
        assertEquals("Reading list", snapshot.seedSourceLabel)
    }

    @Test
    fun accountSwitchEndpointSwitchAndSignOutClearContinuityState() = runBlocking {
        repository.prepareUpNextSyncScope("owner-a", "https://one.example.com")
        repository.startSession(listOf(queueItem(1)), 1, null)
        repository.markUpNextDirty()

        repository.prepareUpNextSyncScope("owner-b", "https://one.example.com")
        assertNull(repository.getSession())
        assertFalse(repository.readUpNextSyncMetadata()!!.dirty)
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

    private fun queueItem(id: Int) = PlaybackQueueItem(
        itemId = id,
        title = "Item $id",
        url = "https://example.com/$id",
        sourceLabel = "Source $id",
    )

    private fun server(
        version: Long,
        itemIds: List<Int> = listOf(1),
        currentItemId: Int? = itemIds.firstOrNull(),
        archivedIds: Set<Int> = emptySet(),
    ) = UpNextSession(
        version = version,
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
