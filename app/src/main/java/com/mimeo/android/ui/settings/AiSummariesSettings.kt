package com.mimeo.android.ui.settings

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
