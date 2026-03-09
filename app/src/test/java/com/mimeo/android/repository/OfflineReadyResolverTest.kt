package com.mimeo.android.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineReadyResolverTest {
    @Test
    fun `marks candidate offline-ready when item id is cached`() {
        val candidates = listOf(
            OfflineReadyCandidate(itemId = 101, activeContentVersionId = 12),
            OfflineReadyCandidate(itemId = 202, activeContentVersionId = 88),
        )

        val result = resolveOfflineReadyItemIds(
            candidates = candidates,
            cachedItemIds = setOf(202),
            cachedActiveContentVersionIds = emptySet(),
        )

        assertEquals(setOf(202), result)
    }

    @Test
    fun `marks candidate offline-ready when active content version is cached`() {
        val candidates = listOf(
            OfflineReadyCandidate(itemId = 3001, activeContentVersionId = 77),
            OfflineReadyCandidate(itemId = 9002, activeContentVersionId = 77),
            OfflineReadyCandidate(itemId = 5003, activeContentVersionId = 11),
        )

        val result = resolveOfflineReadyItemIds(
            candidates = candidates,
            cachedItemIds = emptySet(),
            cachedActiveContentVersionIds = setOf(77),
        )

        assertEquals(setOf(3001, 9002), result)
    }

    @Test
    fun `does not mark when neither id nor content version is cached`() {
        val candidates = listOf(
            OfflineReadyCandidate(itemId = 1, activeContentVersionId = 10),
            OfflineReadyCandidate(itemId = 2, activeContentVersionId = null),
        )

        val result = resolveOfflineReadyItemIds(
            candidates = candidates,
            cachedItemIds = setOf(99),
            cachedActiveContentVersionIds = setOf(55),
        )

        assertEquals(emptySet<Int>(), result)
    }
}
