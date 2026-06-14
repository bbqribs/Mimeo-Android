package com.mimeo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerIdentityGuardTest {

    // normalizeServerIdentity

    @Test
    fun normalizeServerIdentity_trimsLeadingAndTrailingWhitespace() {
        assertEquals("http://example.com", normalizeServerIdentity("  http://example.com  "))
    }

    @Test
    fun normalizeServerIdentity_stripsTrailingSlash() {
        assertEquals("http://example.com", normalizeServerIdentity("http://example.com/"))
    }

    @Test
    fun normalizeServerIdentity_stripsMultipleTrailingSlashes() {
        assertEquals("http://example.com", normalizeServerIdentity("http://example.com///"))
    }

    @Test
    fun normalizeServerIdentity_lowercasesSchemeAndHost() {
        assertEquals("http://example.com", normalizeServerIdentity("HTTP://EXAMPLE.COM"))
    }

    @Test
    fun normalizeServerIdentity_preservesPath() {
        assertEquals("http://example.com/mimeo", normalizeServerIdentity("http://example.com/mimeo"))
    }

    @Test
    fun normalizeServerIdentity_preservesPort() {
        assertEquals("http://192.168.1.1:8000", normalizeServerIdentity("http://192.168.1.1:8000"))
    }

    @Test
    fun normalizeServerIdentity_handlesEmptyString() {
        assertEquals("", normalizeServerIdentity(""))
    }

    // detectServerIdentityMismatch

    @Test
    fun detectServerIdentityMismatch_blankStoredIdentity_returnsFalse() {
        assertFalse(detectServerIdentityMismatch("", "http://example.com"))
    }

    @Test
    fun detectServerIdentityMismatch_whitespaceOnlyStoredIdentity_returnsFalse() {
        assertFalse(detectServerIdentityMismatch("   ", "http://example.com"))
    }

    @Test
    fun detectServerIdentityMismatch_sameIdentity_returnsFalse() {
        assertFalse(
            detectServerIdentityMismatch("http://example.com", "http://example.com"),
        )
    }

    @Test
    fun detectServerIdentityMismatch_differentHost_returnsTrue() {
        assertTrue(
            detectServerIdentityMismatch("http://server-a.com", "http://server-b.com"),
        )
    }

    @Test
    fun detectServerIdentityMismatch_differentPort_returnsTrue() {
        assertTrue(
            detectServerIdentityMismatch("http://192.168.1.1:8000", "http://192.168.1.1:9000"),
        )
    }

    @Test
    fun detectServerIdentityMismatch_differentScheme_returnsTrue() {
        assertTrue(
            detectServerIdentityMismatch("http://example.com", "https://example.com"),
        )
    }

    @Test
    fun detectServerIdentityMismatch_normalizedEquivalents_returnsFalse() {
        val stored = normalizeServerIdentity("HTTP://EXAMPLE.COM/")
        val incoming = normalizeServerIdentity("http://example.com")
        assertFalse(detectServerIdentityMismatch(stored, incoming))
    }

    @Test
    fun detectServerIdentityMismatch_trailingSlashDifference_returnsFalse() {
        val stored = normalizeServerIdentity("http://example.com/")
        val incoming = normalizeServerIdentity("http://example.com")
        assertFalse(detectServerIdentityMismatch(stored, incoming))
    }
}
