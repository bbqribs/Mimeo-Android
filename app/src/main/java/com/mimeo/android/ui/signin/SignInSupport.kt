package com.mimeo.android.ui.signin

import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.DEFAULT_LAN_HOST
import com.mimeo.android.model.DEFAULT_LOCAL_BASE_URL
import com.mimeo.android.model.DEFAULT_REMOTE_HOST
import com.mimeo.android.model.inferConnectionModeForHost
import java.io.IOException
import java.util.Locale
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SignInState {
    data object Idle : SignInState
    data object Loading : SignInState
    data class Error(val message: String) : SignInState
}

enum class SignInServerPreset {
    REMOTE,
    LAN,
    MANUAL,
}

enum class SignInUrlScheme(val value: String) {
    HTTP("http"),
    HTTPS("https"),
}

internal const val DEFAULT_REMOTE_SIGN_IN_HOST = DEFAULT_REMOTE_HOST
internal const val DEFAULT_LAN_SIGN_IN_HOST = DEFAULT_LAN_HOST
private const val DEFAULT_LOCAL_SIGN_IN_URL = DEFAULT_LOCAL_BASE_URL

internal fun inferConnectionModeForBaseUrl(baseUrl: String): ConnectionMode {
    return inferConnectionModeForHost(baseUrl)
}

internal fun resolveSignInErrorMessage(error: Throwable): String {
    if (looksLikeSchemeOrTlsMismatch(error)) {
        return "Probable URL scheme/security mismatch. Remote/hosted is HTTPS-first; for Tailscale IP without endpoint TLS use HTTP, or use HTTPS with a .ts.net/hosted URL."
    }
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
        is IOException -> "Could not reach server. Check URL scheme (http/https), certificate trust, and network."
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

internal fun defaultSignInServerUrl(initialServerUrl: String): String {
    val trimmed = initialServerUrl.trim()
    return if (trimmed.isBlank() || trimmed == DEFAULT_LOCAL_SIGN_IN_URL) {
        buildPresetServerUrl(
            SignInServerPreset.REMOTE,
            if (isCarrierGradeNatHost(DEFAULT_REMOTE_SIGN_IN_HOST.substringBefore(':'))) SignInUrlScheme.HTTP else SignInUrlScheme.HTTPS,
            manualUrl = "",
        )
    } else {
        trimmed
    }
}

internal fun inferSignInPreset(serverUrl: String): SignInServerPreset {
    val normalized = normalizeServerUrl(serverUrl)
    return when (normalized) {
        normalizeServerUrl(buildPresetServerUrl(SignInServerPreset.REMOTE, SignInUrlScheme.HTTP, ""))
        -> SignInServerPreset.REMOTE
        normalizeServerUrl(buildPresetServerUrl(SignInServerPreset.REMOTE, SignInUrlScheme.HTTPS, ""))
        -> SignInServerPreset.REMOTE
        normalizeServerUrl(buildPresetServerUrl(SignInServerPreset.LAN, SignInUrlScheme.HTTP, ""))
        -> SignInServerPreset.LAN
        normalizeServerUrl(buildPresetServerUrl(SignInServerPreset.LAN, SignInUrlScheme.HTTPS, ""))
        -> SignInServerPreset.LAN
        else -> SignInServerPreset.MANUAL
    }
}

internal fun inferSignInScheme(serverUrl: String): SignInUrlScheme {
    return if (serverUrl.trim().startsWith("https://", ignoreCase = true)) {
        SignInUrlScheme.HTTPS
    } else {
        SignInUrlScheme.HTTP
    }
}

internal fun buildPresetServerUrl(
    preset: SignInServerPreset,
    scheme: SignInUrlScheme,
    manualUrl: String,
): String {
    val normalizedManual = manualUrl.trim()
    return when (preset) {
        SignInServerPreset.REMOTE -> "${scheme.value}://$DEFAULT_REMOTE_SIGN_IN_HOST"
        SignInServerPreset.LAN -> "${scheme.value}://$DEFAULT_LAN_SIGN_IN_HOST"
        SignInServerPreset.MANUAL -> normalizeManualServerUrl(normalizedManual, scheme)
    }
}

private fun normalizeManualServerUrl(manualUrl: String, scheme: SignInUrlScheme): String {
    if (manualUrl.isBlank()) return ""
    return if (manualUrl.startsWith("http://", ignoreCase = true) || manualUrl.startsWith("https://", ignoreCase = true)) {
        manualUrl
    } else {
        "${scheme.value}://$manualUrl"
    }
}

private fun normalizeServerUrl(serverUrl: String): String {
    return serverUrl.trim().trimEnd('/').lowercase(Locale.US)
}

private fun extractApiDetail(message: String?): String? {
    val raw = message?.substringAfter(": ", missingDelimiterValue = message)?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        Json.parseToJsonElement(raw).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun looksLikeSchemeOrTlsMismatch(error: Throwable): Boolean {
    if (error is SSLException) return true
    val fullMessage = generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(separator = " ")
        .lowercase(Locale.US)
    if (fullMessage.isBlank()) return false
    return fullMessage.contains("cleartxt") ||
        fullMessage.contains("cleartext") ||
        fullMessage.contains("ssl") ||
        fullMessage.contains("tls") ||
        fullMessage.contains("handshake")
}

private fun isCarrierGradeNatHost(host: String): Boolean {
    if (!host.startsWith("100.")) return false
    val octets = host.split('.')
    if (octets.size != 4) return false
    val secondOctet = octets.getOrNull(1)?.toIntOrNull() ?: return false
    return secondOctet in 64..127
}
