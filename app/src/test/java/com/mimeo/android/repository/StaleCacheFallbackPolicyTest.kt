package com.mimeo.android.repository

import com.mimeo.android.data.ApiException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleCacheFallbackPolicyTest {

    @Test
    fun ioFailure_allowsStaleFallback() {
        assertTrue(shouldAllowStaleCacheFallback(IOException("offline")))
    }

    @Test
    fun serverFailure_allowsStaleFallback() {
        assertTrue(shouldAllowStaleCacheFallback(ApiException(503, "Service unavailable")))
    }

    @Test
    fun clientFailure_blocksStaleFallback() {
        assertFalse(shouldAllowStaleCacheFallback(ApiException(404, "Not found")))
    }

    @Test
    fun unknownFailure_blocksStaleFallback() {
        assertFalse(shouldAllowStaleCacheFallback(IllegalStateException("boom")))
    }
}
