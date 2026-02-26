package com.mimeo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenDiagnostics: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }
    var autoAdvance by remember(settings.autoAdvanceOnCompletion) {
        mutableStateOf(settings.autoAdvanceOnCompletion)
    }
    var autoScrollWhileListening by remember(settings.autoScrollWhileListening) {
        mutableStateOf(settings.autoScrollWhileListening)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Button(onClick = {
                        vm.saveSettings(baseUrl, token, autoAdvance, autoScrollWhileListening)
                    }) { Text("Save") }
                    Button(onClick = {
                        vm.saveSettings(baseUrl, token, autoAdvance, autoScrollWhileListening)
                        vm.testConnection()
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
