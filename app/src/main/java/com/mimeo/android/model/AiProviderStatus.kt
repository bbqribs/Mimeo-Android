package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * BYOAI-A3 — Android consumption of the backend's read-only AI provider status
 * contract (`GET /config/ai-provider` -> `AiProviderConfigStatusOut`).
 *
 * This endpoint is reachable by an ordinary authenticated read-scope session
 * (`require_token_scope(read)`), so it is safe to call from a normal Android
 * sign-in. It carries only display-safe fields: there is never a plaintext key,
 * never ciphertext, and never an env var name. `key_last4` is at most the final
 * four characters of a stored key and is treated as a non-secret indicator only.
 *
 * Android remains status-display only: it never sends, stores, or edits any of
 * these values. The richer status here is an *enrichment* over
 * `GET /summary/capabilities`; if it is unavailable the existing BYOAI-A1
 * capabilities display remains the fallback.
 *
 * `last_test_detail` may contain a raw provider error string, so it is
 * intentionally NOT modeled here — Android maps only the coarse
 * [lastTestStatus] slug to friendly copy and never surfaces raw detail.
 */
@Serializable
data class AiProviderConfigStatusOut(
    /** Pre-sanitized provider slug (e.g. "anthropic"); null when none configured. */
    val provider: String? = null,
    val model: String? = null,
    @SerialName("base_url") val baseUrl: String? = null,
    val enabled: Boolean = false,
    val configured: Boolean = false,
    @SerialName("key_present") val keyPresent: Boolean = false,
    /** Final four chars of a stored key, non-secret indicator only; may be null. */
    @SerialName("key_last4") val keyLast4: String? = null,
    // "untested" | "ok" | "auth_failed" | "unreachable" | "error" — opaque slug.
    @SerialName("last_test_status") val lastTestStatus: String? = null,
    // ISO-8601 timestamp string, or null when never tested.
    @SerialName("last_test_at") val lastTestAt: String? = null,
    // "database" | "environment" | "none" — where the config originates.
    val source: String = "none",
)

/**
 * VM-facing state for the optional provider-status enrichment lookup. Failure or
 * unauthorized access collapses to [Unavailable], which the UI treats as "show
 * no enrichment" so the BYOAI-A1 capabilities display degrades silently.
 */
sealed class AiProviderStatusState {
    object Idle : AiProviderStatusState()
    object Loading : AiProviderStatusState()
    data class Ready(val status: AiProviderConfigStatusOut) : AiProviderStatusState()
    object Unavailable : AiProviderStatusState()
}
