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

    @Test
    fun `defaults blank and local sign in url to remote http preset`() {
        assertEquals(
            "http://100.93.62.125:8000",
            defaultSignInServerUrl(""),
        )
        assertEquals(
            "http://100.93.62.125:8000",
            defaultSignInServerUrl("http://10.0.2.2:8000"),
        )
    }

    @Test
    fun `builds preset urls for remote lan and manual entry`() {
        assertEquals(
            "http://100.93.62.125:8000",
            buildPresetServerUrl(SignInServerPreset.REMOTE, SignInUrlScheme.HTTP, ""),
        )
        assertEquals(
            "https://192.168.68.124:8000",
            buildPresetServerUrl(SignInServerPreset.LAN, SignInUrlScheme.HTTPS, ""),
        )
        assertEquals(
            "http://example.com:8000",
            buildPresetServerUrl(SignInServerPreset.MANUAL, SignInUrlScheme.HTTP, "example.com:8000"),
        )
    }

    @Test
    fun `infers preset and scheme from sign in url`() {
        assertEquals(
            SignInServerPreset.REMOTE,
            inferSignInPreset("https://100.93.62.125:8000"),
        )
        assertEquals(
            SignInServerPreset.LAN,
            inferSignInPreset("http://192.168.68.124:8000"),
        )
        assertEquals(
            SignInServerPreset.MANUAL,
            inferSignInPreset("https://mimeo.example.com"),
        )
        assertEquals(
            SignInUrlScheme.HTTPS,
            inferSignInScheme("https://100.93.62.125:8000"),
        )
    }
}
