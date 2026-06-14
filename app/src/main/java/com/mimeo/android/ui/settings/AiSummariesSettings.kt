package com.mimeo.android.ui.settings

import com.mimeo.android.model.SummaryCapabilitiesOut
import com.mimeo.android.model.availableModes
import com.mimeo.android.model.labelForKind
import com.mimeo.android.model.resolvedDefaultKind

/**
 * T-AUX-3 — display-safe projection of the backend summary capabilities for the
 * AI Summaries settings spoke. Everything here is derived only from server-owned,
 * pre-sanitized values: no provider keys, env var names, prompts, or raw payloads
 * are ever represented. Kept pure so the copy can be unit-tested without Compose.
 */
internal data class AiSummariesSettingsViewData(
    val enabled: Boolean,
    val statusLabel: String,
    val providerLine: String?,
    val modelLine: String?,
    val dailyLimitLine: String?,
    val modeLabels: List<String>,
    val defaultModeLabel: String?,
    val disclaimer: String,
)

internal const val AI_SUMMARIES_DEFAULT_DISCLAIMER =
    "AI-generated summaries can be wrong. Verify important details."

internal fun aiSummariesSettingsViewData(
    capabilities: SummaryCapabilitiesOut,
): AiSummariesSettingsViewData {
    val modes = capabilities.availableModes()
    val defaultKind = capabilities.resolvedDefaultKind()
    return AiSummariesSettingsViewData(
        enabled = capabilities.enabled,
        statusLabel = if (capabilities.enabled) "Enabled" else "Disabled",
        providerLine = capabilities.provider.displayName?.takeIf { it.isNotBlank() },
        modelLine = capabilities.model.displayName?.takeIf { it.isNotBlank() },
        dailyLimitLine = dailyLimitLine(capabilities),
        modeLabels = modes.map { capabilities.labelForKind(it.kind) },
        defaultModeLabel = capabilities.labelForKind(defaultKind).takeIf { modes.isNotEmpty() },
        disclaimer = capabilities.disclaimer?.takeIf { it.isNotBlank() }
            ?: AI_SUMMARIES_DEFAULT_DISCLAIMER,
    )
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
