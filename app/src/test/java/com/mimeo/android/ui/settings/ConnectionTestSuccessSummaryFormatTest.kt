package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTestSuccessSummaryFormatTest {
    @Test
    fun `summary includes mode host timestamp and git sha when present`() {
        val snapshot = ConnectionTestSuccessSnapshot(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            gitSha = "abc1234",
            succeededAtMs = 1_700_000_000_000L,
        )

        val summary = formatConnectionTestSuccessSummary(snapshot)

        assertTrue(summary.contains("Remote"))
        assertTrue(summary.contains("Remote:"))
        assertTrue(summary.contains("100.101.102.103"))
        assertTrue(summary.contains("git_sha=abc1234"))
        assertTrue(!summary.contains("•"))
    }

    @Test
    fun `summary omits git sha suffix when unavailable`() {
        val snapshot = ConnectionTestSuccessSnapshot(
            mode = ConnectionMode.LAN,
            baseUrl = "http://192.168.68.124:8000",
            gitSha = null,
            succeededAtMs = 1_700_000_000_000L,
        )

        val summary = formatConnectionTestSuccessSummary(snapshot)

        assertTrue(summary.contains("LAN"))
        assertTrue(summary.contains("192.168.68.124"))
        assertTrue(!summary.contains("git_sha="))
    }

    @Test
    fun `snapshot default timestamp is stable sentinel`() {
        val snapshot = ConnectionTestSuccessSnapshot(
            mode = ConnectionMode.LOCAL,
            baseUrl = "http://10.0.2.2:8000",
        )

        assertEquals(0L, snapshot.succeededAtMs)
    }

    @Test
    fun `current status reports match when mode and base url align`() {
        val snapshot = ConnectionTestSuccessSnapshot(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000/",
            gitSha = "abc123",
            succeededAtMs = 1_700_000_000_000L,
        )

        val summary = formatCurrentConnectionStatusSummary(
            mode = ConnectionMode.REMOTE,
            selectedBaseUrl = "http://100.101.102.103:8000",
            snapshot = snapshot,
        )

        assertTrue(summary.contains("matches last successful target"))
        assertTrue(summary.contains("git_sha=abc123"))
    }

    @Test
    fun `current status reports mismatch when selected target differs`() {
        val snapshot = ConnectionTestSuccessSnapshot(
            mode = ConnectionMode.LAN,
            baseUrl = "http://192.168.68.124:8000",
            succeededAtMs = 1_700_000_000_000L,
        )

        val summary = formatCurrentConnectionStatusSummary(
            mode = ConnectionMode.LAN,
            selectedBaseUrl = "http://192.168.68.200:8000",
            snapshot = snapshot,
        )

        assertTrue(summary.contains("differs from last successful target"))
        assertTrue(summary.contains("192.168.68.124"))
    }

    @Test
    fun `normalize base url trims case and trailing slash`() {
        val normalized = normalizeConnectionBaseUrl(" HTTP://EXAMPLE.COM:8000/ ")
        assertEquals("http://example.com:8000", normalized)
    }
}
