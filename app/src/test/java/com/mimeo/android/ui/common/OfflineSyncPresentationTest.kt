package com.mimeo.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineSyncPresentationTest {

    private val now = 1_000_000_000L
    private fun minutes(n: Long) = n * 60_000L
    private fun hours(n: Long) = n * 60L * 60_000L
    private fun days(n: Long) = n * 24L * 60L * 60_000L

    @Test
    fun `age formats across minute hour and day boundaries`() {
        assertEquals("just now", formatLastSyncAge(now - 30_000L, now))
        assertEquals("1m ago", formatLastSyncAge(now - minutes(1), now))
        assertEquals("59m ago", formatLastSyncAge(now - minutes(59), now))
        assertEquals("1h ago", formatLastSyncAge(now - hours(1), now))
        assertEquals("23h ago", formatLastSyncAge(now - hours(23), now))
        assertEquals("1d ago", formatLastSyncAge(now - days(1), now))
        assertEquals("9d ago", formatLastSyncAge(now - days(9), now))
    }

    @Test
    fun `never-synced and invalid timestamps have no age`() {
        assertNull(formatLastSyncAge(null, now))
        assertNull(formatLastSyncAge(0L, now))
        assertNull(formatLastSyncAge(-5L, now))
    }

    @Test
    fun `future timestamp from clock skew reads as just now`() {
        assertEquals("just now", formatLastSyncAge(now + hours(2), now))
    }

    @Test
    fun `label is identical online and offline so only the icon changes state`() {
        // The indicator is always present; connectivity is carried by the icon, not the text.
        val expected = "Last sync: 3h ago"
        assertEquals(expected, syncIndicatorLabel(lastSyncAtMs = now - hours(3), nowMs = now))
    }

    @Test
    fun `label keeps a prefix rather than a bare age`() {
        assertEquals("Last sync: just now", syncIndicatorLabel(lastSyncAtMs = now - 10_000L, nowMs = now))
        assertEquals("Last sync: 2d ago", syncIndicatorLabel(lastSyncAtMs = now - days(2), nowMs = now))
    }

    @Test
    fun `never-synced account reports so instead of an empty age`() {
        assertEquals("Never synced", syncIndicatorLabel(lastSyncAtMs = null, nowMs = now))
        assertEquals("Never synced", syncIndicatorLabel(lastSyncAtMs = 0L, nowMs = now))
    }

    @Test
    fun `icon state is announced for screen readers`() {
        assertEquals("Offline", syncIndicatorStateDescription(isOffline = true))
        assertEquals("Connected", syncIndicatorStateDescription(isOffline = false))
    }
}
