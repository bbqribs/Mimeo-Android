package com.mimeo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.DeviceSession

@Composable
fun DevicesAndSessionsScreen(vm: AppViewModel) {
    val devicesListState by vm.devicesListState.collectAsState()
    val revokingDeviceIds by vm.revokingDeviceIds.collectAsState()
    val revokeOthersInProgress by vm.revokeOthersInProgress.collectAsState()
    var deviceToRevoke by remember { mutableStateOf<DeviceSession?>(null) }
    var showSignOutOthersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Devices & sessions", style = MaterialTheme.typography.titleMedium)
        Text(
            "These are the sessions currently signed in to your account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val devices = (devicesListState as? DevicesListState.Success)?.devices.orEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.loadDevices() }, enabled = devicesListState !is DevicesListState.Loading) {
                Text("Refresh")
            }
            Button(
                onClick = { showSignOutOthersDialog = true },
                enabled = hasRevocableOtherDevices(devices) && !revokeOthersInProgress,
            ) {
                Text(if (revokeOthersInProgress) "Signing out..." else "Sign out everywhere else")
            }
        }

        when (val state = devicesListState) {
            is DevicesListState.Idle, is DevicesListState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DevicesListState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is DevicesListState.Success -> {
                if (state.devices.isEmpty()) {
                    Text(
                        "No active sessions found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.devices, key = { it.id }) { device ->
                            DeviceRow(
                                device = device,
                                revoking = revokingDeviceIds.contains(device.id),
                                onSignOut = { deviceToRevoke = device },
                            )
                        }
                    }
                }
            }
        }
    }

    deviceToRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToRevoke = null },
            title = { Text("Sign out this device?") },
            text = { Text("\"${device.name}\" will no longer be able to use its current session.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.revokeDevice(device.id)
                        deviceToRevoke = null
                    },
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRevoke = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSignOutOthersDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutOthersDialog = false },
            title = { Text("Sign out everywhere else?") },
            text = { Text("All other signed-in sessions will be signed out. This device stays signed in.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutOthersDialog = false
                        vm.revokeOtherDevices()
                    },
                ) {
                    Text("Sign out everywhere else", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutOthersDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DeviceRow(
    device: DeviceSession,
    revoking: Boolean,
    onSignOut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                if (device.isCurrent) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "Current device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                "Signed in: ${formatDeviceTimestamp(device.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Last used: ${formatDeviceTimestamp(device.lastUsedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!device.expiresAt.isNullOrBlank()) {
                Text(
                    "Expires: ${formatDeviceTimestamp(device.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!device.isCurrent) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSignOut,
                        enabled = !revoking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(if (revoking) "Signing out..." else "Sign out")
                    }
                }
            }
        }
    }
}
