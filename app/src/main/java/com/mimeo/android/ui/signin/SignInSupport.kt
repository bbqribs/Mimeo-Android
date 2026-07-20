package com.mimeo.android.ui.signin

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.DEVELOPER_PRESETS_AVAILABLE
import com.mimeo.android.model.DEFAULT_LAN_HOST
import com.mimeo.android.model.DEFAULT_LOCAL_BASE_URL
import com.mimeo.android.model.DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL
import com.mimeo.android.model.DEFAULT_REMOTE_HOST
import com.mimeo.android.model.inferConnectionModeForHost
import com.mimeo.android.ui.settings.validateConnectionEndpoint
import java.io.IOException
import java.net.URI
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
        return "Probable URL scheme/security mismatch. Remote is HTTPS-first with .ts.net; fallback HTTP is $DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL when endpoint TLS is disabled."
    }
    return when (error) {
        is ApiException -> {
            val detail = extractApiDetail(error.message)
            when (error.statusCode) {
                // The backend intentionally answers setup-pending accounts with the same
                // generic 401 as a wrong password (no account enumeration), so the setup
                // reminder rides along with the credential message instead of being its
                // own error class.
                401 -> "Invalid username or password. If your account hasn't been set up yet, finish setup on the service's /welcome page first."
                429 -> detail ?: "Too many sign-in attempts. Please wait before trying again."
                400 -> detail ?: "Sign-in failed. Check your server URL and credentials."
                in 500..599 -> "The server is temporarily unavailable. Try again in a moment."
                else -> detail ?: "Couldn't reach the server. Check the server URL and your network connection."
            }
        }
        is IOException -> "Couldn't reach the server. Check the server URL (http or https), certificate trust, and your network connection."
        else -> "Couldn't reach the server. Check the server URL and your network connection."
    }
}

/**
 * Keyboard behavior for the username field: usernames are exact identifiers, so
 * auto-capitalization and autocorrect must be off.
 */
internal fun signInUsernameKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
    imeAction = ImeAction.Next,
)

internal fun signInPasswordKeyboardOptions(): KeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Done,
)

/**
 * Build the browser handoff URL for account setup/recovery from the user-entered
 * server URL, or return null when no safe URL can be derived.
 *
 * Reuses the existing endpoint validation (scheme http/https, host required, no
 * path/query/fragment) and then reconstructs the URL strictly from the validated
 * scheme + host + port, so the handoff can only ever open `<configured origin>/welcome`.
 */
internal fun buildWelcomeUrl(rawServerUrl: String): String? {
    val trimmed = rawServerUrl.trim()
    if (trimmed.isBlank()) return null
    val mode = inferConnectionModeForBaseUrl(trimmed)
    if (validateConnectionEndpoint(mode, trimmed).blockingError != null) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
    val host = uri.host?.trim().orEmpty()
    if (host.isBlank()) return null
    val port = if (uri.port != -1) ":${uri.port}" else ""
    return "$scheme://$host$port/welcome"
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

/**
 * Server presets offered on the sign-in screen. Release builds expose only manual entry so no
 * personal backend host identity is presented as a default/preset choice; debug builds keep the
 * full developer presets.
 */
internal fun availableSignInPresets(): List<SignInServerPreset> {
    return if (DEVELOPER_PRESETS_AVAILABLE) {
        SignInServerPreset.entries.toList()
    } else {
        listOf(SignInServerPreset.MANUAL)
    }
}

internal fun defaultSignInServerUrl(initialServerUrl: String): String {
    val trimmed = initialServerUrl.trim()
    if (!DEVELOPER_PRESETS_AVAILABLE) {
        // Manual-first: preserve any stored URL for already-configured installs; otherwise start
        // blank so the user enters their own server URL. No personal preset is seeded.
        return trimmed
    }
    return if (trimmed.isBlank() || trimmed == DEFAULT_LOCAL_SIGN_IN_URL) {
        buildPresetServerUrl(
            SignInServerPreset.REMOTE,
            SignInUrlScheme.HTTPS,
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

