package com.mimeo.android.ui.common

import com.mimeo.android.data.normalizeServerIdentity
import com.mimeo.android.model.AppSettings
import java.net.URI

/**
 * Safe, user-facing identity for the persisted session used by API requests. Editable form
 * fields are deliberately not inputs: the active [AppSettings] is the only source of truth.
 */
internal data class AuthenticatedIdentityPresentation(
    val isSignedIn: Boolean,
    val username: String?,
    val canonicalEndpointOrigin: String?,
) {
    val authenticationState: String
        get() = if (isSignedIn) "Signed in" else "Signed out"

    val usernameDisplay: String
        get() = username ?: if (isSignedIn) "Unavailable (manual token)" else "Not signed in"

    val endpointDisplay: String
        get() = canonicalEndpointOrigin ?: "Unavailable"
}

internal fun authenticatedIdentityPresentation(settings: AppSettings): AuthenticatedIdentityPresentation {
    if (settings.apiToken.trim().isBlank()) {
        return AuthenticatedIdentityPresentation(
            isSignedIn = false,
            username = null,
            canonicalEndpointOrigin = null,
        )
    }
    return AuthenticatedIdentityPresentation(
        isSignedIn = true,
        username = settings.authenticatedUsername.trim().takeIf { it.isNotBlank() },
        canonicalEndpointOrigin = canonicalEndpointOrigin(settings.baseUrl),
    )
}

/** Exposes only scheme, host, and a non-default port; never a path, query, or fragment. */
internal fun canonicalEndpointOrigin(baseUrl: String): String? {
    val normalized = normalizeServerIdentity(baseUrl)
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase().orEmpty()
    if (scheme !in setOf("http", "https") || host.isBlank()) return null
    val portSuffix = when {
        uri.port == -1 -> ""
        scheme == "http" && uri.port == 80 -> ""
        scheme == "https" && uri.port == 443 -> ""
        else -> ":${uri.port}"
    }
    return "$scheme://$host$portSuffix"
}
