package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionEndpointValidationTest {
    @Test
    fun `rejects blank and malformed urls`() {
        val blank = validateConnectionEndpoint(ConnectionMode.REMOTE, "")
        assertEquals("Base URL is required.", blank.blockingError)

        val malformed = validateConnectionEndpoint(ConnectionMode.REMOTE, "http://")
        assertEquals("Enter a valid URL like http://host:8000.", malformed.blockingError)
    }

    @Test
    fun `rejects unsupported scheme and path query fragment`() {
        val ftp = validateConnectionEndpoint(ConnectionMode.LAN, "ftp://example.com:8000")
        assertEquals("Use http:// or https://.", ftp.blockingError)

        val path = validateConnectionEndpoint(ConnectionMode.LAN, "http://example.com:8000/api")
        assertEquals("Use base host only (no path).", path.blockingError)

        val query = validateConnectionEndpoint(ConnectionMode.LAN, "http://example.com:8000?x=1")
        assertEquals("Remove query/fragment from the base URL.", query.blockingError)
    }

    @Test
    fun `lan mode rejects loopback and emulator hosts`() {
        val loopback = validateConnectionEndpoint(ConnectionMode.LAN, "http://127.0.0.1:8000")
        assertEquals(
            "LAN mode needs your server LAN IP (for example http://192.168.x.y:8000).",
            loopback.blockingError,
        )

        val emulator = validateConnectionEndpoint(ConnectionMode.LAN, "http://10.0.2.2:8000")
        assertEquals(
            "LAN mode needs your server LAN IP (for example http://192.168.x.y:8000).",
            emulator.blockingError,
        )
    }

    @Test
    fun `remote mode rejects loopback and warns on lan ip`() {
        val loopback = validateConnectionEndpoint(ConnectionMode.REMOTE, "http://localhost:8000")
        assertEquals(
            "Remote mode needs a Tailscale/VPN or remote host URL, not localhost/emulator loopback.",
            loopback.blockingError,
        )

        val lan = validateConnectionEndpoint(ConnectionMode.REMOTE, "http://192.168.1.10:8000")
        assertNull(lan.blockingError)
        assertTrue(
            lan.warnings.any { it.contains("Remote mode is using a LAN IP", ignoreCase = true) },
        )
    }

    @Test
    fun `remote http warns for non-tailnet non-lan hosts`() {
        val publicHttp = validateConnectionEndpoint(ConnectionMode.REMOTE, "http://example.com:8000")
        assertNull(publicHttp.blockingError)
        assertTrue(publicHttp.warnings.any { it.contains("Use HTTPS", ignoreCase = true) })
    }

    @Test
    fun `remote tailnet http remains allowed without https warning`() {
        val tailnet = validateConnectionEndpoint(ConnectionMode.REMOTE, "http://100.93.62.125:8000")
        assertNull(tailnet.blockingError)
        assertTrue(tailnet.warnings.none { it.contains("Use HTTPS", ignoreCase = true) })
    }

    @Test
    fun `lan mode warns when using ts dot net host`() {
        val validation = validateConnectionEndpoint(ConnectionMode.LAN, "http://beh-dec2022.taildacac5.ts.net:8000")

        assertNull(validation.blockingError)
        assertTrue(
            validation.warnings.any { it.contains("Remote/Tailscale target", ignoreCase = true) },
        )
    }
}
