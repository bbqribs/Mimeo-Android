package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModeGuidanceCopyTest {
    @Test
    fun `remote base url guidance mentions tailscale and lan distinction`() {
        val message = connectionModeBaseUrlGuidance(ConnectionMode.REMOTE)

        assertTrue(message.contains("Tailscale", ignoreCase = true))
        assertTrue(message.contains(".ts.net", ignoreCase = true))
        assertTrue(message.contains("LAN mode", ignoreCase = true))
        assertTrue(message.contains("HTTPS-first", ignoreCase = true))
        assertTrue(message.contains("beh-august2015.taildacac5.ts.net", ignoreCase = true))
        assertTrue(message.contains("fallback", ignoreCase = true))
    }

    @Test
    fun `lan guidance mentions lan ip and http default`() {
        val message = connectionModeBaseUrlGuidance(ConnectionMode.LAN)

        assertTrue(message.contains("<LAN-IP>", ignoreCase = true))
        assertTrue(message.contains("http://", ignoreCase = true))
    }

    @Test
    fun `remote token guidance references remote target consistency`() {
        val message = connectionModeTokenGuidance(ConnectionMode.REMOTE)

        assertTrue(
            message.contains("same remote server", ignoreCase = true) ||
                message.contains("token", ignoreCase = true),
        )
    }

    @Test
    fun `device token scope hint distinguishes read-only from read-write`() {
        val hint = deviceTokenScopeHint()

        assertTrue(hint.contains("read-only", ignoreCase = true))
        assertTrue(hint.contains("read-write", ignoreCase = true))
        assertTrue(hint.contains("Bluesky", ignoreCase = true))
        assertTrue(hint.contains("saves", ignoreCase = true))
    }
}
