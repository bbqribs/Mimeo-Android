package com.mimeo.android.ui.settings

import com.mimeo.android.model.ConnectionMode
import java.net.URI
import java.util.Locale

internal object ConnectionTestMessageResolver {
    fun tokenRequired(): String = "Token required"

    fun connected(gitSha: String?): String = "Connected git_sha=${gitSha ?: "unknown"}"

    fun forApiFailure(
        mode: ConnectionMode,
        baseUrl: String,
        statusCode: Int,
        message: String?,
    ): String {
        return when (statusCode) {
            401, 403 -> "Token rejected. Check API token."
            404 -> "Server reachable, but endpoint not found. Check base URL."
            in 500..599 -> "Backend error. Server reachable; check backend status."
            else -> {
                val lower = message.orEmpty().lowercase(Locale.US)
                if (lower.contains("unauthorized") || lower.contains("forbidden")) {
                    "Token rejected. Check API token."
                } else {
                    "Connection failed. ${modeHint(mode, baseUrl)}"
                }
            }
        }
    }

    fun forException(
        mode: ConnectionMode,
        baseUrl: String,
        message: String?,
    ): String {
        val lower = message.orEmpty().lowercase(Locale.US)
        return when {
            lower.contains("cleartxt") || lower.contains("cleartext") ->
                "Wrong URL scheme. Use http/https that matches server setup."
            lower.contains("ssl") || lower.contains("certificate") || lower.contains("handshake") ->
                "TLS/HTTPS failed. Verify remote certificate or URL scheme."
            lower.contains("unable to resolve host") || lower.contains("no address associated") ->
                "Host not found. ${modeHint(mode, baseUrl)}"
            lower.contains("failed to connect") || lower.contains("connection refused") ||
                lower.contains("timeout") || lower.contains("timed out") || lower.contains("network is unreachable") ->
                "Backend unreachable. ${modeHint(mode, baseUrl)}"
            else -> "Connection failed. ${modeHint(mode, baseUrl)}"
        }
    }

    private fun modeHint(mode: ConnectionMode, baseUrl: String): String {
        val host = parseHost(baseUrl).lowercase(Locale.US)
        val isLanHost = isLanIp(host)
        return when (mode) {
            ConnectionMode.LOCAL -> {
                if (!host.contains("10.0.2.2") && host != "localhost" && host != "127.0.0.1") {
                    "Local mode expects loopback/dev host (for emulator use 10.0.2.2)."
                } else {
                    "Verify backend is running on local/dev host."
                }
            }
            ConnectionMode.LAN -> {
                if (host == "localhost" || host == "127.0.0.1" || host.contains("10.0.2.2")) {
                    "LAN mode needs your server LAN IP (for example http://192.168.x.y:8000)."
                } else {
                    "Verify phone and server are on the same LAN and backend is reachable."
                }
            }
            ConnectionMode.REMOTE -> {
                if (isLanHost) {
                    "Remote mode usually needs Tailscale/VPN URL, not LAN IP."
                } else {
                    "Check Tailscale/VPN is connected on phone and server."
                }
            }
        }
    }

    private fun parseHost(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return ""
        return runCatching { URI(trimmed).host.orEmpty() }.getOrElse {
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
        }
    }

    private fun isLanIp(host: String): Boolean {
        return host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.matches(Regex("""172\.(1[6-9]|2\d|3[0-1])\..*"""))
    }
}
