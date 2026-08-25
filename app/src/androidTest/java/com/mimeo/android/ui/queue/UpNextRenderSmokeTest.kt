package com.mimeo.android.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimeo.android.model.UpNextHistoryEntry
import com.mimeo.android.model.UpNextHistoryProjection
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.ui.theme.MimeoTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Hermetic render assurance for a representative locally owned Up Next state. */
@RunWith(AndroidJUnit4::class)
class UpNextRenderSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun currentAndUpcomingItemsRenderWithoutCrashing() {
        val session = NowPlayingSession(
            items = listOf(
                sessionItem(itemId = 1, title = "Current assurance article"),
                sessionItem(itemId = 2, title = "Upcoming assurance article"),
            ),
            historyItems = listOf(
                sessionItem(itemId = 3, title = "Visible history assurance article"),
            ),
            currentIndex = 0,
            updatedAt = 1L,
            sourcePlaylistId = null,
        )

        composeTestRule.setContent {
            MimeoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    NowPlayingSessionPanel(
                        session = session,
                        historyProjection = null,
                        seededFromLabel = "CI assurance fixture",
                        onOpenItem = {},
                        onJumpToQueueItem = {},
                        onJumpToHistoryItem = {},
                        onReorderItem = { _, _ -> },
                        onRemoveItem = {},
                        onClearUpcoming = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Current assurance article").assertIsDisplayed()
        composeTestRule.onNodeWithText("Visible history assurance article").assertExists()
        composeTestRule.onNodeWithText("Up Next · 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming assurance article").assertIsDisplayed()
    }

    @Test
    fun canonicalHistoryRendersWithoutAnActiveSession() {
        val projection = UpNextHistoryProjection(
            entries = listOf(
                historyEntry(7, "First occurrence", "2026-08-24T10:00:00Z", stillInSession = true),
                historyEntry(7, "Repeated occurrence", "2026-08-24T11:00:00Z", stillInSession = false),
            ),
            hasMore = true,
            recordingSince = "2026-08-24T00:00:00Z",
        )

        composeTestRule.setContent {
            MimeoTheme {
                UpNextHistoryOnlyPanel(
                    historyProjection = projection,
                    onOpenItem = {},
                    onArchiveItem = {},
                    onUnarchiveItem = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithText("History · 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("First occurrence").assertExists()
        composeTestRule.onNodeWithText("Repeated occurrence").assertExists()
        composeTestRule.onNodeWithText("Only the 50 most recent History entries are shown.").assertExists()
        composeTestRule.onNodeWithText("No active session. Open an item to start one.").assertExists()

        composeTestRule.onNodeWithContentDescription("More actions for First occurrence").performClick()
        composeTestRule.onNodeWithText("Archive").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move to Bin").assertDoesNotExist()
    }

    @Test
    fun historyLongPressSelectsRepeatedArticleOccurrencesForBatchArchive() {
        var archivedIds = emptySet<Int>()
        val projection = UpNextHistoryProjection(
            entries = listOf(
                historyEntry(7, "First selected occurrence", "2026-08-24T10:00:00Z", stillInSession = true),
                historyEntry(7, "Repeated selected occurrence", "2026-08-24T11:00:00Z", stillInSession = false),
            ),
            hasMore = false,
            recordingSince = "2026-08-24T00:00:00Z",
        )

        composeTestRule.setContent {
            MimeoTheme {
                UpNextHistoryOnlyPanel(
                    historyProjection = projection,
                    onOpenItem = {},
                    onArchiveItem = {},
                    onUnarchiveItem = {},
                    onBatchArchiveItems = { archivedIds = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeTestRule.onNodeWithText("First selected occurrence")
            .performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Selected").assertCountEquals(2)
        composeTestRule.onNodeWithContentDescription("Archive selected").assertIsEnabled().performClick()
        composeTestRule.runOnIdle { assertEquals(setOf(7), archivedIds) }
    }

    @Test
    fun earlierRowKeepsOverflowAndAlsoEntersBatchSelection() {
        val session = NowPlayingSession(
            items = listOf(
                sessionItem(itemId = 11, title = "Earlier action assurance"),
                sessionItem(itemId = 12, title = "Current action assurance"),
            ),
            historyItems = emptyList(),
            currentIndex = 1,
            updatedAt = 1L,
            sourcePlaylistId = null,
        )

        composeTestRule.setContent {
            MimeoTheme {
                NowPlayingSessionPanel(
                    session = session,
                    historyProjection = UpNextHistoryProjection(
                        entries = emptyList(),
                        hasMore = false,
                        recordingSince = "2026-08-24T00:00:00Z",
                    ),
                    seededFromLabel = "CI assurance fixture",
                    onOpenItem = {},
                    onJumpToQueueItem = {},
                    onJumpToHistoryItem = {},
                    onReorderItem = { _, _ -> },
                    onRemoveItem = {},
                    onClearUpcoming = {},
                    onBinSessionEarlierItem = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Earlier action assurance").performScrollTo()
        composeTestRule.onNodeWithContentDescription("More actions for Earlier action assurance").performClick()
        composeTestRule.onNodeWithText("Move to Bin").assertIsDisplayed()
        composeTestRule.onNodeWithText("Move to Bin").performClick()

        composeTestRule.onNodeWithText("Earlier action assurance")
            .performTouchInput { longClick() }
        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Archive selected").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Unarchive selected").assertIsNotEnabled()
    }

    private fun sessionItem(itemId: Int, title: String) = NowPlayingSessionItem(
        itemId = itemId,
        title = title,
        url = "https://example.invalid/$itemId",
        host = "example.invalid",
        sourceType = null,
        sourceLabel = null,
        sourceUrl = null,
        captureKind = null,
        sourceAppPackage = null,
        status = "processed",
        activeContentVersionId = itemId,
        lastReadPercent = 0,
        chunkIndex = 0,
        offsetInChunkChars = 0,
        readerScrollOffset = 0,
    )

    private fun historyEntry(
        itemId: Int,
        title: String,
        playedAt: String,
        stillInSession: Boolean,
    ) = UpNextHistoryEntry(
        itemId = itemId,
        playedAt = playedAt,
        title = title,
        url = "https://example.invalid/$itemId",
        host = "example.invalid",
        status = "ready",
        hasActiveContent = true,
        createdAt = "2026-08-24T09:00:00Z",
        isArchived = false,
        isMuted = false,
        stillInSession = stillInSession,
    )
}
