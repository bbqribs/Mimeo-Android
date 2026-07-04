package com.mimeo.android.ui.settings

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.DeviceSession
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface DevicesListState {
    data object Idle : DevicesListState
    data object Loading : DevicesListState
    data class Success(val devices: List<DeviceSession>) : DevicesListState
    data class Error(val message: String) : DevicesListState
}

data class DeviceActionResolution(
    val message: String,
    val staleAuth: Boolean = false,
)

internal fun resolveDevicesListError(error: Throwable): DeviceActionResolution {
    return when (error) {
        is ApiException -> when (error.statusCode) {
            401 -> DeviceActionResolution(
                message = "Session expired. Please sign in again.",
                staleAuth = true,
            )
            403 -> DeviceActionResolution(
                message = "Devices & sessions isn't available for this sign-in method.",
            )
            404 -> DeviceActionResolution(
                message = "Devices & sessions isn't available on this server yet.",
            )
            in 500..599 -> DeviceActionResolution(
                message = "Couldn't load your devices. Please try again.",
            )
            else -> DeviceActionResolution(
                message = extractApiDetail(error.message) ?: "Couldn't load your devices. Please try again.",
            )
        }
        is IOException -> DeviceActionResolution(
            message = "Couldn't reach server. Check your connection and try again.",
        )
        else -> DeviceActionResolution(
            message = "Couldn't load your devices. Please try again.",
        )
    }
}

internal fun resolveRevokeDeviceError(error: Throwable): DeviceActionResolution {
    return when (error) {
        is ApiException -> when (error.statusCode) {
            400 -> DeviceActionResolution(
                message = "That's your current session. Use Sign Out instead.",
            )
            401 -> DeviceActionResolution(
                message = "Session expired. Please sign in again.",
                staleAuth = true,
            )
            403 -> DeviceActionResolution(
                message = "Devices & sessions isn't available for this sign-in method.",
            )
            404 -> DeviceActionResolution(
                message = "That session is no longer available.",
            )
            in 500..599 -> DeviceActionResolution(
                message = "Couldn't sign out that device. Please try again.",
            )
            else -> DeviceActionResolution(
                message = extractApiDetail(error.message) ?: "Couldn't sign out that device. Please try again.",
            )
        }
        is IOException -> DeviceActionResolution(
            message = "Couldn't reach server. Check your connection and try again.",
        )
        else -> DeviceActionResolution(
            message = "Couldn't sign out that device. Please try again.",
        )
    }
}

internal fun resolveRevokeOtherDevicesError(error: Throwable): DeviceActionResolution {
    return when (error) {
        is ApiException -> when (error.statusCode) {
            401 -> DeviceActionResolution(
                message = "Session expired. Please sign in again.",
                staleAuth = true,
            )
            403 -> DeviceActionResolution(
                message = "Devices & sessions isn't available for this sign-in method.",
            )
            404 -> DeviceActionResolution(
                message = "Devices & sessions isn't available on this server yet.",
            )
            in 500..599 -> DeviceActionResolution(
                message = "Couldn't sign out other sessions. Please try again.",
            )
            else -> DeviceActionResolution(
                message = extractApiDetail(error.message) ?: "Couldn't sign out other sessions. Please try again.",
            )
        }
        is IOException -> DeviceActionResolution(
            message = "Couldn't reach server. Check your connection and try again.",
        )
        else -> DeviceActionResolution(
            message = "Couldn't sign out other sessions. Please try again.",
        )
    }
}

/** Whether the "Sign out everywhere else" action has anything to act on. */
internal fun hasRevocableOtherDevices(devices: List<DeviceSession>): Boolean = devices.any { !it.isCurrent }

internal fun revokeOtherDevicesSuccessMessage(revokedCount: Int): String {
    return when (revokedCount) {
        0 -> "No other sessions to sign out."
        1 -> "Signed out 1 other session."
        else -> "Signed out $revokedCount other sessions."
    }
}

/** Renders an ISO 8601 timestamp as a short human-readable date, or a dash when absent/unparseable. */
internal fun formatDeviceTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        val normalized = iso.replace("Z", "+00:00")
        val dt = java.time.OffsetDateTime.parse(normalized)
        dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
    }.getOrDefault(iso.take(10))
}

private fun extractApiDetail(message: String?): String? {
    val raw = message?.substringAfter(": ", missingDelimiterValue = message)?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        Json.parseToJsonElement(raw).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
