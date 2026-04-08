package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import java.net.URI
import java.util.Locale

internal data class ConnectionEndpointValidation(
    val blockingError: String? = null,
    val warnings: List<String> = emptyList(),
)

internal fun validateConnectionEndpoint(
    mode: ConnectionMode,
    rawBaseUrl: String,
): ConnectionEndpointValidation {
    val trimmed = rawBaseUrl.trim()
    if (trimmed.isBlank()) {
        return ConnectionEndpointValidation(blockingError = "Base URL is required.")
    }
    val uri = runCatching { URI(trimmed) }.getOrNull()
        ?: return ConnectionEndpointValidation(blockingError = "Enter a valid URL like http://host:8000.")

    val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
    if (scheme != "http" && scheme != "https") {
        return ConnectionEndpointValidation(blockingError = "Use http:// or https://.")
    }
    val host = uri.host?.trim().orEmpty()
    if (host.isBlank()) {
        return ConnectionEndpointValidation(blockingError = "URL must include a host name or IP.")
    }
    if (!uri.path.isNullOrBlank() && uri.path != "/") {
        return ConnectionEndpointValidation(blockingError = "Use base host only (no path).")
    }
    if (!uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()) {
        return ConnectionEndpointValidation(blockingError = "Remove query/fragment from the base URL.")
    }

    val lowerHost = host.lowercase(Locale.US)
    val warnings = mutableListOf<String>()
    val isLoopback = lowerHost == "localhost" || lowerHost == "127.0.0.1"
    val isEmulatorHost = lowerHost == "10.0.2.2"
    val isLanIp = isLanIpv4(lowerHost)
    val isLikelyTailnetIp = isTailnetIpv4(lowerHost)
    val isLikelyTailnetHost = lowerHost.endsWith(".ts.net")

    when (mode) {
        ConnectionMode.LOCAL -> {
            if (!isLoopback && !isEmulatorHost) {
                warnings += "Local mode usually uses localhost/127.0.0.1/10.0.2.2. Use LAN or Remote for other hosts."
            }
            if (scheme == "https") {
                warnings += "HTTPS in Local mode may need a trusted local cert."
            }
        }

        ConnectionMode.LAN -> {
            if (isLoopback || isEmulatorHost) {
                return ConnectionEndpointValidation(
                    blockingError = "LAN mode needs your server LAN IP (for example http://192.168.x.y:8000).",
                )
            }
            if (isLikelyTailnetIp || isLikelyTailnetHost) {
                warnings += "This host looks like a Remote/Tailscale target. Use Remote mode unless phone + laptop are on the same LAN."
            }
            if (!isLanIp) {
                warnings += "LAN mode is intended for same-network server addresses."
            }
        }

        ConnectionMode.REMOTE -> {
            if (isLoopback || isEmulatorHost) {
                return ConnectionEndpointValidation(
                    blockingError = "Remote mode needs a Tailscale/VPN or remote host URL, not localhost/emulator loopback.",
                )
            }
            if (isLanIp) {
                warnings += "Remote mode is using a LAN IP. If phone and server are on same network, use LAN mode."
            }
            if (scheme == "http" && !isLikelyTailnetIp && !isLikelyTailnetHost && !isLanIp) {
                warnings += "HTTP remote URLs are supported over trusted VPN/Tailscale. Use HTTPS for internet-exposed hosts."
            }
        }
    }

    return ConnectionEndpointValidation(warnings = warnings)
}

private fun isLanIpv4(host: String): Boolean {
    if (!host.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$"))) return false
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return when {
        parts[0] == 10 -> true
        parts[0] == 192 && parts[1] == 168 -> true
        parts[0] == 172 && parts[1] in 16..31 -> true
        else -> false
    }
}

private fun isTailnetIpv4(host: String): Boolean {
    if (!host.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$"))) return false
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 100 && parts[1] in 64..127
}
