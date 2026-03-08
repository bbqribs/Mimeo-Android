package com.mimeo.android.share

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareRecentDuplicateDetectorTest {
    @After
    fun tearDown() {
        ShareRecentDuplicateDetector.clearForTest()
    }

    @Test
    fun `detects duplicate for same destination within window`() {
        val key = "android-share-abc123"
        val now = 1_000L

        assertFalse(ShareRecentDuplicateDetector.wasSeenRecently(key, playlistId = null, nowMillis = now))

        ShareRecentDuplicateDetector.remember(key, playlistId = null, nowMillis = now)

        assertTrue(ShareRecentDuplicateDetector.wasSeenRecently(key, playlistId = null, nowMillis = now + 1))
    }

    @Test
    fun `does not cross destinations`() {
        val key = "android-share-abc123"
        val now = 1_000L

        ShareRecentDuplicateDetector.remember(key, playlistId = null, nowMillis = now)

        assertFalse(ShareRecentDuplicateDetector.wasSeenRecently(key, playlistId = 7, nowMillis = now + 1))
    }

    @Test
    fun `expires entries after duplicate window`() {
        val key = "android-share-abc123"
        val now = 1_000L

        ShareRecentDuplicateDetector.remember(key, playlistId = null, nowMillis = now)

        assertFalse(
            ShareRecentDuplicateDetector.wasSeenRecently(
                key,
                playlistId = null,
                nowMillis = now + ShareRecentDuplicateDetector.WINDOW_MILLIS + 1,
            )
        )
    }
}
