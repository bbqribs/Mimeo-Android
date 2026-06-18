package com.mimeo.android.bluesky

import com.mimeo.android.UiSnackbarMessage
import com.mimeo.android.data.ApiClient
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyOperatorStatusResponse
import com.mimeo.android.state.BlueskyStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BlueskyServiceCoordinatorTest {

    private lateinit var state: BlueskyStateHolder
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var snackbarMessages: Channel<UiSnackbarMessage>
    private lateinit var coordinator: BlueskyServiceCoordinator

    @Before
    fun setUp() {
        state = BlueskyStateHolder()
        settings = MutableStateFlow(AppSettings())
        snackbarMessages = Channel(Channel.BUFFERED)
        coordinator = BlueskyServiceCoordinator(
            apiClient = ApiClient(),
            state = state,
            settings = settings,
            scope = CoroutineScope(Dispatchers.Unconfined),
            snackbarMessages = snackbarMessages,
            onCandidateSaved = {},
        )
    }

    @Test
    fun setBlueskyBrowseSourceFilterUpdatesState() {
        assertNull(state.blueskyBrowseSourceFilter.value)
        coordinator.setBlueskyBrowseSourceFilter(42)
        assertEquals(42, state.blueskyBrowseSourceFilter.value)
        coordinator.setBlueskyBrowseSourceFilter(null)
        assertNull(state.blueskyBrowseSourceFilter.value)
    }

    @Test
    fun setBlueskyBrowseQueryUpdatesState() {
        assertEquals("", state.blueskyBrowseQuery.value)
        coordinator.setBlueskyBrowseQuery("kotlin flows")
        assertEquals("kotlin flows", state.blueskyBrowseQuery.value)
    }

    @Test
    fun resetOnSignOutClearsConnectionState() {
        state._blueskyAccountConnection.value = BlueskyAccountConnectionResponse(connected = true, handle = "alice.bsky.social")
        state._blueskyOperatorStatus.value = BlueskyOperatorStatusResponse(enabled = true)
        state._blueskyStatusError.value = "some error"
        state._blueskyConnectError.value = "connect error"
        state._blueskyConnectIsReadOnlyScope.value = true

        coordinator.resetOnSignOut()

        assertNull(state.blueskyAccountConnection.value)
        assertNull(state.blueskyOperatorStatus.value)
        assertNull(state.blueskyStatusError.value)
        assertNull(state.blueskyConnectError.value)
        assertFalse(state.blueskyConnectIsReadOnlyScope.value)
    }

    @Test
    fun refreshBlueskyStatusSetsErrorWhenTokenBlank() {
        settings.value = AppSettings(apiToken = "")

        coordinator.refreshBlueskyStatus()

        assertNull(state.blueskyAccountConnection.value)
        assertNull(state.blueskyOperatorStatus.value)
        assertEquals("Token required to load Bluesky status.", state.blueskyStatusError.value)
    }

    @Test
    fun connectBlueskySetErrorWhenTokenBlank() {
        settings.value = AppSettings(apiToken = "")

        coordinator.connectBluesky("alice.bsky.social", "app-password")

        assertEquals("Sign in first to connect a Bluesky account.", state.blueskyConnectError.value)
        assertFalse(state.blueskyConnecting.value)
    }

    @Test
    fun disconnectBlueskyNoopsWhenTokenBlank() {
        settings.value = AppSettings(apiToken = "")

        coordinator.disconnectBluesky()

        assertFalse(state.blueskyDisconnecting.value)
    }

    @Test
    fun loadBlueskyBrowseSetsErrorWhenTokenBlank() {
        settings.value = AppSettings(apiToken = "")

        coordinator.loadBlueskyBrowse()

        assertEquals("Token required.", state.blueskyBrowseError.value)
    }

    @Test
    fun loadBlueskyCandidatePickerSetsErrorWhenTokenBlank() {
        settings.value = AppSettings(apiToken = "")

        coordinator.loadBlueskyCandidatePicker()

        assertEquals("Token required to load Bluesky sources.", state.blueskyCandidatePickerError.value)
    }

    @Test
    fun resolveBlueskyPinTarget_feedGenerator_setsUri() {
        // Feeds pin by uri, like lists — this is the parity fix; previously feeds resolved
        // to no target and the pin was skipped.
        val target = resolveBlueskyPinTarget(
            sourceType = "feed_generator",
            scanIdentifier = "at://did:plc:abc/app.bsky.feed.generator/news",
            selectionActor = null,
            selectionUri = "at://did:plc:abc/app.bsky.feed.generator/news",
        )
        assertNull(target.actor)
        assertEquals("at://did:plc:abc/app.bsky.feed.generator/news", target.uri)
    }

    @Test
    fun resolveBlueskyPinTarget_listFeed_setsUri() {
        val target = resolveBlueskyPinTarget(
            sourceType = "list_feed",
            scanIdentifier = null,
            selectionActor = null,
            selectionUri = "at://did:plc:abc/app.bsky.graph.list/uk",
        )
        assertNull(target.actor)
        assertEquals("at://did:plc:abc/app.bsky.graph.list/uk", target.uri)
    }

    @Test
    fun resolveBlueskyPinTarget_authorFeed_setsActor() {
        val target = resolveBlueskyPinTarget(
            sourceType = "author_feed",
            scanIdentifier = "alice.bsky.social",
            selectionActor = "alice.bsky.social",
            selectionUri = null,
        )
        assertEquals("alice.bsky.social", target.actor)
        assertNull(target.uri)
    }

    @Test
    fun resolveBlueskyPinTarget_homeTimeline_hasNoTarget() {
        val target = resolveBlueskyPinTarget(
            sourceType = "home_timeline",
            scanIdentifier = null,
            selectionActor = null,
            selectionUri = null,
        )
        assertNull(target.actor)
        assertNull(target.uri)
    }
}
