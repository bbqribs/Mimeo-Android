package com.mimeo.android.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.ConnectivityDiagnosticOutcome

@Composable
fun ConnectivityDiagnosticsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val rows by vm.diagnosticsRows.collectAsState()
    val running by vm.diagnosticsRunning.collectAsState()
    val lastError by vm.diagnosticsLastError.collectAsState()
    val isPhysicalDevice = !isProbablyEmulator()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Connectivity diagnostics", style = MaterialTheme.typography.titleMedium)
        Text("Base URL: ${settings.baseUrl.ifBlank { "(unset)" }}")
        Text("Token: ${if (settings.apiToken.isBlank()) "missing" else "present"}")
        if (isPhysicalDevice) {
            Text("Physical device: use http://<PC_LAN_IP>:8000 (10.0.2.2 is emulator-only).")
        } else {
            Text("Emulator: use http://10.0.2.2:8000 for host machine backend.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runConnectivityDiagnostics(isPhysicalDevice) }, enabled = !running) {
                Text(if (running) "Running..." else "Run diagnostics")
            }
        }

        if (lastError != null) {
            Text("Last error: $lastError", color = MaterialTheme.colorScheme.error)
        }

        rows.forEach { row ->
            val color = when (row.outcome) {
                ConnectivityDiagnosticOutcome.PASS -> MaterialTheme.colorScheme.primary
                ConnectivityDiagnosticOutcome.FAIL -> MaterialTheme.colorScheme.error
                ConnectivityDiagnosticOutcome.INFO -> MaterialTheme.colorScheme.secondary
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("[${row.outcome}] ${row.name} @ ${row.checkedAt}", color = color)
                Text("url=${row.url}", style = MaterialTheme.typography.bodySmall)
                Text(row.detail, style = MaterialTheme.typography.bodySmall)
                if (!row.hint.isNullOrBlank()) {
                    Text("hint=${row.hint}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun isProbablyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val product = Build.PRODUCT.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val device = Build.DEVICE.lowercase()
    return fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("emulator") ||
        product.contains("sdk") ||
        manufacturer.contains("genymotion") ||
        (brand.startsWith("generic") && device.startsWith("generic"))
}
