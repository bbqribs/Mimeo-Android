package com.mimeo.android.ui.signin

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ConnectionMode
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SignInState {
    data object Idle : SignInState
    data object Loading : SignInState
    data class Error(val message: String) : SignInState
}

internal fun inferConnectionModeForBaseUrl(baseUrl: String): ConnectionMode {
    val host = parseHost(baseUrl)
    if (host.isBlank()) return ConnectionMode.LAN
    if (host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost") {
        return ConnectionMode.LOCAL
    }
    if (isCarrierGradeNat(host)) {
        return ConnectionMode.REMOTE
    }
    return ConnectionMode.LAN
}

internal fun resolveSignInErrorMessage(error: Throwable): String {
    return when (error) {
        is ApiException -> {
            val detail = extractApiDetail(error.message)
            when (error.statusCode) {
                401 -> "Invalid username or password"
                429 -> detail ?: "Too many login attempts. Please wait before trying again."
                400 -> detail ?: "Sign-in failed. Check your server URL and credentials."
                in 500..599 -> "Sign-in failed. Please try again."
                else -> detail ?: "Could not reach server. Check the URL and your network connection."
            }
        }
        is IOException -> "Could not reach server. Check the URL and your network connection."
        else -> "Could not reach server. Check the URL and your network connection."
    }
}

internal fun buildAuthDeviceName(manufacturer: String, model: String): String {
    val trimmedManufacturer = manufacturer.trim()
    val trimmedModel = model.trim()
    return when {
        trimmedManufacturer.isBlank() -> trimmedModel.ifBlank { "Android" }
        trimmedModel.isBlank() -> trimmedManufacturer
        trimmedModel.startsWith(trimmedManufacturer, ignoreCase = true) -> trimmedModel
        else -> "$trimmedManufacturer $trimmedModel"
    }
}

private fun parseHost(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        URI(trimmed).host.orEmpty()
    }.getOrElse {
        trimmed.removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
    }.lowercase(Locale.US)
}

private fun isCarrierGradeNat(host: String): Boolean {
    if (!host.startsWith("100.")) return false
    val octets = host.split('.')
    if (octets.size != 4) return false
    val secondOctet = octets.getOrNull(1)?.toIntOrNull() ?: return false
    return secondOctet in 64..127
}

private fun extractApiDetail(message: String?): String? {
    val raw = message?.substringAfter(": ", missingDelimiterValue = message)?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        Json.parseToJsonElement(raw).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
