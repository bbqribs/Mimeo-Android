package com.mimeo.android.ui.settings

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.DeviceSession
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesSupportTest {

    @Test
    fun `list load maps stale auth and known statuses`() {
        val stale = resolveDevicesListError(ApiException(401, "HTTP 401: Unauthorized"))
        assertEquals("Session expired. Please sign in again.", stale.message)
        assertTrue(stale.staleAuth)

        val forbidden = resolveDevicesListError(ApiException(403, "HTTP 403: Forbidden"))
        assertEquals("Devices & sessions isn't available for this sign-in method.", forbidden.message)
        assertFalse(forbidden.staleAuth)

        val notFound = resolveDevicesListError(ApiException(404, "HTTP 404: Not Found"))
        assertEquals("Devices & sessions isn't available on this server yet.", notFound.message)

        val serverError = resolveDevicesListError(ApiException(500, "HTTP 500: boom"))
        assertEquals("Couldn't load your devices. Please try again.", serverError.message)

        val network = resolveDevicesListError(IOException("timeout"))
        assertEquals("Couldn't reach server. Check your connection and try again.", network.message)
    }

    @Test
    fun `revoke device refuses current session with plain copy`() {
        val refusal = resolveRevokeDeviceError(
            ApiException(400, """HTTP 400: {"detail":"Cannot revoke the token used for this request. Sign out instead."}"""),
        )
        assertEquals("That's your current session. Use Sign Out instead.", refusal.message)
        assertFalse(refusal.staleAuth)
    }

    @Test
    fun `revoke device maps not found and stale auth`() {
        val notFound = resolveRevokeDeviceError(ApiException(404, """HTTP 404: {"detail":"Device token not found"}"""))
        assertEquals("That session is no longer available.", notFound.message)
        assertFalse(notFound.staleAuth)

        val stale = resolveRevokeDeviceError(ApiException(401, "HTTP 401: Unauthorized"))
        assertTrue(stale.staleAuth)
        assertEquals("Session expired. Please sign in again.", stale.message)
    }

    @Test
    fun `revoke others maps statuses and success copy`() {
        val stale = resolveRevokeOtherDevicesError(ApiException(401, "HTTP 401: Unauthorized"))
        assertTrue(stale.staleAuth)

        val serverError = resolveRevokeOtherDevicesError(ApiException(500, "HTTP 500: boom"))
        assertEquals("Couldn't sign out other sessions. Please try again.", serverError.message)

        assertEquals("No other sessions to sign out.", revokeOtherDevicesSuccessMessage(0))
        assertEquals("Signed out 1 other session.", revokeOtherDevicesSuccessMessage(1))
        assertEquals("Signed out 3 other sessions.", revokeOtherDevicesSuccessMessage(3))
    }

    @Test
    fun `formats and defaults timestamps safely`() {
        assertEquals("—", formatDeviceTimestamp(null))
        assertEquals("—", formatDeviceTimestamp(""))
        assertEquals("not-a-date", formatDeviceTimestamp("not-a-date"))
        assertTrue(formatDeviceTimestamp("2026-01-15T10:30:00Z").contains("2026"))
    }

    @Test
    fun `sign out everywhere else is only actionable when another device exists`() {
        val onlyCurrent = listOf(sampleDevice(id = 1, isCurrent = true))
        assertFalse(hasRevocableOtherDevices(onlyCurrent))

        val currentPlusOther = listOf(
            sampleDevice(id = 1, isCurrent = true),
            sampleDevice(id = 2, isCurrent = false),
        )
        assertTrue(hasRevocableOtherDevices(currentPlusOther))

        assertFalse(hasRevocableOtherDevices(emptyList()))
    }

    @Test
    fun `empty and error list states are distinguishable and stable`() {
        val empty = DevicesListState.Success(emptyList())
        assertTrue(empty.devices.isEmpty())
        assertEquals(DevicesListState.Success(emptyList()), empty)

        val error = DevicesListState.Error("Couldn't load your devices. Please try again.")
        assertEquals("Couldn't load your devices. Please try again.", error.message)
        assertEquals(DevicesListState.Loading, DevicesListState.Loading)
        assertEquals(DevicesListState.Idle, DevicesListState.Idle)
    }

    private fun sampleDevice(id: Int, isCurrent: Boolean) = DeviceSession(
        id = id,
        name = "device-$id",
        tokenPrefix = "prefix$id",
        isCurrent = isCurrent,
    )
}
