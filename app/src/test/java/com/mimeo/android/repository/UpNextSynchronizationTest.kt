package com.mimeo.android.repository

import com.mimeo.android.model.UpNextSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextSynchronizationTest {
    private val local = LocalUpNextSnapshot(
        itemIds = listOf(3, 1, 2),
        currentItemId = 1,
        seedSourceKind = "playlist",
        seedSourceLabel = "Reading list",
    )

    private fun server(version: Long = 7) = UpNextSession(
        version = version,
        items = emptyList(),
        currentItemId = null,
        seedSourceKind = "custom",
        seedSourceLabel = "Other device",
        seededAt = "2026-07-17T10:00:00Z",
        updatedAt = "2026-07-17T10:00:00Z",
        dirtySinceSeed = true,
    )

    @Test
    fun firstAdoptionServerAbsentLocalEmptyMarksCleanAbsent() {
        assertTrue(planFirstUpNextAdoption(null, null) is UpNextSyncPlan.MarkCleanAbsent)
    }

    @Test
    fun firstAdoptionServerAbsentLocalPresentCreatesWithNullVersion() {
        val plan = planFirstUpNextAdoption(null, local) as UpNextSyncPlan.Replace
        assertNull(plan.expectedVersion)
        assertEquals(listOf(3, 1, 2), plan.snapshot.itemIds)
        assertEquals("Reading list", plan.snapshot.seedSourceLabel)
    }

    @Test
    fun firstAdoptionServerPresentWinsOverEmptyOrPopulatedLocal() {
        assertEquals(server(), (planFirstUpNextAdoption(server(), null) as UpNextSyncPlan.Adopt).session)
        assertEquals(server(), (planFirstUpNextAdoption(server(), local) as UpNextSyncPlan.Adopt).session)
    }

    @Test
    fun cleanReconnectAdoptsRefreshedServer() {
        val plan = planUpNextReconnect(false, 6, local, server(7)) as UpNextSyncPlan.Adopt
        assertEquals(7L, plan.session?.version)
    }

    @Test
    fun dirtyReconnectReplacesWithExactObservedVersionAndPreservesOrder() {
        val plan = planUpNextReconnect(true, 6, local) as UpNextSyncPlan.Replace
        assertEquals(6L, plan.expectedVersion)
        assertEquals(listOf(3, 1, 2), plan.snapshot.itemIds)
        assertEquals(1, plan.snapshot.currentItemId)
    }

    @Test
    fun dirtyEmptyReconnectClearsWithExactObservedVersion() {
        val plan = planUpNextReconnect(true, 6, null) as UpNextSyncPlan.Clear
        assertEquals(6L, plan.expectedVersion)
    }

    @Test
    fun localCreateThenClearBeforeAcknowledgementDoesNotIssueInvalidClear() {
        assertTrue(planUpNextReconnect(true, null, null) is UpNextSyncPlan.MarkCleanAbsent)
    }

    @Test
    fun reassertingAuthoritativeActiveItemDoesNotCreateAnEchoMutation() {
        assertEquals(false, shouldMutateUpNextActiveItem(currentItemId = 41, requestedItemId = 41))
        assertTrue(shouldMutateUpNextActiveItem(currentItemId = 41, requestedItemId = 42))
    }

    @Test
    fun serverProjectionWithoutActiveItemKeepsAllMembershipUpcoming() {
        val sections = computeNowPlayingSessionSections(
            items = listOf(3, 1, 2),
            currentIndex = -1,
            itemId = { it },
        )
        assertNull(sections.active)
        assertEquals(emptyList<Int>(), sections.earlierInQueue)
        assertEquals(listOf(3, 1, 2), sections.upNext)
    }
}
