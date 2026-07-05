package com.mimeo.android.state

import com.mimeo.android.data.ApiClient
import com.mimeo.android.model.AppSettings
import com.mimeo.android.ui.settings.DevicesListState
import com.mimeo.android.ui.settings.PasswordChangeState
import com.mimeo.android.ui.settings.passwordChangeSuccessMessage
import com.mimeo.android.ui.settings.resolveDevicesListError
import com.mimeo.android.ui.settings.resolvePasswordChangeError
import com.mimeo.android.ui.settings.resolveRevokeDeviceError
import com.mimeo.android.ui.settings.resolveRevokeOtherDevicesError
import com.mimeo.android.ui.settings.revokeOtherDevicesSuccessMessage
import com.mimeo.android.ui.settings.validatePasswordChangeInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns devices-list and password-change state/operations. 401/stale-token handling is not
 * owned here: [handleAuthFailureIfNeeded] is injected so AppViewModel's auth-failure
 * mutex/idempotence logic stays the single source of truth for re-auth.
 */
internal class AccountSecurityCoordinator(
    private val apiClient: ApiClient,
    private val state: AccountSecurityStateHolder,
    private val settings: StateFlow<AppSettings>,
    private val scope: CoroutineScope,
    private val showSnackbar: (String) -> Unit,
    private val handleAuthFailureIfNeeded: suspend (Throwable) -> Boolean,
) {
    // ── Password change ──────────────────────────────────────────────────────

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String,
    ) {
        val validationError = validatePasswordChangeInput(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmNewPassword = confirmNewPassword,
        )
        if (validationError != null) {
            state._passwordChangeState.value = PasswordChangeState.Error(validationError)
            return
        }

        scope.launch {
            state._passwordChangeState.value = PasswordChangeState.Submitting
            val current = settings.value
            try {
                apiClient.postChangePassword(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    oldPassword = currentPassword,
                    newPassword = newPassword,
                )
                val message = passwordChangeSuccessMessage()
                state._passwordChangeState.value = PasswordChangeState.Success(message)
                showSnackbar(message)
            } catch (error: Exception) {
                val resolution = resolvePasswordChangeError(error)
                if (resolution.staleAuth && handleAuthFailureIfNeeded(error)) {
                    state._passwordChangeState.value = PasswordChangeState.Idle
                } else {
                    state._passwordChangeState.value = PasswordChangeState.Error(resolution.message)
                }
            }
        }
    }

    fun clearPasswordChangeState() {
        state._passwordChangeState.value = PasswordChangeState.Idle
    }

    // ── Devices & sessions ────────────────────────────────────────────────────

    fun loadDevices() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._devicesListState.value = DevicesListState.Error("Sign in to view your devices and sessions.")
            return
        }
        scope.launch {
            state._devicesListState.value = DevicesListState.Loading
            try {
                val devices = apiClient.getAccountDevices(current.baseUrl, current.apiToken)
                state._devicesListState.value = DevicesListState.Success(devices)
            } catch (error: Throwable) {
                val resolution = resolveDevicesListError(error)
                if (resolution.staleAuth && handleAuthFailureIfNeeded(error)) {
                    state._devicesListState.value = DevicesListState.Idle
                } else {
                    state._devicesListState.value = DevicesListState.Error(resolution.message)
                }
            }
        }
    }

    fun revokeDevice(deviceId: Int) {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            state._revokingDeviceIds.value = state._revokingDeviceIds.value + deviceId
            try {
                apiClient.postRevokeDevice(current.baseUrl, current.apiToken, deviceId)
                showSnackbar("Device signed out.")
                loadDevices()
            } catch (error: Throwable) {
                val resolution = resolveRevokeDeviceError(error)
                if (resolution.staleAuth && handleAuthFailureIfNeeded(error)) {
                    // handled: navigated to re-auth
                } else {
                    showSnackbar(resolution.message)
                }
            } finally {
                state._revokingDeviceIds.value = state._revokingDeviceIds.value - deviceId
            }
        }
    }

    fun revokeOtherDevices() {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            state._revokeOthersInProgress.value = true
            try {
                val result = apiClient.postRevokeOtherDevices(current.baseUrl, current.apiToken)
                showSnackbar(revokeOtherDevicesSuccessMessage(result.revoked))
                loadDevices()
            } catch (error: Throwable) {
                val resolution = resolveRevokeOtherDevicesError(error)
                if (resolution.staleAuth && handleAuthFailureIfNeeded(error)) {
                    // handled: navigated to re-auth
                } else {
                    showSnackbar(resolution.message)
                }
            } finally {
                state._revokeOthersInProgress.value = false
            }
        }
    }

    fun resetOnSignOut() {
        state._devicesListState.value = DevicesListState.Idle
        state._revokingDeviceIds.value = emptySet()
        state._revokeOthersInProgress.value = false
    }
}
