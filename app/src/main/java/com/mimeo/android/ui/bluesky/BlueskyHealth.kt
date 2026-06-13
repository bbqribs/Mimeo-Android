package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyOperatorStatusResponse
import java.util.Locale

/**
 * User-facing Bluesky connection health, derived entirely from data Android already
 * fetches (`GET /bluesky/account/connection` + `GET /bluesky/operator/status`). No
 * backend changes are required; this collapses scattered operator-style fields into
 * one status, one explanation, and one recovery action per the cross-surface audit.
 */
enum class BlueskyHealthState {
    /** Connected and recent activity looks healthy. */
    CONNECTED,

    /** The account connection needs the user to re-authenticate (app password). */
    ACTION_NEEDED,

    /** A transient problem (rate limit, network); retrying later should recover. */
    TEMPORARY_TROUBLE,

    /** No Bluesky account is connected yet. */
    NOT_CONNECTED,

    /** Status data is not available (still loading or unreachable). */
    UNKNOWN,
}

/** The single recovery verb shown alongside a health state. */
enum class BlueskyRecoveryAction {
    CONNECT,
    RECONNECT,
    TRY_AGAIN,
    NONE,
}

data class BlueskyHealthPresentation(
    val state: BlueskyHealthState,
    val title: String,
    val detail: String?,
    val action: BlueskyRecoveryAction,
    val handle: String?,
)

private val AUTH_TROUBLE_CODES = setOf(
    "auth_error",
    "unauthorized",
    "invalid",
    "invalid_credentials",
    "expired",
    "reconnect_required",
    "forbidden",
)

private val TEMPORARY_TROUBLE_CODES = setOf(
    "rate_limited",
    "ratelimited",
    "timeout",
    "timed_out",
    "network",
    "network_error",
    "unavailable",
    "error",
    "failed",
    "failure",
)

private fun String?.normalizedCode(): String =
    this?.trim()?.lowercase(Locale.US).orEmpty()

private fun String?.isAuthTrouble(): Boolean = normalizedCode() in AUTH_TROUBLE_CODES

private fun String?.isTemporaryTrouble(): Boolean = normalizedCode() in TEMPORARY_TROUBLE_CODES

/**
 * Maps a coded run/validation status to plain language for the "What happened?"
 * disclosure. Never returns raw provider errors or developer noise.
 */
fun blueskyPlainStatus(code: String?): String = when (code.normalizedCode()) {
    "" -> "Unknown"
    "ok", "success", "succeeded", "completed", "valid", "connected" -> "Working normally"
    "auth_error", "unauthorized", "invalid", "invalid_credentials", "expired", "forbidden" ->
        "Sign-in needs attention"
    "reconnect_required" -> "Reconnect needed"
    "rate_limited", "ratelimited" -> "Busy (rate limited)"
    "blocked" -> "Blocked by Bluesky"
    "timeout", "timed_out" -> "Timed out"
    "network", "network_error", "unavailable" -> "Couldn't reach Bluesky"
    "failed", "failure", "error" -> "Last attempt didn't finish"
    "pending", "running", "scheduled" -> "In progress"
    else -> code.normalizedCode().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/**
 * Derives the headline Bluesky health from current connection + operator status.
 * Conservative: only downgrades from CONNECTED when there is a clear trouble signal.
 */
fun resolveBlueskyHealth(
    account: BlueskyAccountConnectionResponse?,
    operatorStatus: BlueskyOperatorStatusResponse?,
): BlueskyHealthPresentation {
    if (account == null && operatorStatus == null) {
        return BlueskyHealthPresentation(
            state = BlueskyHealthState.UNKNOWN,
            title = "Status unavailable",
            detail = "We couldn't load your Bluesky status. Try again.",
            action = BlueskyRecoveryAction.TRY_AGAIN,
            handle = null,
        )
    }

    val connected = account?.connected == true
    if (!connected) {
        return BlueskyHealthPresentation(
            state = BlueskyHealthState.NOT_CONNECTED,
            title = "Not connected",
            detail = "Connect your Bluesky account to browse links from your feeds.",
            action = BlueskyRecoveryAction.CONNECT,
            handle = null,
        )
    }

    val handle = account.handle?.trim()?.takeIf { it.isNotBlank() }
    val handleLabel = handle?.let { "@${it.removePrefix("@")}" }

    val sources = operatorStatus?.sources.orEmpty()
    val anyReconnectRequired = sources.any { it.reconnectRequired == true }
    val validationState = account.resolvedValidationState
    val lastRunStatus = operatorStatus?.lastRunStatus

    val authTrouble = anyReconnectRequired ||
        validationState.isAuthTrouble() ||
        lastRunStatus.isAuthTrouble() ||
        sources.any { it.lastStatus.isAuthTrouble() }

    if (authTrouble) {
        return BlueskyHealthPresentation(
            state = BlueskyHealthState.ACTION_NEEDED,
            title = "Reconnect needed",
            detail = handleLabel?.let { "$it needs you to sign in again." }
                ?: "Bluesky needs you to sign in again.",
            action = BlueskyRecoveryAction.RECONNECT,
            handle = handle,
        )
    }

    val temporaryTrouble = lastRunStatus.isTemporaryTrouble() ||
        sources.any { it.lastStatus.isTemporaryTrouble() }

    if (temporaryTrouble) {
        return BlueskyHealthPresentation(
            state = BlueskyHealthState.TEMPORARY_TROUBLE,
            title = "Temporary trouble",
            detail = "Bluesky is busy or unreachable right now. Try again later.",
            action = BlueskyRecoveryAction.TRY_AGAIN,
            handle = handle,
        )
    }

    return BlueskyHealthPresentation(
        state = BlueskyHealthState.CONNECTED,
        title = handleLabel?.let { "Connected as $it" } ?: "Connected",
        detail = null,
        action = BlueskyRecoveryAction.NONE,
        handle = handle,
    )
}
