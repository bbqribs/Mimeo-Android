package com.mimeo.android

import com.mimeo.android.data.ApiException
import com.mimeo.android.repository.ItemTextPrefetchAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoActiveContentStateTest {

    @Test
    fun identifiesNoActiveContentApiException() {
        assertTrue(isNoActiveContentError(ApiException(409, """HTTP 409: {"detail":"No active content"}""")))
        assertFalse(isNoActiveContentError(ApiException(409, """HTTP 409: {"detail":"Duplicate"}""")))
        assertFalse(isNoActiveContentError(ApiException(500, "HTTP 500")))
    }

    @Test
    fun identifiesNoActiveContentPrefetchAttempt() {
        assertTrue(
            isNoActiveContentAttempt(
                ItemTextPrefetchAttempt(
                    itemId = 23,
                    success = false,
                    errorSummary = """api:409:HTTP 409: {"detail":"No active content"}""",
                    retryable = false,
                ),
            ),
        )
        assertFalse(
            isNoActiveContentAttempt(
                ItemTextPrefetchAttempt(
                    itemId = 24,
                    success = false,
                    errorSummary = "network:timeout",
                    retryable = true,
                ),
            ),
        )
    }

    @Test
    fun exposesConciseOfflineMessage() {
        assertEquals("Not available for offline reading", noActiveContentOfflineMessage())
    }
}
