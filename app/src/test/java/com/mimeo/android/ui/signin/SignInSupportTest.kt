package com.mimeo.android.ui.signin

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SignInSupportTest {
    @Test
    fun `infers connection mode from common base urls`() {
        assertEquals(ConnectionMode.LOCAL, inferConnectionModeForBaseUrl("http://10.0.2.2:8000"))
        assertEquals(ConnectionMode.LAN, inferConnectionModeForBaseUrl("http://192.168.68.124:8000"))
        assertEquals(ConnectionMode.REMOTE, inferConnectionModeForBaseUrl("http://100.88.12.4:8000"))
        assertEquals(ConnectionMode.LAN, inferConnectionModeForBaseUrl("https://example.com"))
    }

    @Test
    fun `resolves sign in errors to bounded user messages`() {
        assertEquals(
            "Invalid username or password",
            resolveSignInErrorMessage(ApiException(401, """HTTP 401: {"detail":"Invalid username or password"}""")),
        )
        assertEquals(
            "Too many login attempts. Please wait 5 minutes before trying again.",
            resolveSignInErrorMessage(ApiException(429, """HTTP 429: {"detail":"Too many login attempts. Please wait 5 minutes before trying again."}""")),
        )
        assertEquals(
            "Could not reach server. Check the URL and your network connection.",
            resolveSignInErrorMessage(IOException("timeout")),
        )
    }
}
