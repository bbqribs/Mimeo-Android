package com.mimeo.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.AiProviderEditResult
import com.mimeo.android.model.AiProviderStatusState

/**
 * BYOAI-A5 — the operator AI Provider edit screen.
 *
 * Reached only through the gated "Manage AI provider" entry (see
 * [aiProviderManageEntryVisible]); the route does not exist for ordinary
 * sessions. All actions talk to the Mimeo backend only — Android never calls an
 * LLM provider directly.
 *
 * No-secret handling enforced here (ticket §4):
 *   - the API key lives ONLY in a plain [remember] string, so it is never written
 *     to the saved-instance bundle and is discarded when this composable leaves
 *     composition (back / navigation away / process death);
 *   - the key is never prefilled from any backend value;
 *   - the key is wiped after save, test, delete, and cancel;
 *   - only the non-secret `key_last4` tail is ever shown, via the safe-status
 *     section; the full key is never redisplayed.
 */
@Composable
internal fun AiProviderEditScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val statusState by vm.aiProviderStatus.collectAsState()
    val busy by vm.aiProviderEditBusy.collectAsState()
    val result by vm.aiProviderEditResult.collectAsState()

    val liveStatus = (statusState as? AiProviderStatusState.Ready)?.status

    // Seed the form once from the status that was loaded before the gated entry
    // was shown. Non-secret fields survive a configuration change; the key does
    // not (plain remember below).
    val initialForm = remember { aiProviderEditFormFrom(liveStatus) }
    var providerSlug by rememberSaveable { mutableStateOf(initialForm.provider) }
    var model by rememberSaveable { mutableStateOf(initialForm.model) }
    var baseUrl by rememberSaveable { mutableStateOf(initialForm.baseUrl) }
    var enabled by rememberSaveable { mutableStateOf(initialForm.enabled) }
    // Transient, write-only key — intentionally NOT rememberSaveable.
    var apiKey by remember { mutableStateOf("") }

    var providerMenuOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Fresh screen: drop any stale action result, and on dispose wipe the key
    // and clear the result so nothing lingers.
    LaunchedEffect(Unit) { vm.clearAiProviderEditResult() }
    DisposableEffect(Unit) {
        onDispose {
            apiKey = ""
            vm.clearAiProviderEditResult()
        }
    }

    fun currentForm() = AiProviderEditForm(
        provider = providerSlug,
        model = model,
        baseUrl = baseUrl,
        enabled = enabled,
        apiKey = apiKey,
    )

    fun exit() {
        apiKey = ""
        onBack()
    }

    BackHandler(enabled = true) {
        if (showDeleteConfirm) {
            showDeleteConfirm = false
        } else {
            exit()
        }
    }

    val showBaseUrl = aiProviderShowBaseUrl(providerSlug)
    val selectedOption = aiProviderOptionFor(providerSlug)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { exit() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to AI Summaries",
                )
            }
            Text(
                text = "AI Provider",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // ----- Safe status (read-only) -----
        liveStatus?.let { status ->
            val view = aiProviderEditStatusView(status)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Current status", style = MaterialTheme.typography.bodyMedium)
                    SmallInfo(view.stateMessage)
                    view.providerLine?.let { SmallInfo(it) }
                    view.modelLine?.let { SmallInfo(it) }
                    view.baseUrlLine?.let { SmallInfo(it) }
                    SmallInfo(view.enabledLine)
                    view.keyLine?.let { SmallInfo(it) }
                    view.sourceLine?.let { SmallInfo(it) }
                    view.lastTestLine?.let { SmallInfo(it) }
                    view.lastTestedOnLine?.let { SmallInfo(it) }
                    view.environmentNote?.let { SmallInfo(it) }
                }
            }
        }

        // ----- Configuration form -----
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Configuration", style = MaterialTheme.typography.bodyMedium)

                // Provider dropdown.
                Text("Provider", style = MaterialTheme.typography.bodySmall)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { providerMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedOption.label, modifier = Modifier.fillMaxWidth())
                    }
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false },
                    ) {
                        AI_PROVIDER_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    providerMenuOpen = false
                                    if (option.slug != providerSlug) {
                                        providerSlug = option.slug
                                        // Prefill a sensible default model on switch
                                        // when the field is empty; never touch the key.
                                        if (model.isBlank()) model = option.defaultModel
                                        if (!option.usesBaseUrl) baseUrl = ""
                                    }
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (showBaseUrl) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    placeholder = {
                        Text(
                            if (liveStatus?.keyPresent == true) {
                                "Leave blank to keep the stored key"
                            } else {
                                "Paste the provider API key"
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                SmallInfo(
                    "The key is sent to your Mimeo server only. It is never stored on " +
                        "this device and never shown again.",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        }

        // ----- Action result message -----
        result?.let { actionResult ->
            SmallInfo(aiProviderResultMessage(actionResult))
        }

        // ----- Actions -----
        val canSave = !busy && model.isNotBlank() &&
            (!showBaseUrl || baseUrl.isNotBlank() || liveStatus?.configured == true)
        val canTest = !busy && liveStatus?.configured == true

        Button(
            onClick = {
                vm.saveAiProviderConfig(aiProviderSaveRequest(currentForm()))
                apiKey = ""
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Save")
            }
        }

        OutlinedButton(
            onClick = {
                vm.testAiProviderConfig()
                apiKey = ""
            },
            enabled = canTest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test")
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            enabled = !busy && liveStatus?.configured == true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear configuration")
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear provider configuration?") },
            text = {
                Text(
                    "This removes the AI provider configuration stored on your " +
                        "Mimeo server. Summaries will stop working until it is set up again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteAiProviderConfig()
                    apiKey = ""
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Map a coarse [AiProviderEditResult] to display-safe copy. */
internal fun aiProviderResultMessage(result: AiProviderEditResult): String = when (result) {
    AiProviderEditResult.Saved -> AI_PROVIDER_SAVE_SUCCESS
    AiProviderEditResult.Tested -> AI_PROVIDER_TEST_COMPLETE
    AiProviderEditResult.Cleared -> AI_PROVIDER_DELETE_SUCCESS
    is AiProviderEditResult.Failed -> aiProviderErrorMessage(result.code)
}

@Composable
private fun SmallInfo(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
