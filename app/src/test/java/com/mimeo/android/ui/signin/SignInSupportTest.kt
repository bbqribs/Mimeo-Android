package com.mimeo.android.ui.signin

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // Credential failure carries the setup reminder because the backend
        // intentionally answers setup-pending accounts with the same generic 401
        // (no account enumeration), so the classes are not contractually
        // distinguishable client-side.
        assertEquals(
            "Invalid username or password. If your account hasn't been set up yet, finish setup on the service's /welcome page first.",
            resolveSignInErrorMessage(ApiException(401, """HTTP 401: {"detail":"Invalid username or password"}""")),
        )
        assertEquals(
            "Too many login attempts. Please wait 5 minutes before trying again.",
            resolveSignInErrorMessage(ApiException(429, """HTTP 429: {"detail":"Too many login attempts. Please wait 5 minutes before trying again."}""")),
        )
        assertEquals(
            "Couldn't reach the server. Check the server URL (http or https), certificate trust, and your network connection.",
            resolveSignInErrorMessage(IOException("timeout")),
        )
    }

    @Test
    fun `distinguishes server unavailability from connection failures`() {
        assertEquals(
            "The server is temporarily unavailable. Try again in a moment.",
            resolveSignInErrorMessage(ApiException(503, "HTTP 503: upstream down")),
        )
        assertEquals(
            "Couldn't reach the server. Check the server URL and your network connection.",
            resolveSignInErrorMessage(RuntimeException("unexpected")),
        )
    }

    @Test
    fun `username keyboard disables capitalization and autocorrect`() {
        val options = signInUsernameKeyboardOptions()
        assertEquals(androidx.compose.ui.text.input.KeyboardCapitalization.None, options.capitalization)
        assertEquals(false, options.autoCorrectEnabled)
    }

    @Test
    fun `builds welcome url only from validated server origins`() {
        assertEquals(
            "https://beh-august2015.taildacac5.ts.net/welcome",
            buildWelcomeUrl("https://beh-august2015.taildacac5.ts.net"),
        )
        assertEquals(
            "https://beh-august2015.taildacac5.ts.net/welcome",
            buildWelcomeUrl("https://beh-august2015.taildacac5.ts.net/"),
        )
        assertEquals(
            "http://192.168.68.124:8000/welcome",
            buildWelcomeUrl("http://192.168.68.124:8000"),
        )
    }

    @Test
    fun `refuses welcome url for invalid or unsafe server input`() {
        assertEquals(null, buildWelcomeUrl(""))
        assertEquals(null, buildWelcomeUrl("   "))
        assertEquals(null, buildWelcomeUrl("ftp://example.com"))
        assertEquals(null, buildWelcomeUrl("https://example.com/some/path"))
        assertEquals(null, buildWelcomeUrl("https://example.com?query=1"))
        assertEquals(null, buildWelcomeUrl("not a url"))
        assertEquals(null, buildWelcomeUrl("javascript:alert(1)"))
    }

    @Test
    fun `defaults blank and local sign in url to remote preset for configured host type`() {
        assertEquals(
            "https://beh-august2015.taildacac5.ts.net",
            defaultSignInServerUrl(""),
        )
        assertEquals(
            "https://beh-august2015.taildacac5.ts.net",
            defaultSignInServerUrl("http://10.0.2.2:8000"),
        )
    }

    @Test
    fun `builds preset urls for remote lan and manual entry`() {
        assertEquals(
            "https://beh-august2015.taildacac5.ts.net",
            buildPresetServerUrl(SignInServerPreset.REMOTE, SignInUrlScheme.HTTPS, ""),
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
    fun `available presets always offer manual entry and preserve stored urls`() {
        // Manual entry is always available regardless of build variant.
        assertTrue(availableSignInPresets().contains(SignInServerPreset.MANUAL))
        // An already-configured server URL is preserved as the sign-in default.
        assertEquals(
            "https://reader.example.com",
            defaultSignInServerUrl("https://reader.example.com"),
        )
    }

    @Test
    fun `maps cleartext and tls sign in failures to scheme guidance`() {
        assertEquals(
            "Probable URL scheme/security mismatch. Remote is HTTPS-first with .ts.net; fallback HTTP is http://100.84.13.10:8000 when endpoint TLS is disabled.",
            resolveSignInErrorMessage(IOException("CLEARTEXT communication to host not permitted by network security policy")),
        )
        assertEquals(
            "Probable URL scheme/security mismatch. Remote is HTTPS-first with .ts.net; fallback HTTP is http://100.84.13.10:8000 when endpoint TLS is disabled.",
            resolveSignInErrorMessage(IOException("SSLHandshakeException: handshake failed")),
        )
    }

    @Test
    fun `infers preset and scheme from sign in url`() {
        assertEquals(
            SignInServerPreset.REMOTE,
            inferSignInPreset("https://beh-august2015.taildacac5.ts.net"),
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
            inferSignInScheme("https://beh-august2015.taildacac5.ts.net"),
        )
    }
}
