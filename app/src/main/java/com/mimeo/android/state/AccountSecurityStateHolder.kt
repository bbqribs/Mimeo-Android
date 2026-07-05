package com.mimeo.android.state

import com.mimeo.android.ui.settings.DevicesListState
import com.mimeo.android.ui.settings.PasswordChangeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AccountSecurityStateHolder {

    // Devices & sessions
    internal val _devicesListState = MutableStateFlow<DevicesListState>(DevicesListState.Idle)
    val devicesListState: StateFlow<DevicesListState> = _devicesListState.asStateFlow()

    internal val _revokingDeviceIds = MutableStateFlow<Set<Int>>(emptySet())
    val revokingDeviceIds: StateFlow<Set<Int>> = _revokingDeviceIds.asStateFlow()

    internal val _revokeOthersInProgress = MutableStateFlow(false)
    val revokeOthersInProgress: StateFlow<Boolean> = _revokeOthersInProgress.asStateFlow()

    // Password change
    internal val _passwordChangeState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val passwordChangeState: StateFlow<PasswordChangeState> = _passwordChangeState.asStateFlow()
}
