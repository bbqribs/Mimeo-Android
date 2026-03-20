package com.mimeo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTokenStorageControllerTest {
    @Test
    fun readToken_prefersSecureTokenWhenPresent() {
        val secure = FakeTokenSlot("secure-token")
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { secure },
            legacySlot = legacy,
        )

        assertEquals("secure-token", controller.readToken())
    }

    @Test
    fun readToken_usesLegacyWhenSecureUnavailable() {
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { null },
            legacySlot = legacy,
        )

        assertEquals("legacy-token", controller.readToken())
    }

    @Test
    fun writeToken_writesSecureAndClearsLegacyWhenAvailable() {
        val secure = FakeTokenSlot()
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { secure },
            legacySlot = legacy,
        )

        val result = controller.writeToken("next-token")

        assertFalse(result.usedLegacyFallback)
        assertEquals("next-token", secure.token)
        assertEquals("", legacy.token)
    }

    @Test
    fun writeToken_fallsBackToLegacyWhenSecureUnavailable() {
        val legacy = FakeTokenSlot()
        val controller = AuthTokenStorageController(
            secureSlotProvider = { null },
            legacySlot = legacy,
        )

        val result = controller.writeToken("next-token")

        assertTrue(result.usedLegacyFallback)
        assertEquals("next-token", legacy.token)
    }

    @Test
    fun clearToken_clearsBothSecureAndLegacy() {
        val secure = FakeTokenSlot("secure-token")
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { secure },
            legacySlot = legacy,
        )

        controller.clearToken()

        assertEquals("", secure.token)
        assertEquals("", legacy.token)
    }

    @Test
    fun migrateLegacyToken_prefersSecureWhenAvailable() {
        val secure = FakeTokenSlot()
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { secure },
            legacySlot = legacy,
        )

        val result = controller.migrateLegacyToken(legacy.readToken())

        assertFalse(result.usedLegacyFallback)
        assertEquals("legacy-token", secure.token)
        assertEquals("", legacy.token)
    }

    @Test
    fun migrateLegacyToken_usesLegacyFallbackWhenSecureUnavailable() {
        val legacy = FakeTokenSlot("legacy-token")
        val controller = AuthTokenStorageController(
            secureSlotProvider = { null },
            legacySlot = legacy,
        )

        val result = controller.migrateLegacyToken(legacy.readToken())

        assertTrue(result.usedLegacyFallback)
        assertEquals("legacy-token", legacy.token)
    }

    private class FakeTokenSlot(initial: String = "") : AuthTokenSlot {
        var token: String = initial

        override fun readToken(): String = token

        override fun writeToken(token: String) {
            this.token = token.trim()
        }

        override fun clearToken() {
            token = ""
        }
    }
}
