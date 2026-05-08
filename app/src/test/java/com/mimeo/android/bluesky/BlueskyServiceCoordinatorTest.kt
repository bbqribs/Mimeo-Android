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
}
