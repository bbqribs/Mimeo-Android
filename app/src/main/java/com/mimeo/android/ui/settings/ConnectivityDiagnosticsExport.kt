package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectivityDiagnosticOutcome
import com.mimeo.android.model.ConnectivityDiagnosticRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun buildConnectivityDiagnosticsExportText(
    mode: ConnectionMode,
    baseUrl: String,
    rows: List<ConnectivityDiagnosticRow>,
    lastError: String?,
    exportedAt: String = diagnosticsExportTimestampNow(),
): String {
    val passCount = rows.count { it.outcome == ConnectivityDiagnosticOutcome.PASS }
    val failCount = rows.count { it.outcome == ConnectivityDiagnosticOutcome.FAIL }
    val infoCount = rows.count { it.outcome == ConnectivityDiagnosticOutcome.INFO }
    val stableCount = rows.count { it.detail.contains("class=stable") }
    val flakyCount = rows.count { it.detail.contains("class=flaky") }
    val downCount = rows.count { it.detail.contains("class=down") }
    val safeBaseUrl = baseUrl.ifBlank { "(unset)" }
    val safeError = lastError?.takeIf { it.isNotBlank() } ?: "none"

    val lines = mutableListOf<String>()
    lines += "Mimeo Android connectivity diagnostics"
    lines += "exported_at: $exportedAt"
    lines += "mode: ${mode.name}"
    lines += "base_url: $safeBaseUrl"
    lines += "summary: rows=${rows.size}, pass=$passCount, fail=$failCount, info=$infoCount"
    if (stableCount + flakyCount + downCount > 0) {
        lines += "path_quality: stable=$stableCount, flaky=$flakyCount, down=$downCount"
    }
    lines += "error: $safeError"
    lines += "endpoint_results:"

    if (rows.isEmpty()) {
        lines += "- (none)"
    } else {
        rows.forEachIndexed { index, row ->
            lines += "${index + 1}. [${row.outcome}] ${row.name} @ ${row.checkedAt}"
            lines += "   url: ${row.url}"
            lines += "   detail: ${row.detail}"
            lines += "   hint: ${row.hint?.ifBlank { "(none)" } ?: "(none)"}"
        }
    }

    return lines.joinToString("\n")
}

internal fun diagnosticsExportTimestampNow(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

