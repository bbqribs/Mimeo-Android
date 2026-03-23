package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectivityDiagnosticOutcome
import com.mimeo.android.model.ConnectivityDiagnosticRow
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityDiagnosticsExportTest {
    @Test
    fun `export text includes required fields and endpoint rows`() {
        val rows = listOf(
            ConnectivityDiagnosticRow(
                name = "health",
                url = "http://10.0.2.2:8000/health",
                outcome = ConnectivityDiagnosticOutcome.PASS,
                detail = "status=200",
                hint = null,
                checkedAt = "2026-03-23T11:00:00Z",
            ),
            ConnectivityDiagnosticRow(
                name = "debug/version",
                url = "http://10.0.2.2:8000/debug/version",
                outcome = ConnectivityDiagnosticOutcome.FAIL,
                detail = "status=500",
                hint = "backend error; check logs",
                checkedAt = "2026-03-23T11:00:05Z",
            ),
        )

        val text = buildConnectivityDiagnosticsExportText(
            mode = ConnectionMode.LOCAL,
            baseUrl = "http://10.0.2.2:8000",
            rows = rows,
            lastError = "backend error",
            exportedAt = "2026-03-23T11:00:09Z",
        )

        assertTrue(text.contains("exported_at: 2026-03-23T11:00:09Z"))
        assertTrue(text.contains("mode: LOCAL"))
        assertTrue(text.contains("base_url: http://10.0.2.2:8000"))
        assertTrue(text.contains("summary: rows=2, pass=1, fail=1, info=0"))
        assertTrue(text.contains("error: backend error"))
        assertTrue(text.contains("[PASS] health @ 2026-03-23T11:00:00Z"))
        assertTrue(text.contains("[FAIL] debug/version @ 2026-03-23T11:00:05Z"))
        assertTrue(text.contains("hint: backend error; check logs"))
    }

    @Test
    fun `export text is stable when diagnostics list is empty`() {
        val text = buildConnectivityDiagnosticsExportText(
            mode = ConnectionMode.REMOTE,
            baseUrl = "",
            rows = emptyList(),
            lastError = null,
            exportedAt = "2026-03-23T11:12:00Z",
        )

        assertTrue(text.contains("mode: REMOTE"))
        assertTrue(text.contains("base_url: (unset)"))
        assertTrue(text.contains("summary: rows=0, pass=0, fail=0, info=0"))
        assertTrue(text.contains("error: none"))
        assertTrue(text.contains("endpoint_results:\n- (none)"))
    }
}

