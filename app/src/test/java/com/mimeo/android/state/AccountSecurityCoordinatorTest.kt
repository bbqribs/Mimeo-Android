package com.mimeo.android.state

import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.AppSettings
import com.mimeo.android.ui.settings.DevicesListState
import com.mimeo.android.ui.settings.PasswordChangeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-B — coordinator-level coverage for the account-security extraction: devices list/revoke,
 * password change, and the 401/stale-token delegation contract with the injected
 * [AccountSecurityCoordinator] auth-failure callback (AppViewModel's `handleAuthFailureIfNeeded`
 * in production).
 */
class AccountSecurityCoordinatorTest {

    private lateinit var server: MockWebServer
    private lateinit var state: AccountSecurityStateHolder
    private lateinit var settings: MutableStateFlow<AppSettings>
    private val snackbarMessages = mutableListOf<String>()
    private val authFailureCalls = mutableListOf<Throwable>()
    private var authFailureHandles = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        state = AccountSecurityStateHolder()
        settings = MutableStateFlow(AppSettings(baseUrl = server.url("/").toString(), apiToken = "device-token"))
        snackbarMessages.clear()
        authFailureCalls.clear()
        authFailureHandles = false
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())

    private fun newCoordinator(scope: CoroutineScope) = AccountSecurityCoordinator(
        apiClient = client(),
        state = state,
        settings = settings,
        scope = scope,
        showSnackbar = { message -> snackbarMessages.add(message) },
        handleAuthFailureIfNeeded = { error ->
            authFailureCalls.add(error)
            authFailureHandles
        },
    )

    /**
     * Runs [action] against a coordinator whose scope shares this call's own coroutine [Job][
     * kotlinx.coroutines.Job], so `runBlocking`'s structured-concurrency wait-for-children behavior
     * blocks until the coordinator's fire-and-forget `scope.launch` work has actually finished
     * before this function returns. Assertions must run *after* this call, not inside [action].
     */
    private fun runCoordinatorAction(action: (AccountSecurityCoordinator) -> Unit) {
        runBlocking {
            val coordinator = newCoordinator(CoroutineScope(coroutineContext))
            action(coordinator)
        }
    }

    private val devicesListBody = """
        [
            {"id": 2, "name": "web-session", "token_prefix": "abcd1234", "scope": "read_write",
             "created_at": "2026-01-10T08:00:00+00:00", "expires_at": null, "is_expired": false,
             "last_used_at": null, "is_current": true},
            {"id": 5, "name": "Pixel 8", "token_prefix": "efgh5678", "scope": "read",
             "created_at": "2026-01-12T08:00:00+00:00", "expires_at": null, "is_expired": false,
             "last_used_at": null, "is_current": false}
        ]
    """.trimIndent()

    @Test
    fun loadDevicesSuccessPopulatesState() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(devicesListBody))

        runCoordinatorAction { it.loadDevices() }

        val loaded = state.devicesListState.value as DevicesListState.Success
        assertEquals(2, loaded.devices.size)
        assertTrue(loaded.devices.first { it.id == 2 }.isCurrent)
    }

    @Test
    fun loadDevicesFailureSurfacesErrorState() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}"""))

        runCoordinatorAction { it.loadDevices() }

        val error = state.devicesListState.value as DevicesListState.Error
        assertEquals("Couldn't load your devices. Please try again.", error.message)
    }

    @Test
    fun revokeNonCurrentDeviceSuccessRefreshesListAndClearsProgress() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(devicesListBody))

        runCoordinatorAction { it.revokeDevice(5) }

        assertTrue(snackbarMessages.contains("Device signed out."))
        assertTrue(state.revokingDeviceIds.value.isEmpty())
        assertTrue(state.devicesListState.value is DevicesListState.Success)
    }

    @Test
    fun revokeDeviceFailureShowsSnackbarAndClearsProgress() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}"""))

        runCoordinatorAction { it.revokeDevice(5) }

        assertTrue(snackbarMessages.contains("Couldn't sign out that device. Please try again."))
        assertTrue(state.revokingDeviceIds.value.isEmpty())
    }

    @Test
    fun revokeOtherDevicesSuccessRefreshesListAndClearsProgress() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"revoked":1}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(devicesListBody))

        runCoordinatorAction { it.revokeOtherDevices() }

        assertTrue(snackbarMessages.contains("Signed out 1 other session."))
        assertFalse(state.revokeOthersInProgress.value)
        assertTrue(state.devicesListState.value is DevicesListState.Success)
    }

    @Test
    fun changePasswordSuccessSetsSuccessState() {
        server.enqueue(MockResponse().setResponseCode(200))

        runCoordinatorAction {
            it.changePassword(
                currentPassword = "old-password-123",
                newPassword = "new-password-123456",
                confirmNewPassword = "new-password-123456",
            )
        }

        val success = state.passwordChangeState.value as PasswordChangeState.Success
        assertTrue(success.message.contains("Password changed"))
        assertTrue(snackbarMessages.any { it.contains("Password changed") })
    }

    @Test
    fun changePasswordFailureSetsErrorState() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Old password incorrect"}"""))

        runCoordinatorAction {
            it.changePassword(
                currentPassword = "wrong-password",
                newPassword = "new-password-123456",
                confirmNewPassword = "new-password-123456",
            )
        }

        val error = state.passwordChangeState.value as PasswordChangeState.Error
        assertEquals("Old password incorrect", error.message)
    }

    @Test
    fun loadDevicesStaleTokenDelegatesToAuthFailureCallback() {
        authFailureHandles = true
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))

        runCoordinatorAction { it.loadDevices() }

        assertEquals(1, authFailureCalls.size)
        assertTrue(authFailureCalls.single() is ApiException)
    }

    @Test
    fun staleTokenHandledByCallbackDoesNotSetDuplicateErrorState() {
        authFailureHandles = true
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))

        runCoordinatorAction { it.loadDevices() }

        // The auth-failure callback already routed the user to re-auth; the coordinator must not
        // also surface a duplicate "couldn't load" error state on top of that navigation.
        assertEquals(DevicesListState.Idle, state.devicesListState.value)
    }

    @Test
    fun staleTokenNotHandledByCallbackFallsBackToErrorState() {
        authFailureHandles = false
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))

        runCoordinatorAction { it.loadDevices() }

        assertEquals(1, authFailureCalls.size)
        assertTrue(state.devicesListState.value is DevicesListState.Error)
    }

    @Test
    fun resetOnSignOutClearsDevicesStateButNotPasswordChangeState() {
        state._devicesListState.value = DevicesListState.Success(emptyList())
        state._revokingDeviceIds.value = setOf(1)
        state._revokeOthersInProgress.value = true
        state._passwordChangeState.value = PasswordChangeState.Error("some error")

        newCoordinator(CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)).resetOnSignOut()

        assertEquals(DevicesListState.Idle, state.devicesListState.value)
        assertTrue(state.revokingDeviceIds.value.isEmpty())
        assertFalse(state.revokeOthersInProgress.value)
        assertTrue(state.passwordChangeState.value is PasswordChangeState.Error)
    }
}
