package com.mimeo.android.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.model.PlaybackQueueItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The session writes the playback-commit reconciler and explicit Play Now stand on.
 *
 * The classifiers decide *which* of these runs (see LivePlaybackSessionSyncTest and
 * ReaderPlaySessionOwnerTest); these assert what each one actually does to Up Next.
 */
@RunWith(RobolectricTestRunner::class)
class SessionPointerPromotionTest {
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
    fun playingTheCurrentItemLeavesThePointerAndOrderUnchanged() = runBlocking {
        val session = startSession(listOf(1, 2, 3), startItemId = 2)
        assertEquals(2, session.currentItem?.itemId)

        val moved = repository.moveCurrentIndex(targetIndex = 1, priorActiveToHistory = true)!!

        assertEquals(listOf(1, 2, 3), moved.items.map { it.itemId })
        assertEquals(2, moved.currentItem?.itemId)
    }

    @Test
    fun playingAnUpcomingItemMovesThePointerToItAndDisplacesThePriorActive() = runBlocking {
        startSession(listOf(1, 2, 3), startItemId = 1)

        val moved = repository.moveCurrentIndex(targetIndex = 2, priorActiveToHistory = true)!!

        assertEquals(3, moved.currentItem?.itemId)
        // The prior active item left the queue for History rather than staying upcoming.
        assertEquals(listOf(2, 3), moved.items.map { it.itemId })
    }

    @Test
    fun replayingAnEarlierItemMovesThePointerBackwards() = runBlocking {
        startSession(listOf(1, 2, 3), startItemId = 3)

        val moved = repository.moveCurrentIndex(targetIndex = 0, priorActiveToHistory = false)!!

        assertEquals(1, moved.currentItem?.itemId)
        // Nothing was displaced: the established policy only banks a prior active item that
        // was actually listened to (shouldPlacePriorActiveInHistory, asserted separately).
        assertEquals(listOf(1, 2, 3), moved.items.map { it.itemId })
    }

    @Test
    fun replayingAnEarlierItemBanksThePriorActiveWhenPolicySaysSo() = runBlocking {
        startSession(listOf(1, 2, 3), startItemId = 3)

        val moved = repository.moveCurrentIndex(targetIndex = 0, priorActiveToHistory = true)!!

        assertEquals(1, moved.currentItem?.itemId)
        assertEquals(listOf(1, 2), moved.items.map { it.itemId })
    }

    @Test
    fun playingAHistoryItemRestoresItAsCurrentExactlyOnce() = runBlocking {
        val session = startSession(listOf(1, 2, 3), startItemId = 1)
        val banked = session.currentItem!!
        repository.moveCurrentIndex(targetIndex = 2, priorActiveToHistory = true)

        val restored = repository.insertTransientHistoryItemAsCurrent(banked)!!

        assertEquals(1, restored.currentItem?.itemId)
        assertEquals(listOf(2, 1, 3), restored.items.map { it.itemId })

        // Engine state emits repeatedly while the restored item plays. A second restore for
        // the same item must not insert it twice or shift the pointer again.
        val repeated = repository.insertTransientHistoryItemAsCurrent(banked)!!

        assertEquals(1, repeated.currentItem?.itemId)
        assertEquals(listOf(2, 1, 3), repeated.items.map { it.itemId })
    }

    @Test
    fun explicitPlayNowAdoptsAnItemFromOutsideTheSession() = runBlocking {
        startSession(listOf(1, 2, 3), startItemId = 1)

        val adopted = repository.playNowInSession(queueItem(99))

        assertEquals(99, adopted.currentItem?.itemId)
        assertEquals(listOf(99, 1, 2, 3), adopted.items.map { it.itemId })
    }

    @Test
    fun explicitPlayNowStartsASessionWhenThereIsNone() = runBlocking {
        val adopted = repository.playNowInSession(queueItem(99))

        assertEquals(99, adopted.currentItem?.itemId)
        assertEquals(listOf(99), adopted.items.map { it.itemId })
    }

    @Test
    fun explicitPlayNowOnTheAlreadyCurrentItemChangesNothing() = runBlocking {
        startSession(listOf(1, 2, 3), startItemId = 2)

        val adopted = repository.playNowInSession(queueItem(2))

        assertEquals(2, adopted.currentItem?.itemId)
        assertEquals(listOf(1, 2, 3), adopted.items.map { it.itemId })
    }

    private suspend fun startSession(itemIds: List<Int>, startItemId: Int) =
        repository.startSession(
            queueItems = itemIds.map(::queueItem),
            startItemId = startItemId,
            sourcePlaylistId = null,
        )

    private fun queueItem(id: Int) = PlaybackQueueItem(
        itemId = id,
        title = "Item $id",
        url = "https://example.com/$id",
        sourceLabel = "Source $id",
    )
}
