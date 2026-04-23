package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModeGuidanceCopyTest {
    @Test
    fun `remote base url guidance mentions tailscale and lan distinction`() {
        val message = connectionModeBaseUrlGuidance(ConnectionMode.REMOTE)

        assertTrue(message.contains("Tailscale", ignoreCase = true) || message.contains("VPN", ignoreCase = true))
        assertTrue(message.contains("LAN mode", ignoreCase = true))
        assertTrue(message.contains("HTTPS", ignoreCase = true))
    }

    @Test
    fun `lan guidance mentions lan ip example`() {
        val message = connectionModeBaseUrlGuidance(ConnectionMode.LAN)

        assertTrue(message.contains("192.168", ignoreCase = true))
    }

    @Test
    fun `remote token guidance references remote target consistency`() {
        val message = connectionModeTokenGuidance(ConnectionMode.REMOTE)

        assertTrue(
            message.contains("same remote server", ignoreCase = true) ||
                message.contains("token", ignoreCase = true),
        )
    }
}
