package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * T-AUX-3 — Android consumption of the backend summary capabilities contract
 * (T-SUM-4: `GET /summary/capabilities`).
 *
 * These DTOs intentionally expose only backend-owned, display-safe values. The
 * contract never carries provider API keys, env var names, prompt text, or raw
 * provider payloads, and Android must never surface any of those. Provider and
 * model fields are pre-sanitized display strings chosen by the server operator.
 */

/** Canonical default summary kind. Mirrors the backend's `SUMMARY_DEFAULT_KIND`. */
const val SUMMARY_KIND_ABSTRACT: String = "abstract"

@Serializable
data class SummaryModeOut(
    val kind: String,
    val label: String = "",
    val description: String = "",
)

@Serializable
data class SummaryProviderCapabilityOut(
    // "disabled" | "configured" | "unconfigured" — treated as opaque status text.
    val status: String = "disabled",
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class SummaryModelCapabilityOut(
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class SummaryDailyLimitOut(
    val limit: Int = 0,
    val used: Int = 0,
    @SerialName("window_hours") val windowHours: Int = 24,
)

@Serializable
data class SummaryCapabilitiesOut(
    val enabled: Boolean = false,
    val provider: SummaryProviderCapabilityOut = SummaryProviderCapabilityOut(),
    val model: SummaryModelCapabilityOut = SummaryModelCapabilityOut(),
    @SerialName("supported_modes") val supportedModes: List<SummaryModeOut> = emptyList(),
    @SerialName("default_kind") val defaultKind: String = SUMMARY_KIND_ABSTRACT,
    @SerialName("daily_limit") val dailyLimit: SummaryDailyLimitOut? = null,
    val disclaimer: String? = null,
)

/**
 * VM-facing state for the lightly-cached capabilities lookup. [Unavailable]
 * always carries a display-safe message — never a raw error or payload.
 */
sealed class SummaryCapabilitiesState {
    object Idle : SummaryCapabilitiesState()
    object Loading : SummaryCapabilitiesState()
    data class Ready(val capabilities: SummaryCapabilitiesOut) : SummaryCapabilitiesState()
    data class Unavailable(val message: String) : SummaryCapabilitiesState()
}

/**
 * Modes the reader selector should offer. Deduped by kind, order preserved. When
 * the backend supplied no modes but summaries are otherwise usable, fall back to
 * a single Standard mode so the selector is never empty and the old no-kind
 * behavior keeps working.
 */
fun SummaryCapabilitiesOut.availableModes(): List<SummaryModeOut> {
    val deduped = supportedModes
        .filter { it.kind.isNotBlank() }
        .distinctBy { it.kind }
    if (deduped.isNotEmpty()) return deduped
    return listOf(SummaryModeOut(kind = SUMMARY_KIND_ABSTRACT, label = "Standard"))
}

/**
 * The safe default kind to start from: the backend default when it is actually a
 * supported mode, else `abstract` when available, else the first supported mode,
 * else `abstract`. Guarantees a valid, requestable kind.
 */
fun SummaryCapabilitiesOut.resolvedDefaultKind(): String {
    val modes = availableModes()
    val kinds = modes.map { it.kind }
    return when {
        defaultKind.isNotBlank() && defaultKind in kinds -> defaultKind
        SUMMARY_KIND_ABSTRACT in kinds -> SUMMARY_KIND_ABSTRACT
        else -> modes.first().kind
    }
}

/**
 * Coerce a (possibly stale or unsupported) user selection to a valid kind. Keeps
 * the selection when still supported; otherwise falls back to [resolvedDefaultKind].
 */
fun SummaryCapabilitiesOut.coerceSelectedKind(selected: String?): String {
    val kinds = availableModes().map { it.kind }
    return if (selected != null && selected in kinds) selected else resolvedDefaultKind()
}

/** Human label for a kind, falling back to a title-cased slug when unknown. */
fun SummaryCapabilitiesOut.labelForKind(kind: String): String {
    availableModes().firstOrNull { it.kind == kind }?.label
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return defaultLabelForKind(kind)
}

/** Title-case a kind slug like `key_points` -> `Key points` for display fallback. */
fun defaultLabelForKind(kind: String): String {
    val cleaned = kind.replace('_', ' ').replace('-', ' ').trim()
    if (cleaned.isEmpty()) return "Standard"
    return cleaned.replaceFirstChar { it.uppercaseChar() }
}
