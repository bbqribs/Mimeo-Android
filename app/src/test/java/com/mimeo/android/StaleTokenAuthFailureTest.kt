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
        assertFalse(isStaleTokenAuthFailure(ApiException(500, "Server error")))
        assertFalse(isStaleTokenAuthFailure(IllegalStateException("boom")))
    }

    @Test
    fun exposesSessionExpiredMessage() {
        assertEquals("Session expired. Please sign in again.", staleTokenSignInMessage())
    }
}
