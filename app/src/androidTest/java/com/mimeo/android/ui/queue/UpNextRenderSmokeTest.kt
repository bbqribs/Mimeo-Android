package com.mimeo.android.ui.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.ui.theme.MimeoTheme
import org.junit.Rule
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
            currentIndex = 0,
            updatedAt = 1L,
            sourcePlaylistId = null,
        )

        composeTestRule.setContent {
            MimeoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    NowPlayingSessionPanel(
                        session = session,
                        seededFromLabel = "CI assurance fixture",
                        onOpenItem = {},
                        onJumpToQueueItem = {},
                        onReorderItem = { _, _ -> },
                        onRemoveItem = {},
                        onClearUpcoming = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Current assurance article").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up Next · 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Upcoming assurance article").assertIsDisplayed()
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
}
