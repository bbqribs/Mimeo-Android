package com.mimeo.android.model

import java.net.URI
import java.util.Locale

internal const val DEFAULT_LOCAL_HOST = "10.0.2.2:8000"
internal const val DEFAULT_LAN_HOST = "192.168.68.124:8000"
internal const val DEFAULT_REMOTE_HTTPS_HOST = "beh-august2015.taildacac5.ts.net"
internal const val DEFAULT_REMOTE_HTTP_FALLBACK_HOST = "100.84.13.10:8000"
internal const val DEFAULT_REMOTE_HOST = DEFAULT_REMOTE_HTTPS_HOST

internal const val DEFAULT_LOCAL_BASE_URL = "http://$DEFAULT_LOCAL_HOST"
internal const val DEFAULT_LAN_BASE_URL = "http://$DEFAULT_LAN_HOST"
internal const val DEFAULT_REMOTE_BASE_URL = "https://$DEFAULT_REMOTE_HTTPS_HOST"
internal const val DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL = "http://$DEFAULT_REMOTE_HTTP_FALLBACK_HOST"

internal fun inferConnectionModeForHost(baseUrl: String): ConnectionMode {
    val host = parseConnectionHost(baseUrl)
    if (host.isBlank()) return ConnectionMode.REMOTE
    if (host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost") {
        return ConnectionMode.LOCAL
    }
    if (host.endsWith(".ts.net") || isCarrierGradeNatHost(host)) {
        return ConnectionMode.REMOTE
    }
    return ConnectionMode.LAN
}

internal fun defaultBaseUrlForMode(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.LOCAL -> DEFAULT_LOCAL_BASE_URL
    ConnectionMode.LAN -> DEFAULT_LAN_BASE_URL
    ConnectionMode.REMOTE -> DEFAULT_REMOTE_BASE_URL
}

private fun parseConnectionHost(baseUrl: String): String {
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

private fun isCarrierGradeNatHost(host: String): Boolean {
    if (!host.startsWith("100.")) return false
    val octets = host.split('.')
    if (octets.size != 4) return false
    val secondOctet = octets.getOrNull(1)?.toIntOrNull() ?: return false
    return secondOctet in 64..127
}
