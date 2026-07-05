package com.mimeo.android.ui.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyScannerPreferences
import com.mimeo.android.ui.bluesky.BlueskyHandleField
import com.mimeo.android.util.bluesky.normalizeBlueskyHandleInput
import com.mimeo.android.ui.settings.BlueskyHealthCard
import com.mimeo.android.ui.settings.BlueskySourceRow
import com.mimeo.android.ui.settings.SettingsKeyValueLine
import com.mimeo.android.ui.settings.SettingsSectionHeader
import com.mimeo.android.ui.settings.blueskyAccountDiagnosticLines
import com.mimeo.android.ui.settings.blueskySchedulerDiagnosticsVisible
import com.mimeo.android.ui.settings.formatBlueskyBool
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

/**
 * The Bluesky settings spoke: connection status/health, connect/reconnect form, scheduler and
 * per-source diagnostics (debug-only), and scan-limit preferences. Collects its own
 * Bluesky-exclusive VM flows directly so changes here don't recompose the rest of the Settings
 * screen; [blueskyAccountConnection] and [blueskyScannerPreferences] are passed in because
 * `SettingsScreen` also observes them for its own effects.
 */
@Composable
internal fun BlueskySection(
    vm: AppViewModel,
    blueskyAccountConnection: BlueskyAccountConnectionResponse?,
    blueskyScannerPreferences: BlueskyScannerPreferences?,
    showBlueskyConnectForm: Boolean,
    onShowBlueskyConnectFormChange: (Boolean) -> Unit,
    blueskyHandle: String,
    onBlueskyHandleChange: (String) -> Unit,
    blueskyAppPassword: String,
    onBlueskyAppPasswordChange: (String) -> Unit,
    localMaxAgeHours: String,
    onLocalMaxAgeHoursChange: (String) -> Unit,
    localMaxPosts: String,
    onLocalMaxPostsChange: (String) -> Unit,
    localMaxLinks: String,
    onLocalMaxLinksChange: (String) -> Unit,
    onCreateBlueskySmartPlaylist: () -> Unit,
    onCreateSourceSmartPlaylist: (name: String, captureKinds: String, sort: String) -> Unit,
    onSignOut: () -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val blueskyOperatorStatus by vm.blueskyOperatorStatus.collectAsState()
    val blueskyStatusLoading by vm.blueskyStatusLoading.collectAsState()
    val blueskyStatusError by vm.blueskyStatusError.collectAsState()
    val blueskyConnecting by vm.blueskyConnecting.collectAsState()
    val blueskyConnectError by vm.blueskyConnectError.collectAsState()
    val blueskyConnectIsReadOnlyScope by vm.blueskyConnectIsReadOnlyScope.collectAsState()
    val blueskyDisconnecting by vm.blueskyDisconnecting.collectAsState()
    val blueskyScannerPreferencesLoading by vm.blueskyScannerPreferencesLoading.collectAsState()
    val blueskyScannerPreferencesSaving by vm.blueskyScannerPreferencesSaving.collectAsState()
    val blueskyScannerPreferencesError by vm.blueskyScannerPreferencesError.collectAsState()

    SettingsSectionHeader(
        title = "Bluesky",
        subtitle = "Connect your Bluesky account and check its status. Saving from Bluesky never changes Up Next.",
    )
    BlueskyHealthCard(
        account = blueskyAccountConnection,
        operatorStatus = blueskyOperatorStatus,
        loading = blueskyStatusLoading,
        statusError = blueskyStatusError,
        onConnectOrReconnect = { onShowBlueskyConnectFormChange(true) },
        onTryAgain = { vm.refreshBlueskyStatus() },
    )

    var blueskyExplainerExpanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "About Bluesky app passwords",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { blueskyExplainerExpanded = !blueskyExplainerExpanded }) {
                    Text(if (blueskyExplainerExpanded) "Hide" else "Show")
                }
            }
            AnimatedVisibility(visible = blueskyExplainerExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Mimeo uses a Bluesky app password for authenticated read access — not your main Bluesky password.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "App passwords grant limited access and can be created or revoked at any time in your Bluesky account settings (Settings → App Passwords).",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Bluesky status",
                style = if (isV1) mTypography.row else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (isV1) mColors.fg else Color.Unspecified,
            )
            if (blueskyStatusLoading) {
                Text(
                    text = "Loading Bluesky status...",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!blueskyStatusError.isNullOrBlank()) {
                Text(
                    text = blueskyStatusError.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            val account = blueskyAccountConnection
            val scheduler = blueskyOperatorStatus
            if (account != null) {
                if (account.connected == true) {
                    // Raw account identifiers (DID) and un-humanized backend codes are
                    // diagnostics: ordinary users see the friendly BlueskyHealthCard
                    // above, so these only render in debug builds.
                    blueskyAccountDiagnosticLines(account, BuildConfig.DEBUG).forEach { line ->
                        SettingsKeyValueLine(line.label, line.value)
                    }
                    if (account.disconnectAvailable == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                enabled = !blueskyDisconnecting && !blueskyStatusLoading,
                                onClick = { vm.disconnectBluesky() },
                            ) {
                                Text(if (blueskyDisconnecting) "Disconnecting..." else "Disconnect Bluesky account")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No Bluesky account is connected.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The connect form is shared by first-time Connect and Reconnect
                // (re-entering an app password); the recovery action reveals it.
                val showConnectForm = account.connected != true || showBlueskyConnectForm
                if (showConnectForm) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isV1) mColors.line else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (account.connected == true) "Reconnect Bluesky account" else "Connect Bluesky account",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (account.connected == true) {
                            TextButton(onClick = { onShowBlueskyConnectFormChange(false) }) {
                                Text("Cancel")
                            }
                        }
                    }
                    BlueskyHandleField(
                        value = blueskyHandle,
                        onValueChange = onBlueskyHandleChange,
                        label = "Bluesky handle",
                        enabled = !blueskyConnecting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = blueskyAppPassword,
                        onValueChange = onBlueskyAppPasswordChange,
                        label = { Text("Bluesky app password") },
                        placeholder = { Text("App password — not your main Bluesky password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !blueskyConnecting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!blueskyConnectError.isNullOrBlank()) {
                        Text(
                            text = blueskyConnectError!!,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                        if (blueskyConnectIsReadOnlyScope) {
                            TextButton(onClick = onSignOut) {
                                Text("Sign out")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            enabled = normalizeBlueskyHandleInput(blueskyHandle) != null && blueskyAppPassword.isNotBlank() && !blueskyConnecting,
                            onClick = { vm.connectBluesky(normalizeBlueskyHandleInput(blueskyHandle)!!, blueskyAppPassword) },
                        ) {
                            Text(if (blueskyConnecting) "Connecting..." else "Connect")
                        }
                    }
                }
            }
            // Scheduler internals, raw last-run codes, raw backend error messages, and
            // per-source actor identifiers are operator diagnostics — debug-only. Ordinary
            // users rely on the friendly "What happened?" disclosure in BlueskyHealthCard.
            if (scheduler != null && blueskySchedulerDiagnosticsVisible(BuildConfig.DEBUG)) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isV1) mColors.line else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 1.dp,
                )
                var schedulerDetailsExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Scheduler & sources",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { schedulerDetailsExpanded = !schedulerDetailsExpanded }) {
                        Text(if (schedulerDetailsExpanded) "Hide" else "Show")
                    }
                }
                AnimatedVisibility(visible = schedulerDetailsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsKeyValueLine("Scheduler enabled", formatBlueskyBool(scheduler.resolvedSchedulerEnabled))
                        SettingsKeyValueLine("State", scheduler.state ?: "Unknown")
                        SettingsKeyValueLine("Enabled source count", scheduler.enabledSourceCount?.toString() ?: "0")
                        SettingsKeyValueLine("Due source count", scheduler.dueSourceCount?.toString() ?: "0")
                        SettingsKeyValueLine("Next due", scheduler.resolvedNextDue ?: "Not scheduled")
                        SettingsKeyValueLine("Last run status", scheduler.lastRunStatus ?: "Unknown")
                        SettingsKeyValueLine("Last error", scheduler.resolvedLastErrorMessage?.takeIf { it.isNotBlank() } ?: "None")
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isV1) mColors.line else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 1.dp,
                        )
                        Text(
                            text = "Bluesky sources",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        val visibleSources = scheduler.sources.filterNot { it.hidden == true || it.archived == true }
                        if (visibleSources.isEmpty()) {
                            Text(
                                text = "No visible Bluesky sources.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            visibleSources.forEach { source ->
                                BlueskySourceRow(
                                    source = source,
                                    onCreateSmartPlaylist = { name, captureKinds, sort ->
                                        onCreateSourceSmartPlaylist(name, captureKinds, sort)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (!blueskyStatusLoading && blueskyStatusError.isNullOrBlank() && account == null && scheduler == null) {
                Text(
                    text = "No Bluesky status data available.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(
                    enabled = !blueskyStatusLoading,
                    onClick = onCreateBlueskySmartPlaylist,
                ) {
                    Text("Create smart playlist")
                }
                TextButton(
                    enabled = !blueskyStatusLoading,
                    onClick = { vm.refreshBlueskyStatus() },
                ) {
                    Text(if (blueskyStatusLoading) "Refreshing..." else "Refresh")
                }
            }
        }
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            var scannerDefaultsExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Scan limits (advanced)",
                    style = if (isV1) mTypography.row else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = if (isV1) mColors.fg else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { scannerDefaultsExpanded = !scannerDefaultsExpanded }) {
                    Text(if (scannerDefaultsExpanded) "Hide" else "Show")
                }
            }
            AnimatedVisibility(visible = scannerDefaultsExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Default live scan caps — explicit scan requests may override them. These do not enable auto-save or mutate Up Next.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val prefs = blueskyScannerPreferences
            if (blueskyScannerPreferencesLoading && prefs == null) {
                Text(
                    text = "Loading scanner defaults...",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (prefs != null) {
                OutlinedTextField(
                    value = localMaxAgeHours,
                    onValueChange = onLocalMaxAgeHoursChange,
                    label = { Text("Lookback window (hours, 1–${prefs.maxAgeHoursCeiling})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !blueskyScannerPreferencesSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = localMaxPosts,
                    onValueChange = onLocalMaxPostsChange,
                    label = { Text("Posts to check (1–${prefs.maxPostsCeiling})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !blueskyScannerPreferencesSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = localMaxLinks,
                    onValueChange = onLocalMaxLinksChange,
                    label = { Text("Links to show (1–${prefs.maxLinksCeiling})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !blueskyScannerPreferencesSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!blueskyScannerPreferencesError.isNullOrBlank()) {
                Text(
                    text = blueskyScannerPreferencesError.orEmpty(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            if (prefs != null) {
                val saveAgeHours = localMaxAgeHours.trim().toIntOrNull()
                val savePosts = localMaxPosts.trim().toIntOrNull()
                val saveLinks = localMaxLinks.trim().toIntOrNull()
                val saveInputValid = saveAgeHours != null && saveAgeHours >= 1 &&
                    savePosts != null && savePosts >= 1 &&
                    saveLinks != null && saveLinks >= 1
                val localMatchesBackend = localMaxAgeHours.trim() == prefs.maxAgeHours.toString() &&
                    localMaxPosts.trim() == prefs.maxPosts.toString() &&
                    localMaxLinks.trim() == prefs.maxLinks.toString()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        enabled = !localMatchesBackend && !blueskyScannerPreferencesSaving,
                        onClick = {
                            onLocalMaxAgeHoursChange(prefs.maxAgeHours.toString())
                            onLocalMaxPostsChange(prefs.maxPosts.toString())
                            onLocalMaxLinksChange(prefs.maxLinks.toString())
                        },
                    ) {
                        Text("Reset")
                    }
                    Button(
                        enabled = saveInputValid && !blueskyScannerPreferencesSaving && !blueskyScannerPreferencesLoading,
                        onClick = {
                            if (saveAgeHours != null && savePosts != null && saveLinks != null) {
                                vm.saveBlueskyScannerPreferences(saveAgeHours, savePosts, saveLinks)
                            }
                        },
                    ) {
                        Text(if (blueskyScannerPreferencesSaving) "Saving..." else "Save scanner defaults")
                    }
                }
            }
            }
            }
        }
    }
}
