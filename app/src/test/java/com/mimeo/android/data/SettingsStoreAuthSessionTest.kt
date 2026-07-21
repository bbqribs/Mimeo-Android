package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.ConnectionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreAuthSessionTest {

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
    fun savedSignedInSessionRestoresStoredToken() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = " issued-token ",
            accountIdentity = " test-sq-reorder-20260721 ",
        )

        val restored = store.settingsFlow.first()

        assertEquals("issued-token", restored.apiToken)
        assertEquals(ConnectionMode.REMOTE, restored.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", restored.baseUrl)
        assertEquals("test-sq-reorder-20260721", restored.authenticatedUsername)
    }

    @Test
    fun clearingOrReplacingTheTokenClearsTheAccountPresentation() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "issued-token",
            accountIdentity = "alice",
        )

        store.saveTokenOnly("")
        assertEquals("", store.settingsFlow.first().authenticatedUsername)

        store.saveSignedInSession(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "issued-token",
            accountIdentity = "alice",
        )
        store.save(
            baseUrl = "https://reader.example.com",
            connectionMode = ConnectionMode.REMOTE,
            localBaseUrl = "http://10.0.2.2:8000",
            lanBaseUrl = "http://192.168.1.10:8000",
            remoteBaseUrl = "https://reader.example.com",
            apiToken = "manual-replacement-token",
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
            playbackSpeed = 1f,
            selectedPlaylistId = null,
            defaultSavePlaylistId = null,
            readingFontSizeSp = 16,
            readingFontOption = com.mimeo.android.model.ReaderFontOption.SANS_SERIF,
            readingLineHeightPercent = 160,
            readingMaxWidthDp = 720,
            readingParagraphSpacing = 1f,
            playerControlsMode = com.mimeo.android.model.PlayerControlsMode.FULL,
            playerChevronSnapEdge = com.mimeo.android.model.PlayerChevronSnapEdge.HOME,
            playerChevronEdgeOffset = 0.5f,
        )

        assertEquals("", store.settingsFlow.first().authenticatedUsername)
    }

    @Test
    fun accountSwitchReplacesThePersistedAccountAndEndpointTogether() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://one.example.com/",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "one-token",
            accountIdentity = "alice",
        )

        store.saveSignedInSession(
            baseUrl = "https://two.example.com/",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "two-token",
            accountIdentity = "bob",
        )

        val restored = store.settingsFlow.first()
        assertEquals("bob", restored.authenticatedUsername)
        assertEquals("https://two.example.com/", restored.baseUrl)
        assertEquals("two-token", restored.apiToken)
    }

    @Test
    fun clearingStaleTokenPreservesRemoteEndpointState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "stale-token",
        )

        store.saveTokenOnly("")
        val afterClear = store.settingsFlow.first()

        assertEquals("", afterClear.apiToken)
        assertEquals(ConnectionMode.REMOTE, afterClear.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterClear.baseUrl)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterClear.remoteBaseUrl)
    }

    @Test
    fun replacingTokenDoesNotChangeRemoteEndpointState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "stale-token",
        )

        store.saveTokenOnly("fresh-token")
        val afterReplace = store.settingsFlow.first()

        assertEquals("fresh-token", afterReplace.apiToken)
        assertEquals(ConnectionMode.REMOTE, afterReplace.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterReplace.baseUrl)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterReplace.remoteBaseUrl)
    }
}
