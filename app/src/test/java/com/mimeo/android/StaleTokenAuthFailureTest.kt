package com.mimeo.android

import com.mimeo.android.data.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleTokenAuthFailureTest {

    @Test
    fun treatsOnly401AsStaleTokenFailure() {
        assertTrue(isStaleTokenAuthFailure(ApiException(401, "Unauthorized-check token")))
        assertFalse(isStaleTokenAuthFailure(ApiException(403, "Forbidden")))
        assertFalse(isStaleTokenAuthFailure(ApiException(400, "Bad request")))
        assertFalse(isStaleTokenAuthFailure(ApiException(500, "Server error")))
        assertFalse(isStaleTokenAuthFailure(IllegalStateException("boom")))
    }

    @Test
    fun staleTokenFailureIgnoresMessageTextWhenStatusIs401() {
        assertTrue(isStaleTokenAuthFailure(ApiException(401, "")))
        assertTrue(isStaleTokenAuthFailure(ApiException(401, "expired")))
    }

    @Test
    fun exposesSessionExpiredMessage() {
        assertEquals("Session expired. Please sign in again.", staleTokenSignInMessage())
    }

    @Test
    fun staleTokenResolutionClearsTokenAndNavigatesToSignInWhenTokenPresent() {
        val resolution = resolveStaleTokenAuthFailure(
            error = ApiException(401, "Unauthorized-check token"),
            currentToken = "stored-token",
        )

        assertTrue(resolution.handled)
        assertTrue(resolution.clearToken)
        assertEquals("Session expired. Please sign in again.", resolution.signInMessage)
        assertEquals(ROUTE_SIGN_IN, resolution.navigationRoute)
    }

    @Test
    fun staleTokenResolutionTreatsExpiredTokenLikeAnyOther401() {
        val resolution = resolveStaleTokenAuthFailure(
            error = ApiException(401, "expired"),
            currentToken = "stored-token",
        )

        assertTrue(resolution.handled)
        assertTrue(resolution.clearToken)
        assertEquals(ROUTE_SIGN_IN, resolution.navigationRoute)
    }

    @Test
    fun staleTokenResolutionDoesNotRequestAnotherRedirectWhenTokenAlreadyBlank() {
        val resolution = resolveStaleTokenAuthFailure(
            error = ApiException(401, "Unauthorized-check token"),
            currentToken = "",
        )

        assertTrue(resolution.handled)
        assertFalse(resolution.clearToken)
        assertEquals(null, resolution.signInMessage)
        assertEquals(null, resolution.navigationRoute)
    }

    @Test
    fun staleTokenResolutionIgnoresNon401Failures() {
        val resolution = resolveStaleTokenAuthFailure(
            error = ApiException(403, "Forbidden"),
            currentToken = "stored-token",
        )

        assertFalse(resolution.handled)
        assertFalse(resolution.clearToken)
        assertEquals(null, resolution.signInMessage)
        assertEquals(null, resolution.navigationRoute)
    }
}
