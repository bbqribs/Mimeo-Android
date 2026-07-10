package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.PendingItemActionType
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.StartupDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreLocalStateOwnershipTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking {
            store.clearAllSettingsForTesting()
            store.saveTokenOnly("")
        }
    }

    @Test
    fun ownerMatch_keepsAccountScopedState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "alice-token",
            accountIdentity = "Alice",
        )
        seedAccountScopedState()

        val expectedOwner = store.localStateOwnerKeyFor(
            baseUrl = "https://reader.example.com",
            accountIdentity = "alice",
        )

        assertEquals(expectedOwner, store.readLocalStateOwner())
        assertNotNull(store.loadQueueSnapshot(7))
        assertEquals(1, store.pendingManualSavesFlow.first().size)
        assertEquals(1, store.pendingItemActionsFlow.first().size)
        assertEquals(1, store.playbackSegmentIndexByItemFlow.first().size)
    }

    @Test
    fun ownerKey_hashesAccountAndBaseUrlWithoutRawIdentity() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://Reader.Example.com/",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "alice-token",
            accountIdentity = "Alice",
        )

        val owner = store.readLocalStateOwner()

        assertTrue(owner.startsWith("v1:"))
        assertFalse(owner.contains("Alice", ignoreCase = true))
        assertFalse(owner.contains("reader.example.com", ignoreCase = true))
    }

    @Test
    fun ownerMismatch_clearsAccountScopedStateAndPreservesNonAccountSettings() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "alice-token",
            accountIdentity = "alice",
        )
        store.saveStartupDestination(StartupDestination.INBOX)
        seedAccountScopedState()

        val aliceOwner = store.readLocalStateOwner()
        val pilotOwner = store.localStateOwnerKeyFor(
            baseUrl = "https://reader.example.com",
            accountIdentity = "pilot-user",
        )
        assertNotEquals(aliceOwner, pilotOwner)

        store.clearAccountScopedDataStoreState(clearOwner = false)
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "pilot-token",
            accountIdentity = "pilot-user",
        )

        val settings = store.settingsFlow.first()
        assertEquals(pilotOwner, store.readLocalStateOwner())
        assertNull(store.loadQueueSnapshot(7))
        assertTrue(store.pendingManualSavesFlow.first().isEmpty())
        assertTrue(store.pendingItemActionsFlow.first().isEmpty())
        assertTrue(store.playbackSegmentIndexByItemFlow.first().isEmpty())
        assertNull(settings.selectedPlaylistId)
        assertNull(settings.defaultSavePlaylistId)
        assertEquals(StartupDestination.INBOX, settings.startupDestination)
        assertEquals("https://reader.example.com", settings.baseUrl)
    }

    @Test
    fun signOutStyleClear_removesOwnerAndAccountStateButPreservesEndpoint() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "alice-token",
            accountIdentity = "alice",
        )
        seedAccountScopedState()

        store.clearAccountScopedDataStoreState(clearOwner = true)
        store.saveTokenOnly("")

        val settings = store.settingsFlow.first()
        assertEquals("", store.readLocalStateOwner())
        assertEquals("", settings.apiToken)
        assertEquals("https://reader.example.com", settings.baseUrl)
        assertNull(store.loadQueueSnapshot(7))
        assertTrue(store.pendingManualSavesFlow.first().isEmpty())
        assertTrue(store.pendingItemActionsFlow.first().isEmpty())
        assertTrue(store.playbackSegmentIndexByItemFlow.first().isEmpty())
        assertNull(settings.selectedPlaylistId)
        assertNull(settings.defaultSavePlaylistId)
    }

    @Test
    fun sameUserRestart_preservesOwnerAndAccountScopedState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "alice-token",
            accountIdentity = "alice",
        )
        seedAccountScopedState()
        val ownerBeforeRestart = store.readLocalStateOwner()

        // A fresh SettingsStore instance over the same Context/DataStore file simulates an app
        // process restart: nothing about local-state ownership should change without a sign-out
        // or account switch actually occurring in between.
        val restartedStore = SettingsStore(context)

        assertEquals(ownerBeforeRestart, restartedStore.readLocalStateOwner())
        assertNotNull(restartedStore.loadQueueSnapshot(7))
        assertEquals(1, restartedStore.pendingManualSavesFlow.first().size)
        assertEquals(1, restartedStore.pendingItemActionsFlow.first().size)
        assertEquals(1, restartedStore.playbackSegmentIndexByItemFlow.first().size)
        assertEquals("alice-token", restartedStore.settingsFlow.first().apiToken)
    }

    @Test
    fun sameAccountConnectionModeSwitch_doesNotClearOwnerOrPendingState() = runBlocking {
        // Regression guard for H1: switching LOCAL/LAN/REMOTE for the same signed-in account
        // must not look like an owner change. save() is only ever called by the ViewModel with
        // resetLocalStateOwner = true when the token itself changed (see
        // AppViewModel.settingsScreenAuthTargetChanged); this test drives SettingsStore.save()
        // the same way the ViewModel does for a pure connection-mode switch (token unchanged,
        // resetLocalStateOwner = false) and asserts the owner key and pending state survive.
        store.saveSignedInSession(
            baseUrl = "https://lan.example.com",
            connectionMode = ConnectionMode.LAN,
            apiToken = "alice-token",
            accountIdentity = "alice",
        )
        seedAccountScopedState()
        val ownerBeforeSwitch = store.readLocalStateOwner()

        store.save(
            baseUrl = "https://alice-tailnet.example.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            localBaseUrl = "http://10.0.2.2:8000",
            lanBaseUrl = "https://lan.example.com",
            remoteBaseUrl = "https://alice-tailnet.example.ts.net",
            apiToken = "alice-token",
            autoAdvanceOnCompletion = true,
            autoArchiveAtArticleEnd = false,
            speakTitleBeforeArticle = true,
            skipDuplicateOpeningAfterTitleIntro = true,
            playCompletionCueAtArticleEnd = true,
            keepScreenOnDuringSession = true,
            persistentPlayerEnabled = true,
            autoScrollWhileListening = true,
            locusTabReturnsToPlaybackPosition = false,
            continuousNowPlayingMarquee = true,
            forceSentenceHighlightFallback = false,
            showPlaybackDiagnostics = false,
            showAutoDownloadDiagnostics = false,
            showQueueCaptureMetadata = false,
            showPendingOutcomeSimulator = false,
            ttsVoiceName = "",
            keepShareResultNotifications = false,
            autoDownloadSavedArticles = true,
            autoCacheFavoritedItems = true,
            playbackSpeed = 1.0f,
            selectedPlaylistId = null,
            defaultSavePlaylistId = null,
            readingFontSizeSp = 16,
            readingFontOption = com.mimeo.android.model.ReaderFontOption.SANS_SERIF,
            readingLineHeightPercent = 160,
            readingMaxWidthDp = 720,
            readingParagraphSpacing = 1.0f,
            playerControlsMode = com.mimeo.android.model.PlayerControlsMode.FULL,
            playerChevronSnapEdge = com.mimeo.android.model.PlayerChevronSnapEdge.HOME,
            playerChevronEdgeOffset = 0.5f,
            resetLocalStateOwner = false,
        )

        val settings = store.settingsFlow.first()
        assertEquals("https://alice-tailnet.example.ts.net", settings.baseUrl)
        assertEquals(ownerBeforeSwitch, store.readLocalStateOwner())
        assertNotNull(store.loadQueueSnapshot(7))
        assertEquals(1, store.pendingManualSavesFlow.first().size)
        assertEquals(1, store.pendingItemActionsFlow.first().size)
        assertEquals(1, store.playbackSegmentIndexByItemFlow.first().size)
    }

    private suspend fun seedAccountScopedState() {
        store.saveSelectedPlaylistId(7)
        store.saveDefaultSavePlaylistId(9)
        store.saveQueueSnapshot(
            selectedPlaylistId = 7,
            queue = PlaybackQueueResponse(
                count = 1,
                totalCount = 1,
                items = listOf(
                    PlaybackQueueItem(
                        itemId = 101,
                        title = "Sample",
                        url = "https://example.com/item",
                    ),
                ),
            ),
        )
        store.enqueuePendingManualSave(
            source = PendingSaveSource.MANUAL,
            type = PendingManualSaveType.URL,
            urlInput = "https://example.com/save",
            titleInput = null,
            bodyInput = null,
            destinationPlaylistId = 9,
            lastFailureMessage = "offline",
            autoRetryEligible = true,
        )
        store.enqueuePendingItemAction(
            itemId = 101,
            actionType = PendingItemActionType.ARCHIVE,
        )
        store.savePlaybackSegmentIndex(
            itemId = 101,
            segmentIndex = 3,
            offsetInChunkChars = 42,
        )
    }
}
