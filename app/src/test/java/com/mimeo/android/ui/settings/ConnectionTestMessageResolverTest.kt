package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTestMessageResolverTest {
    @Test
    fun `api unauthorized maps to token rejected message`() {
        val message = ConnectionTestMessageResolver.forApiFailure(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            statusCode = 401,
            message = "Unauthorized",
        )

        assertEquals(
            "Token rejected. It may be expired or invalid. Create a new device token and update Settings.",
            message,
        )
    }

    @Test
    fun `api unauthorized in lan keeps generic token guidance`() {
        val message = ConnectionTestMessageResolver.forApiFailure(
            mode = ConnectionMode.LAN,
            baseUrl = "http://192.168.68.124:8000",
            statusCode = 401,
            message = "Unauthorized",
        )

        assertEquals("Token rejected. Check API token.", message)
    }

    @Test
    fun `remote unreachable suggests tailscale check`() {
        val message = ConnectionTestMessageResolver.forException(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            message = "failed to connect",
        )

        assertTrue(message.contains("Backend unreachable."))
        assertTrue(message.contains("Tailscale/VPN"))
    }

    @Test
    fun `remote mode with lan host hints wrong host type`() {
        val message = ConnectionTestMessageResolver.forException(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://192.168.68.124:8000",
            message = "unable to resolve host",
        )

        assertTrue(message.contains("Remote mode usually needs Tailscale/VPN URL"))
    }

    @Test
    fun `lan mode with loopback host provides lan guidance`() {
        val message = ConnectionTestMessageResolver.forException(
            mode = ConnectionMode.LAN,
            baseUrl = "http://127.0.0.1:8000",
            message = "connection refused",
        )

        assertTrue(message.contains("LAN mode needs your server LAN IP"))
    }

    @Test
    fun `connected in remote mode with lan ip includes mode mismatch hint`() {
        val message = ConnectionTestMessageResolver.connected(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://192.168.68.124:8000",
            gitSha = "abc123",
        )

        assertTrue(message.contains("Connected git_sha=abc123"))
        assertTrue(message.contains("Remote mode is using a LAN IP"))
    }

    @Test
    fun `connected in remote mode with tailscale address stays clean`() {
        val message = ConnectionTestMessageResolver.connected(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            gitSha = "abc123",
        )

        assertEquals("Connected git_sha=abc123", message)
    }

    @Test
    fun `diagnostics remote timeout on tailnet host indicates tailnet path failure`() {
        val message = ConnectionTestMessageResolver.forDiagnosticsException(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            message = "connect timed out",
        )

        assertTrue(message.contains("Timeout/no route"))
        assertTrue(message.contains("tailnet-path failure"))
    }

    @Test
    fun `diagnostics remote timeout on lan host indicates wrong mode or base url`() {
        val message = ConnectionTestMessageResolver.forDiagnosticsException(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://192.168.1.30:8000",
            message = "failed to connect",
        )

        assertTrue(message.contains("wrong-mode/base URL issue"))
    }

    @Test
    fun `diagnostics exception classifies scheme mismatch`() {
        val message = ConnectionTestMessageResolver.forDiagnosticsException(
            mode = ConnectionMode.REMOTE,
            baseUrl = "https://100.101.102.103:8000",
            message = "CLEARTEXT communication to host not permitted",
        )

        assertTrue(message.contains("scheme mismatch"))
    }

    @Test
    fun `diagnostics http failure classifies auth failure`() {
        val message = ConnectionTestMessageResolver.forDiagnosticsHttpFailure(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            path = "/health",
            statusCode = 401,
        )

        assertTrue(message.contains("Token rejected"))
    }

    @Test
    fun `diagnostics debug endpoint 404 indicates probable wrong mode base url issue`() {
        val message = ConnectionTestMessageResolver.forDiagnosticsHttpFailure(
            mode = ConnectionMode.REMOTE,
            baseUrl = "http://100.101.102.103:8000",
            path = "/debug/version",
            statusCode = 404,
        )

        assertTrue(message.contains("wrong-mode/base URL issue"))
    }
}
