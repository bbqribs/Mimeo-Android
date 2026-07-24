package com.mimeo.android.ui.library

import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxParkedSaveProjectionTest {
    @Test
    fun hidesParkedSaveWhoseResolvedItemIdIsPresentServerSide() {
        val parked = parkedSave(id = 1L, resolvedItemId = 42)

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 42, url = "https://example.com/article")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun showsParkedSaveWhoseResolvedItemIdIsAbsentServerSide() {
        val parked = parkedSave(id = 1L, resolvedItemId = 42, url = "https://example.com/other")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 7, url = "https://example.com/article")),
        )

        assertEquals(listOf(parked), projected)
    }

    @Test
    fun hidesParkedSaveMatchingServerUrlIgnoringCaseAndTrailingSlash() {
        val parked = parkedSave(id = 1L, url = "https://Example.com/a/")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 9, url = "https://example.com/a")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun hidesParkedSaveWhoseUrlIsEmbeddedInSharedText() {
        val parked = parkedSave(id = 1L, url = "Great read: https://example.com/a")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 9, url = "https://example.com/a")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun showsParkedSaveWhoseUrlIsAbsentServerSide() {
        val parked = parkedSave(id = 1L, url = "https://example.com/not-yet")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 9, url = "https://example.com/a")),
        )

        assertEquals(listOf(parked), projected)
    }

    @Test
    fun showsTextParkedSaveEvenWhenServerItemHasNoUrl() {
        val parked = parkedSave(
            id = 1L,
            url = "",
            type = PendingManualSaveType.TEXT,
        )

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 9, url = "")),
        )

        assertEquals(listOf(parked), projected)
    }

    @Test
    fun resolvedItemIdTakesPrecedenceOverUrlMismatch() {
        val parked = parkedSave(id = 1L, resolvedItemId = 42, url = "https://example.com/old-url")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 42, url = "https://example.com/canonical")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun hidesParkedSaveMatchingAReadyServerItemNotJustAPendingOne() {
        val parked = parkedSave(id = 1L, url = "https://example.com/a")

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(parked),
            serverItems = listOf(serverItem(itemId = 9, url = "https://example.com/a", status = "ready")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun ordersParkedSavesNewestFirst() {
        val older = parkedSave(id = 1L, url = "https://example.com/older", createdAtMs = 1_000L)
        val newer = parkedSave(id = 2L, url = "https://example.com/newer", createdAtMs = 5_000L)

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(older, newer),
            serverItems = emptyList(),
        )

        assertEquals(listOf(newer, older), projected)
    }

    @Test
    fun emptyParkedListProjectsEmpty() {
        val projected = projectParkedSavesForInbox(
            parkedSaves = emptyList(),
            serverItems = listOf(serverItem(itemId = 9, url = "https://example.com/a")),
        )

        assertEquals(emptyList<PendingManualSaveItem>(), projected)
    }

    @Test
    fun emptyServerListShowsEveryParkedSave() {
        val first = parkedSave(id = 1L, url = "https://example.com/a", createdAtMs = 2_000L)
        val second = parkedSave(id = 2L, url = "https://example.com/b", createdAtMs = 1_000L)

        val projected = projectParkedSavesForInbox(
            parkedSaves = listOf(first, second),
            serverItems = emptyList(),
        )

        assertEquals(listOf(first, second), projected)
    }

    @Test
    fun autoExpandOpensPendingSectionOnlyWhenParkedRowsExist() {
        assertFalse(shouldAutoExpandPending(parkedCount = 0, previouslyExpanded = false))
        assertTrue(shouldAutoExpandPending(parkedCount = 0, previouslyExpanded = true))
        assertTrue(shouldAutoExpandPending(parkedCount = 2, previouslyExpanded = false))
        assertTrue(shouldAutoExpandPending(parkedCount = 2, previouslyExpanded = true))
    }

    private fun parkedSave(
        id: Long,
        resolvedItemId: Int? = null,
        url: String = "https://example.com/article",
        type: PendingManualSaveType = PendingManualSaveType.URL,
        createdAtMs: Long = 1_000L,
    ): PendingManualSaveItem {
        return PendingManualSaveItem(
            id = id,
            source = PendingSaveSource.SHARE,
            type = type,
            urlInput = url,
            destinationPlaylistId = null,
            createdAtMs = createdAtMs,
            lastFailureMessage = "Couldn't reach Mimeo",
            autoRetryEligible = true,
            resolvedItemId = resolvedItemId,
        )
    }

    private fun serverItem(
        itemId: Int,
        url: String,
        status: String = "extracting",
    ): PlaybackQueueItem {
        return PlaybackQueueItem(
            itemId = itemId,
            title = "Example",
            url = url,
            host = "example.com",
            status = status,
            apiProgressPercent = 0,
            apiFurthestPercent = 0,
        )
    }
}
