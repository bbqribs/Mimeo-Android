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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mimeo.android.data.ServerIdentityGuardState
import com.mimeo.android.ui.common.LoadStatePane
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SignInScreen(
    initialServerUrl: String,
    initialAutoDownloadEnabled: Boolean,
    signInState: SignInState,
    serverIdentityGuardState: ServerIdentityGuardState = ServerIdentityGuardState.Idle,
    onSignIn: (serverUrl: String, username: String, password: String) -> Unit,
    onAutoDownloadChanged: (Boolean) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onClearError: () -> Unit,
    onConfirmClearAndSignIn: () -> Unit = {},
    onDismissServerIdentityGuard: () -> Unit = {},
) {
    val availablePresets = remember { availableSignInPresets() }
    var serverPreset by rememberSaveable { mutableStateOf(inferSignInPreset(defaultSignInServerUrl(initialServerUrl))) }
    var scheme by rememberSaveable { mutableStateOf(inferSignInScheme(defaultSignInServerUrl(initialServerUrl))) }
    var serverUrl by rememberSaveable(initialServerUrl) {
        mutableStateOf(buildPresetServerUrl(serverPreset, scheme, defaultSignInServerUrl(initialServerUrl)))
    }
    var username by rememberSaveable { mutableStateOf("") }
    // Deliberately not rememberSaveable: the plaintext password must not be written
    // into the saved-instance-state bundle across process recreation.
    var password by remember { mutableStateOf("") }
    var autoDownloadEnabled by rememberSaveable(initialAutoDownloadEnabled) { mutableStateOf(initialAutoDownloadEnabled) }
    val loading = signInState is SignInState.Loading
    val errorMessage = (signInState as? SignInState.Error)?.message
    val focusManager = LocalFocusManager.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current

    // A failed attempt keeps the username but never redisplays the password.
    LaunchedEffect(signInState) {
        if (signInState is SignInState.Error) password = ""
    }

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
                    text = "Sign in with the username and password for your household Mimeo account. " +
                        "The server URL and your credentials are separate: the URL says where the service lives, " +
                        "and your account was created for you by the household operator - the app can't create accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Server",
                    style = MaterialTheme.typography.labelMedium,
                )
                if (availablePresets.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availablePresets.forEach { preset ->
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
                }
                if (availablePresets.size > 1) {
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
                    placeholder = { Text("https://your-server[:port]") },
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
                        .focusRequester(usernameFocusRequester)
                        .semantics { contentType = ContentType.Username },
                    label = { Text("Username") },
                    supportingText = { Text("The short name assigned by your household operator - not an email address.") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = signInUsernameKeyboardOptions(),
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
                        .focusRequester(passwordFocusRequester)
                        .semantics { contentType = ContentType.Password },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = signInPasswordKeyboardOptions(),
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
                LoadStatePane(error = errorMessage?.takeIf { it.isNotBlank() })
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
                val welcomeUrl = buildWelcomeUrl(serverUrl)
                if (welcomeUrl != null) {
                    Text(
                        text = "Set up or recover your account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !loading) { uriHandler.openUri(welcomeUrl) },
                    )
                    Text(
                        text = "Opens the service's welcome page in your browser, where a setup or reset code from the operator is redeemed and you choose your password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "To set up or recover your account, enter your server URL above, then open the service's welcome page in a browser and redeem the setup or reset code from your operator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

    if (serverIdentityGuardState is ServerIdentityGuardState.AwaitingConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissServerIdentityGuard,
            title = { Text("Different Mimeo server") },
            text = {
                Text(
                    "This looks like a different Mimeo server.\n\n" +
                        "Clear local cached items and pending progress before continuing?",
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmClearAndSignIn) {
                    Text("Clear and continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissServerIdentityGuard) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun SignInServerPreset.label(): String = when (this) {
    SignInServerPreset.REMOTE -> "Remote"
    SignInServerPreset.LAN -> "LAN"
    SignInServerPreset.MANUAL -> "Manual"
}
