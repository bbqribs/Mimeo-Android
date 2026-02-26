package com.mimeo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenDiagnostics: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }
    var autoAdvance by remember(settings.autoAdvanceOnCompletion) {
        mutableStateOf(settings.autoAdvanceOnCompletion)
    }
    var autoScrollWhileListening by remember(settings.autoScrollWhileListening) {
        mutableStateOf(settings.autoScrollWhileListening)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var testRequested by remember { mutableStateOf(false) }

    fun saveCurrent() {
        vm.saveSettings(baseUrl, token, autoAdvance, autoScrollWhileListening)
    }

    LaunchedEffect(statusMessage, testRequested) {
        if (!testRequested) return@LaunchedEffect
        val message = statusMessage.orEmpty()
        if (message.equals("Settings saved", ignoreCase = true)) {
            return@LaunchedEffect
        }
        if (message.startsWith("Connected")) {
            testRequested = false
            snackbarHostState.showSnackbar("Connected", duration = SnackbarDuration.Short)
            return@LaunchedEffect
        }
        if (message.contains("Token required", ignoreCase = true)) {
            testRequested = false
            snackbarHostState.showSnackbar("Token required", duration = SnackbarDuration.Short)
            return@LaunchedEffect
        }
        if (message.isNotBlank()) {
            testRequested = false
            val result = snackbarHostState.showSnackbar(
                message = "Cannot reach server",
                actionLabel = "Diagnostics",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onOpenDiagnostics()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Connection")
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveCurrent() }) { Text("Save") }
                        Button(onClick = {
                            saveCurrent()
                            if (token.isBlank()) {
                                testRequested = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Token required",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            } else {
                                testRequested = true
                                vm.testConnection()
                            }
                        }) { Text("Test") }
                        Button(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Playback")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Auto-advance on completion")
                        Switch(
                            checked = autoAdvance,
                            onCheckedChange = { autoAdvance = it },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Auto-scroll while listening")
                        Switch(
                            checked = autoScrollWhileListening,
                            onCheckedChange = { autoScrollWhileListening = it },
                        )
                    }
                    Text("Emulator default: http://10.0.2.2:8000")
                }
            }
        }
    }
}
