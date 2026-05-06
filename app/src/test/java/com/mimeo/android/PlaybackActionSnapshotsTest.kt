package com.mimeo.android

import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActionSnapshotsTest {

    @Test
    fun playAllSnapshotPreservesVisibleOrderAndDedupes() {
        val snapshot = playbackActionSnapshot(
            listOf(item(3), item(1), item(2), item(1)),
        )

        assertEquals(listOf(3, 1, 2), snapshot.map { it.itemId })
    }

    @Test
    fun playFromHereStartsAtSelectedItemAndQueuesBelow() {
        val snapshot = playbackActionSnapshotFromHere(
            sourceItems = listOf(item(10), item(20), item(30), item(40)),
            selectedItemId = 30,
        )

        assertEquals(listOf(30, 40), snapshot.map { it.itemId })
    }

    @Test
    fun playFromHereReturnsEmptySnapshotWhenSelectedItemIsNotVisible() {
        val snapshot = playbackActionSnapshotFromHere(
            sourceItems = listOf(item(10), item(20)),
            selectedItemId = 99,
        )

        assertTrue(snapshot.isEmpty())
    }

    private fun item(itemId: Int): PlaybackQueueItem =
        PlaybackQueueItem(
            itemId = itemId,
            url = "https://example.com/$itemId",
        )
}
