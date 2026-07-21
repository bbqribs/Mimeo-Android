package com.mimeo.android.ui.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.ui.common.authenticatedIdentityPresentation
import com.mimeo.android.ui.settings.ConnectionTestMessageResolver
import com.mimeo.android.ui.settings.PASSWORD_CHANGE_MIN_LENGTH
import com.mimeo.android.ui.settings.PasswordChangeState
import com.mimeo.android.ui.settings.SettingsActionIconButton
import com.mimeo.android.ui.settings.SettingsSectionHeader
import com.mimeo.android.ui.settings.authSessionConsequenceSummary
import com.mimeo.android.ui.settings.buildConnectionTestResultText
import com.mimeo.android.ui.settings.connectionModeBaseUrlGuidance
import com.mimeo.android.ui.settings.connectionModeTokenAuthHelp
import com.mimeo.android.ui.settings.deviceTokenScopeHint
import com.mimeo.android.ui.settings.description
import com.mimeo.android.ui.settings.displayName
import com.mimeo.android.ui.settings.formatAuthSessionStatusSummary
import com.mimeo.android.ui.settings.formatConnectionTestSuccessSummary
import com.mimeo.android.ui.settings.formatCurrentConnectionStatusSummary
import com.mimeo.android.ui.settings.formatPendingSaveTestSummary
import com.mimeo.android.ui.settings.normalizeConnectionBaseUrl
import com.mimeo.android.ui.settings.sharePlainText
import com.mimeo.android.ui.settings.signOutConfirmationMessage
import com.mimeo.android.ui.settings.validateConnectionEndpoint
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Account & Connection settings spoke: sign-in/token, Local/LAN/Remote mode selection,
 * connection test, and Devices & sessions entry point. `saveCurrent`/`applyConnectionSnapshot`/
 * `selectedModeBaseUrl`/`savedModeBaseUrl` stay owned by `SettingsScreen` (they also read
 * Playback/Library/Developer fields for the combined settings payload) and are passed in as
 * callbacks so this section only needs to know about its own connection/token fields.
 */
@Composable
internal fun AccountSection(
    vm: AppViewModel,
    settings: AppSettings,
    connectionMode: ConnectionMode,
    onConnectionModeChange: (ConnectionMode) -> Unit,
    localBaseUrl: String,
    onLocalBaseUrlChange: (String) -> Unit,
    lanBaseUrl: String,
    onLanBaseUrlChange: (String) -> Unit,
    remoteBaseUrl: String,
    onRemoteBaseUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    testingConnection: Boolean,
    testRequested: Boolean,
    onTestRequestedChange: (Boolean) -> Unit,
    lastConnectionTestResult: String?,
    onLastConnectionTestResultChange: (String?) -> Unit,
    lastConnectionTestedAtMs: Long?,
    onLastConnectionTestedAtMsChange: (Long?) -> Unit,
    selectedModeBaseUrl: () -> String,
    savedModeBaseUrl: () -> String,
    saveCurrent: () -> Unit,
    applyConnectionSnapshot: (ConnectionTestSuccessSnapshot) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDevicesAndSessions: () -> Unit,
    onShowPasswordChangeDialogChange: (Boolean) -> Unit,
    onClearPasswordChangeState: () -> Unit,
    onShowSignOutDialogChange: (Boolean) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val pendingManualSaves by vm.pendingManualSaves.collectAsState()
    val connectionTestSuccessByMode by vm.connectionTestSuccessByMode.collectAsState()
    val authenticatedIdentity = authenticatedIdentityPresentation(settings)

    SettingsSectionHeader(
        title = "Account & Connection",
        subtitle = "Choose Local, LAN, or Remote mode. Sign In is recommended; manual token entry replaces this device token.",
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Account and server currently in use",
                style = if (isV1) mTypography.row else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (isV1) mColors.fg else Color.Unspecified,
            )
            Text(
                text = "Account: ${authenticatedIdentity.usernameDisplay}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Server: ${authenticatedIdentity.endpointDisplay}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Authentication: ${authenticatedIdentity.authenticationState}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onShowPasswordChangeDialogChange(true)
                        onClearPasswordChangeState()
                    },
                ) {
                    Text("Change Password")
                }
                Button(
                    onClick = { onShowSignOutDialogChange(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isV1) mColors.danger else androidx.compose.material3.MaterialTheme.colorScheme.error,
                        contentColor = if (isV1) mColors.accentOn else androidx.compose.material3.MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Sign Out")
                }
            }
            Text(
                text = "Connection",
                style = if (isV1) mTypography.row else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (isV1) mColors.fg else Color.Unspecified,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = connectionMode == mode,
                        onClick = { onConnectionModeChange(mode) },
                        label = { Text(mode.displayName()) },
                    )
                }
            }
            Text(
                text = connectionMode.description(),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = selectedModeBaseUrl(),
                onValueChange = {
                    when (connectionMode) {
                        ConnectionMode.LOCAL -> onLocalBaseUrlChange(it)
                        ConnectionMode.LAN -> onLanBaseUrlChange(it)
                        ConnectionMode.REMOTE -> onRemoteBaseUrlChange(it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${connectionMode.displayName()} Base URL") },
                singleLine = true,
            )
            Text(
                text = "Editable ${connectionMode.displayName()} endpoint. It does not change the server currently in use until Save.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val endpointValidation = validateConnectionEndpoint(connectionMode, selectedModeBaseUrl())
            endpointValidation.blockingError?.let { message ->
                Text(
                    text = message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            endpointValidation.warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Advanced token access (device token)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            var connectionHelpExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Connection help",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                TextButton(onClick = { connectionHelpExpanded = !connectionHelpExpanded }) {
                    Text(if (connectionHelpExpanded) "Hide" else "Show")
                }
            }
            AnimatedVisibility(visible = connectionHelpExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = connectionModeBaseUrlGuidance(connectionMode),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = connectionModeTokenAuthHelp(connectionMode),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = deviceTokenScopeHint(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatAuthSessionStatusSummary(
                            savedToken = settings.apiToken,
                            editedToken = token,
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = authSessionConsequenceSummary(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val currentModeSnapshot = connectionTestSuccessByMode[connectionMode]
            val hasUnsavedModeUrlEdit =
                normalizeConnectionBaseUrl(selectedModeBaseUrl()) != normalizeConnectionBaseUrl(savedModeBaseUrl())
            Text(
                text = formatCurrentConnectionStatusSummary(
                    mode = connectionMode,
                    selectedBaseUrl = selectedModeBaseUrl(),
                    snapshot = currentModeSnapshot,
                ),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasUnsavedModeUrlEdit) {
                Text(
                    text = "${connectionMode.displayName()}: unsaved URL edits in the field.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = endpointValidation.blockingError == null,
                    onClick = {
                        if (endpointValidation.blockingError != null) {
                            vm.showSnackbar(endpointValidation.blockingError)
                        } else {
                            saveCurrent()
                        }
                    },
                ) { Text("Save") }
                Button(
                    enabled = !testingConnection && endpointValidation.blockingError == null,
                    onClick = {
                        if (endpointValidation.blockingError != null) {
                            onLastConnectionTestResultChange(endpointValidation.blockingError)
                            onLastConnectionTestedAtMsChange(System.currentTimeMillis())
                            return@Button
                        }
                        saveCurrent()
                        if (token.isBlank()) {
                            onTestRequestedChange(false)
                            onLastConnectionTestResultChange(
                                ConnectionTestMessageResolver.tokenRequired(
                                    mode = connectionMode,
                                    baseUrl = selectedModeBaseUrl(),
                                ),
                            )
                            onLastConnectionTestedAtMsChange(System.currentTimeMillis())
                        } else {
                            onTestRequestedChange(true)
                            vm.testConnection()
                        }
                    },
                ) { Text(if (testingConnection) "Testing..." else "Test") }
                Button(onClick = onOpenDiagnostics) { Text("Diagnostics") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenDevicesAndSessions) { Text("Devices & sessions") }
            }
            val testResultTimestamp = lastConnectionTestedAtMs?.let { millis ->
                runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
                }.getOrDefault(null)
            }
            val pendingSummary = formatPendingSaveTestSummary(
                pendingItems = pendingManualSaves,
                selectedPlaylistId = settings.selectedPlaylistId,
            )
            Text(
                text = "Test results",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            val fullTestResultText = buildConnectionTestResultText(
                baseResult = lastConnectionTestResult,
                pendingSummary = pendingSummary,
                timestamp = testResultTimestamp,
            )
            Text(
                text = fullTestResultText,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsActionIconButton(
                    enabled = fullTestResultText.isNotBlank(),
                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy test result") },
                    tooltip = "Copy test result",
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullTestResultText))
                        vm.showSnackbar("Test result copied")
                    },
                )
                SettingsActionIconButton(
                    enabled = fullTestResultText.isNotBlank(),
                    icon = { Icon(Icons.Outlined.Share, contentDescription = "Share test result") },
                    tooltip = "Share test result",
                    onClick = { sharePlainText(context, "Mimeo connection test", fullTestResultText) },
                )
            }
            val lastSuccessItems = ConnectionMode.entries.mapNotNull { mode ->
                connectionTestSuccessByMode[mode]?.let { snapshot -> mode to snapshot }
            }
            if (lastSuccessItems.isNotEmpty()) {
                Text(
                    text = "Last successful test",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                lastSuccessItems.forEach { (_, snapshot) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = formatConnectionTestSuccessSummary(snapshot),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsActionIconButton(
                                icon = { Icon(Icons.Outlined.Check, contentDescription = "Use this URL") },
                                tooltip = "Use this URL",
                                onClick = { applyConnectionSnapshot(snapshot) },
                            )
                            SettingsActionIconButton(
                                icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL") },
                                tooltip = "Copy URL",
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(snapshot.baseUrl))
                                    vm.showSnackbar("${snapshot.mode.displayName()} URL copied")
                                },
                            )
                            SettingsActionIconButton(
                                icon = { Icon(Icons.Outlined.Share, contentDescription = "Share URL") },
                                tooltip = "Share URL",
                                onClick = {
                                    val shareText = "${snapshot.mode.displayName()} URL: ${snapshot.baseUrl}"
                                    sharePlainText(context, "Mimeo connection URL", shareText)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SignOutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = { Text(signOutConfirmationMessage()) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Sign out",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun PasswordChangeDialog(
    passwordChangeState: PasswordChangeState,
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmNewPassword: String,
    onConfirmNewPasswordChange: (String) -> Unit,
    onClearPasswordChangeState: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (currentPassword: String, newPassword: String, confirmNewPassword: String) -> Unit,
) {
    val passwordChangeError = (passwordChangeState as? PasswordChangeState.Error)?.message
    val isSubmittingPasswordChange = passwordChangeState is PasswordChangeState.Submitting
    AlertDialog(
        onDismissRequest = {
            if (!isSubmittingPasswordChange) {
                onDismiss()
                onClearPasswordChangeState()
            }
        },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        onCurrentPasswordChange(it)
                        if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !isSubmittingPasswordChange,
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        onNewPasswordChange(it)
                        if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !isSubmittingPasswordChange,
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = {
                        onConfirmNewPasswordChange(it)
                        if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    enabled = !isSubmittingPasswordChange,
                )
                Text(
                    text = "Use at least $PASSWORD_CHANGE_MIN_LENGTH characters.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!passwordChangeError.isNullOrBlank()) {
                    Text(
                        text = passwordChangeError,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmittingPasswordChange,
                onClick = {
                    onSubmit(currentPassword, newPassword, confirmNewPassword)
                },
            ) {
                Text(if (isSubmittingPasswordChange) "Changing..." else "Change password")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmittingPasswordChange,
                onClick = {
                    onDismiss()
                    onClearPasswordChangeState()
                },
            ) {
                Text("Cancel")
            }
        },
    )
}
