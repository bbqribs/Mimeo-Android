package com.mimeo.android.ui.settings

import com.mimeo.android.data.ApiException
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val PASSWORD_CHANGE_MIN_LENGTH = 8

sealed interface PasswordChangeState {
    data object Idle : PasswordChangeState
    data object Submitting : PasswordChangeState
    data class Error(val message: String) : PasswordChangeState
    data class Success(val message: String) : PasswordChangeState
}

data class PasswordChangeResolution(
    val message: String,
    val staleAuth: Boolean = false,
)

internal fun validatePasswordChangeInput(
    currentPassword: String,
    newPassword: String,
    confirmNewPassword: String,
): String? {
    if (currentPassword.isBlank()) return "Enter your current password"
    if (newPassword.isBlank()) return "Enter a new password"
    if (confirmNewPassword.isBlank()) return "Confirm the new password"
    if (newPassword != confirmNewPassword) return "New password and confirmation must match"
    if (newPassword.length < PASSWORD_CHANGE_MIN_LENGTH) {
        return "New password must be at least $PASSWORD_CHANGE_MIN_LENGTH characters"
    }
    return null
}

internal fun passwordChangeSuccessMessage(): String {
    return "Password changed. Other sessions were signed out. This device stays signed in."
}

internal fun resolvePasswordChangeError(error: Throwable): PasswordChangeResolution {
    return when (error) {
        is ApiException -> {
            val detail = extractApiDetail(error.message)
            when (error.statusCode) {
                400, 422 -> PasswordChangeResolution(
                    message = detail ?: "Check the password fields and try again.",
                )
                401 -> {
                    val wrongCurrentPassword = detail.orEmpty().contains("current password", ignoreCase = true) ||
                        detail.orEmpty().contains("old password", ignoreCase = true) ||
                        detail.orEmpty().contains("old_password", ignoreCase = true) ||
                        detail.orEmpty().contains("incorrect password", ignoreCase = true)
                    if (wrongCurrentPassword) {
                        PasswordChangeResolution(message = "Current password is incorrect")
                    } else {
                        PasswordChangeResolution(
                            message = "Session expired. Please sign in again.",
                            staleAuth = true,
                        )
                    }
                }
                in 500..599 -> PasswordChangeResolution(
                    message = "Couldn't change password. Please try again.",
                )
                else -> PasswordChangeResolution(
                    message = detail ?: "Couldn't change password. Please try again.",
                )
            }
        }
        is IOException -> PasswordChangeResolution(
            message = "Couldn't change password. Check your connection and try again.",
        )
        else -> PasswordChangeResolution(
            message = "Couldn't change password. Please try again.",
        )
    }
}

private fun extractApiDetail(message: String?): String? {
    val raw = message?.substringAfter(": ", missingDelimiterValue = message)?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        Json.parseToJsonElement(raw).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
