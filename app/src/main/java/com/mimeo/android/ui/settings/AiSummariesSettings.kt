package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.SummaryCapabilitiesOut
import com.mimeo.android.model.availableModes
import com.mimeo.android.model.labelForKind
import com.mimeo.android.model.resolvedDefaultKind

/**
 * T-AUX-3 / BYOAI-A1 — display-safe projection of the backend summary
 * capabilities for the AI Summaries settings spoke. Everything here is derived
 * only from server-owned, pre-sanitized values: no provider keys, env var names,
 * prompts, or raw payloads are ever represented. Android is status-display only —
 * it never offers key entry, provider editing, or prompt editing. Kept pure so
 * the copy can be unit-tested without Compose.
 */

/**
 * Coarse, display-only summary status derived from the safe backend fields
 * (`enabled` + `provider.status`). Used to pick status copy and decide whether
 * to surface the read-only "configure on web" affordance. The raw backend status
 * slug is never shown to the user.
 */
internal enum class AiSummariesStatus {
    /** Summaries are configured and usable right now. */
    Enabled,

    /** A provider is configured on the server but is not usable right now. */
    ProviderUnavailable,

    /** No provider has been set up on the server yet. */
    Unconfigured,

    /** Summaries are turned off on the server. */
    Disabled,
}

internal data class AiSummariesSettingsViewData(
    val enabled: Boolean,
    val status: AiSummariesStatus,
    val statusLabel: String,
    val providerLine: String?,
    val modelLine: String?,
    val dailyLimitLine: String?,
    val modeLabels: List<String>,
    val defaultModeLabel: String?,
    /** Plain-English guidance for the current [status]. Always safe to display. */
    val guidanceMessage: String,
    /**
     * Read-only "configure on your Mimeo server" copy, present only when the
     * operator could act on the web app (unconfigured / provider unavailable).
     * This is copy only — Android never renders a key form or provider editor.
     */
    val configureOnWebMessage: String?,
    val configuredByLine: String,
    val disclaimer: String,
)

internal const val AI_SUMMARIES_DEFAULT_DISCLAIMER =
    "AI-generated summaries can be wrong. Verify important details."

internal const val AI_SUMMARIES_CONFIGURED_BY_LINE = "Your Mimeo server"

internal const val AI_SUMMARIES_UNCONFIGURED_MESSAGE =
    "Set up an AI provider on your Mimeo server to enable summaries."

internal const val AI_SUMMARIES_PROVIDER_UNAVAILABLE_MESSAGE =
    "Your server has an AI provider configured, but it can't be used right now. " +
        "Re-check the provider configuration on your Mimeo server."

internal const val AI_SUMMARIES_ENABLED_MESSAGE =
    "Open a readable article, then use the summary action in the reader to " +
        "generate or update an AI summary. Summary styles and provider details " +
        "are configured by your server."

internal const val AI_SUMMARIES_DISABLED_MESSAGE =
    "Summaries are turned off on your server right now. Your server operator " +
        "manages whether summaries are available."

internal const val AI_SUMMARIES_CONFIGURE_ON_WEB_MESSAGE =
    "Configure on web: open Mimeo in your browser and set up the AI provider in " +
        "your server settings. Provider keys are entered only on the server, " +
        "never in this app."

internal fun aiSummariesSettingsViewData(
    capabilities: SummaryCapabilitiesOut,
): AiSummariesSettingsViewData {
    val modes = capabilities.availableModes()
    val defaultKind = capabilities.resolvedDefaultKind()
    val status = resolveAiSummariesStatus(capabilities)
    return AiSummariesSettingsViewData(
        enabled = capabilities.enabled,
        status = status,
        statusLabel = statusLabelFor(status),
        providerLine = capabilities.provider.displayName?.takeIf { it.isNotBlank() },
        modelLine = capabilities.model.displayName?.takeIf { it.isNotBlank() },
        dailyLimitLine = dailyLimitLine(capabilities),
        modeLabels = modes.map { capabilities.labelForKind(it.kind) },
        defaultModeLabel = capabilities.labelForKind(defaultKind).takeIf { modes.isNotEmpty() },
        guidanceMessage = guidanceMessageFor(status),
        configureOnWebMessage = when (status) {
            AiSummariesStatus.Unconfigured,
            AiSummariesStatus.ProviderUnavailable -> AI_SUMMARIES_CONFIGURE_ON_WEB_MESSAGE
            else -> null
        },
        configuredByLine = AI_SUMMARIES_CONFIGURED_BY_LINE,
        disclaimer = capabilities.disclaimer?.takeIf { it.isNotBlank() }
            ?: AI_SUMMARIES_DEFAULT_DISCLAIMER,
    )
}

/**
 * Map the safe backend fields to a coarse display status. The provider status
 * slug ("unconfigured"/"configured"/"disabled") is treated as opaque: an
 * explicit "unconfigured" always wins; otherwise an enabled server means usable,
 * a configured-but-not-enabled server means the provider is unavailable, and
 * anything else is treated as turned off.
 */
private fun resolveAiSummariesStatus(
    capabilities: SummaryCapabilitiesOut,
): AiSummariesStatus {
    val providerStatus = capabilities.provider.status.trim().lowercase()
    return when {
        providerStatus == "unconfigured" -> AiSummariesStatus.Unconfigured
        capabilities.enabled -> AiSummariesStatus.Enabled
        providerStatus == "configured" -> AiSummariesStatus.ProviderUnavailable
        else -> AiSummariesStatus.Disabled
    }
}

private fun statusLabelFor(status: AiSummariesStatus): String = when (status) {
    AiSummariesStatus.Enabled -> "Enabled"
    AiSummariesStatus.ProviderUnavailable -> "Provider unavailable"
    AiSummariesStatus.Unconfigured -> "Not configured"
    AiSummariesStatus.Disabled -> "Disabled"
}

private fun guidanceMessageFor(status: AiSummariesStatus): String = when (status) {
    AiSummariesStatus.Enabled -> AI_SUMMARIES_ENABLED_MESSAGE
    AiSummariesStatus.ProviderUnavailable -> AI_SUMMARIES_PROVIDER_UNAVAILABLE_MESSAGE
    AiSummariesStatus.Unconfigured -> AI_SUMMARIES_UNCONFIGURED_MESSAGE
    AiSummariesStatus.Disabled -> AI_SUMMARIES_DISABLED_MESSAGE
}

// ---------------------------------------------------------------------------
// BYOAI-A3 — read-only provider status enrichment.
//
// Optional extra detail derived from `GET /config/ai-provider`
// (`AiProviderConfigStatusOut`). This is *enrichment* over the capabilities
// display: every line is independently nullable and the whole block is absent
// when the endpoint is unavailable, so the BYOAI-A1 display is the fallback.
//
// Hard rules (mirroring BYOAI-A1):
//   - never surface raw status slugs, `last_test_detail`, ciphertext, env var
//     names, or any plaintext key;
//   - `key_last4` is a non-secret indicator only — the full key never exists
//     here and is never requested;
//   - status-display only: no edit/test/delete affordance is implied.
// ---------------------------------------------------------------------------

internal data class AiProviderStatusEnrichment(
    /** Friendly last-test outcome, e.g. "Last test: passed". Null when untested/unknown. */
    val lastTestLine: String?,
    /** "Last tested: 2026-06-10" derived from the ISO timestamp's date part, or null. */
    val lastTestedOnLine: String?,
    /** Where the provider config lives, e.g. "Source: Saved on your server". Null for none. */
    val sourceLine: String?,
    /** Non-secret key indicator, e.g. "Key: stored (ending 1234)". Null when no key. */
    val keyLine: String?,
) {
    /** True when nothing safe/useful was derived, so the UI should render nothing. */
    val isEmpty: Boolean
        get() = lastTestLine == null && lastTestedOnLine == null &&
            sourceLine == null && keyLine == null
}

internal const val AI_PROVIDER_LAST_TEST_OK = "Last test: passed"
internal const val AI_PROVIDER_LAST_TEST_UNTESTED = "Last test: not yet tested"
internal const val AI_PROVIDER_LAST_TEST_AUTH_FAILED =
    "Last test: the provider rejected the configured key"
internal const val AI_PROVIDER_LAST_TEST_UNREACHABLE =
    "Last test: the provider couldn't be reached"
internal const val AI_PROVIDER_LAST_TEST_ERROR = "Last test: didn't complete"

internal const val AI_PROVIDER_SOURCE_DATABASE = "Source: saved on your Mimeo server"
internal const val AI_PROVIDER_SOURCE_ENVIRONMENT =
    "Source: set by a server environment variable"

/**
 * Project the safe read-only provider status into display-safe enrichment lines.
 * Returns null when the status carries nothing worth showing (e.g. nothing is
 * configured), so callers fall back to the capabilities-only display.
 */
internal fun aiProviderStatusEnrichment(
    status: AiProviderConfigStatusOut,
): AiProviderStatusEnrichment? {
    val enrichment = AiProviderStatusEnrichment(
        lastTestLine = lastTestLineFor(status.lastTestStatus),
        lastTestedOnLine = lastTestedOnLine(status.lastTestAt),
        sourceLine = sourceLineFor(status.source),
        keyLine = keyLineFor(status),
    )
    return enrichment.takeUnless { it.isEmpty }
}

/** Map the opaque backend test-status slug to friendly copy; null when unknown. */
private fun lastTestLineFor(rawStatus: String?): String? =
    when (rawStatus?.trim()?.lowercase()) {
        "ok" -> AI_PROVIDER_LAST_TEST_OK
        "untested" -> AI_PROVIDER_LAST_TEST_UNTESTED
        "auth_failed" -> AI_PROVIDER_LAST_TEST_AUTH_FAILED
        "unreachable" -> AI_PROVIDER_LAST_TEST_UNREACHABLE
        "error" -> AI_PROVIDER_LAST_TEST_ERROR
        else -> null
    }

/**
 * "Last tested: <date>" using only the calendar-date prefix of an ISO-8601
 * timestamp (everything before the `T`). We deliberately avoid time-of-day and
 * timezone parsing so the line is locale-stable and purely testable. Anything
 * that doesn't look like an ISO date is dropped rather than echoed raw.
 */
private fun lastTestedOnLine(rawTimestamp: String?): String? {
    val trimmed = rawTimestamp?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val datePart = trimmed.substringBefore('T')
    // Guard: only surface a plain YYYY-MM-DD shape; never echo unexpected text.
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(datePart)) return null
    return "Last tested: $datePart"
}

/** Map the config source to a read-only note; null for "none" or unknown. */
private fun sourceLineFor(rawSource: String?): String? =
    when (rawSource?.trim()?.lowercase()) {
        "database" -> AI_PROVIDER_SOURCE_DATABASE
        "environment" -> AI_PROVIDER_SOURCE_ENVIRONMENT
        else -> null
    }

/**
 * Non-secret key indicator. Only shown when the backend reports a key is
 * present. `key_last4` (when a clean 4-char tail) is appended as "ending 1234";
 * the full key never exists on the client and is never requested.
 */
private fun keyLineFor(status: AiProviderConfigStatusOut): String? {
    if (!status.keyPresent) return null
    val last4 = status.keyLast4?.trim().orEmpty()
    // Allow the alphanumerics plus the `-`/`_` that real provider key tails use
    // (e.g. Gemini "3_2Y"), but stay bounded to <=4 chars so a misbehaving
    // backend can never echo a long/garbage value as if it were a key tail.
    return if (last4.matches(Regex("""[A-Za-z0-9_-]{1,4}"""))) {
        "Key: stored (ending $last4)"
    } else {
        "Key: stored"
    }
}

/**
 * "n of N used today" when the backend reports a positive daily limit, else null.
 * The window is annotated only when it is not the conventional 24 hours.
 */
private fun dailyLimitLine(capabilities: SummaryCapabilitiesOut): String? {
    val limit = capabilities.dailyLimit ?: return null
    if (limit.limit <= 0) return null
    val window = if (limit.windowHours == 24) {
        "today"
    } else {
        "per ${limit.windowHours}h"
    }
    return "${limit.used.coerceAtLeast(0)} of ${limit.limit} used $window"
}
