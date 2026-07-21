package com.mimeo.android.ui.common

import com.mimeo.android.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedIdentityPresentationTest {

    @Test
    fun signedInPresentationUsesTheActiveSessionUsernameAndCanonicalOrigin() {
        val presentation = authenticatedIdentityPresentation(
            AppSettings(
                baseUrl = " HTTPS://Reader.Example.com:8443/ignored?query=value#fragment ",
                apiToken = "device-token",
                authenticatedUsername = "  alice  ",
            ),
        )

        assertTrue(presentation.isSignedIn)
        assertEquals("alice", presentation.usernameDisplay)
        assertEquals("https://reader.example.com:8443", presentation.endpointDisplay)
        assertEquals("Signed in", presentation.authenticationState)
    }

    @Test
    fun signedOutPresentationNeverRetainsAccountOrEndpoint() {
        val presentation = authenticatedIdentityPresentation(
            AppSettings(
                baseUrl = "https://reader.example.com",
                apiToken = "",
                authenticatedUsername = "alice",
            ),
        )

        assertFalse(presentation.isSignedIn)
        assertNull(presentation.username)
        assertNull(presentation.canonicalEndpointOrigin)
        assertEquals("Not signed in", presentation.usernameDisplay)
        assertEquals("Signed out", presentation.authenticationState)
    }

    @Test
    fun manualTokenDoesNotPretendToKnowTheAccount() {
        val presentation = authenticatedIdentityPresentation(
            AppSettings(baseUrl = "https://reader.example.com", apiToken = "manual-token"),
        )

        assertTrue(presentation.isSignedIn)
        assertEquals("Unavailable (manual token)", presentation.usernameDisplay)
    }

    @Test
    fun originNeverIncludesPathQueryOrFragment() {
        assertEquals(
            "https://reader.example.com",
            canonicalEndpointOrigin("https://Reader.Example.com/path?token=secret#fragment"),
        )
    }
}
