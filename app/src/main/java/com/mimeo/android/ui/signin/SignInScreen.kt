package com.mimeo.android.ui.signin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SignInScreen(
    initialServerUrl: String,
    initialAutoDownloadEnabled: Boolean,
    signInState: SignInState,
    onSignIn: (serverUrl: String, username: String, password: String) -> Unit,
    onAutoDownloadChanged: (Boolean) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onClearError: () -> Unit,
) {
    var serverPreset by rememberSaveable { mutableStateOf(inferSignInPreset(defaultSignInServerUrl(initialServerUrl))) }
    var scheme by rememberSaveable { mutableStateOf(inferSignInScheme(defaultSignInServerUrl(initialServerUrl))) }
    var serverUrl by rememberSaveable(initialServerUrl) {
        mutableStateOf(buildPresetServerUrl(serverPreset, scheme, defaultSignInServerUrl(initialServerUrl)))
    }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var autoDownloadEnabled by rememberSaveable(initialAutoDownloadEnabled) { mutableStateOf(initialAutoDownloadEnabled) }
    val loading = signInState is SignInState.Loading
    val errorMessage = (signInState as? SignInState.Error)?.message
    val focusManager = LocalFocusManager.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    fun submit() {
        val trimmedUrl = serverUrl.trim()
        val trimmedUsername = username.trim()
        if (loading || trimmedUrl.isBlank() || trimmedUsername.isBlank() || password.isBlank()) return
        focusManager.clearFocus(force = true)
        onSignIn(trimmedUrl, trimmedUsername, password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Sign in to Mimeo",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Enter your server URL and account credentials to get a device token for this app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Server",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SignInServerPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = serverPreset == preset,
                            onClick = {
                                serverPreset = preset
                                serverUrl = buildPresetServerUrl(serverPreset, scheme, serverUrl)
                                if (errorMessage != null) onClearError()
                            },
                            enabled = !loading,
                            label = { Text(preset.label()) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SignInUrlScheme.entries.forEach { nextScheme ->
                        FilterChip(
                            selected = scheme == nextScheme,
                            onClick = {
                                scheme = nextScheme
                                serverUrl = buildPresetServerUrl(serverPreset, scheme, serverUrl)
                                if (errorMessage != null) onClearError()
                            },
                            enabled = !loading,
                            label = { Text(nextScheme.value.uppercase()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        serverPreset = SignInServerPreset.MANUAL
                        if (errorMessage != null) onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server URL") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { usernameFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        if (errorMessage != null) onClearError()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester),
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (errorMessage != null) onClearError()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Auto-download saved articles",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Applies immediately after successful sign-in too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoDownloadEnabled,
                        enabled = !loading,
                        onCheckedChange = {
                            autoDownloadEnabled = it
                            onAutoDownloadChanged(it)
                        },
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { submit() },
                    enabled = !loading && serverUrl.trim().isNotBlank() && username.trim().isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Signing In...")
                        }
                    } else {
                        Text("Sign In")
                    }
                }
                Text(
                    text = "Advanced settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !loading, onClick = onOpenAdvancedSettings),
                )
            }
        }
    }
}

private fun SignInServerPreset.label(): String = when (this) {
    SignInServerPreset.REMOTE -> "Remote"
    SignInServerPreset.LAN -> "LAN"
    SignInServerPreset.MANUAL -> "Manual"
}
