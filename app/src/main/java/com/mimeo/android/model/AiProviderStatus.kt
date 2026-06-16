package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * BYOAI-A6 — a single safe entry in the backend-provided provider catalogue
 * (`AiProviderCatalogueItemOut`). This is the source of truth for the provider
 * dropdown, default models, and base-URL rules; Android no longer mirrors them.
 *
 * Every field here is display-safe metadata: a stable provider slug, a label,
 * the default model, base-URL rules, and a coarse endpoint kind. There is never
 * a key, ciphertext, env var name, prompt, or raw provider payload. Unknown
 * non-critical fields are ignored by the decoder (`ignoreUnknownKeys = true`),
 * and every modeled field except [key] has a safe default so a partial item
 * still decodes; [key] is the item's identity and is required.
 */
@Serializable
data class AiProviderCatalogueItemOut(
    /** Stable, pre-sanitized provider slug (e.g. "anthropic"). */
    val key: String,
    /** Human-facing label (e.g. "Anthropic"); falls back to [key] when blank. */
    val label: String = "",
    @SerialName("default_model") val defaultModel: String = "",
    /** Whether the backend requires an operator-entered base URL for this provider. */
    @SerialName("base_url_required") val baseUrlRequired: Boolean = false,
    /** Whether a base URL is permitted at all for this provider. */
    @SerialName("base_url_allowed") val baseUrlAllowed: Boolean = false,
    // "fixed_endpoint" | "openai_compatible" | "local_openai_compatible" — coarse,
    // non-secret classification; opaque slug, surfaced only as a hint if at all.
    @SerialName("endpoint_kind") val endpointKind: String? = null,
    /** Optional short, display-safe help string for the provider; may be null. */
    val help: String? = null,
)

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
    /**
     * BYOAI-A5 — backend-authoritative operator-capability flag. Only when this
     * is true may Android show the provider edit flow; ordinary read /
     * read_write / NULL-scope sessions report false (or omit the field, which
     * decodes to false), so they remain status-only. Android never infers this
     * by probing privileged endpoints — it is read from this field only.
     */
    @SerialName("can_edit") val canEdit: Boolean = false,
    /**
     * BYOAI-A6 — backend-authoritative provider catalogue. This is the source of
     * truth for the edit-flow dropdown, default models, and base-URL rules.
     * Defaults to empty so an older backend (or a response that omits it) decodes
     * cleanly; an empty catalogue triggers the conservative fallback in the edit
     * flow rather than any stale mirrored data.
     */
    val catalogue: List<AiProviderCatalogueItemOut> = emptyList(),
)

/**
 * BYOAI-A5 — write payload for an operator provider upsert
 * (`POST /config/ai-provider`). Mirrors the backend `AiProviderConfigIn`
 * contract and adds no new semantics.
 *
 * Encoding rules (the repo's `Json` uses the default `encodeDefaults = false`):
 *   - [provider], [model] and [enabled] have no defaults, so they are always
 *     serialized.
 *   - [baseUrl] and [apiKey] default to null and are therefore OMITTED from the
 *     JSON when null. An omitted [apiKey] is the "keep the existing key"
 *     signal — Android never sends an empty-string key. [baseUrl] is only set
 *     for providers that use it (`openai_compatible` / `local`).
 *
 * The [apiKey] here is transient request-only data; it is never stored, logged,
 * or read back. Responses carry only `key_present` + `key_last4`.
 */
@Serializable
data class AiProviderConfigIn(
    val provider: String,
    val model: String,
    val enabled: Boolean,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
)

/**
 * BYOAI-A5 — coarse, display-safe classification of a provider write/test/delete
 * failure. The raw backend body (which may carry provider errors, ciphertext, or
 * env var names) is never represented here: the data layer maps a response to one
 * of these codes and discards the body, and the UI maps a code to friendly copy.
 */
enum class AiProviderErrorCode {
    /** Backend has no usable encryption key configured, so it can't store a key. */
    EncryptionKeyRequired,

    /** Backend encryption key is present but invalid/unusable. */
    EncryptionKeyInvalid,

    /** A base URL is required for the chosen provider but was missing. */
    BaseUrlRequired,

    /** The base URL must start with http:// or https://. */
    BaseUrlMustBeHttp,

    /** The chosen provider slug is not supported by the backend. */
    UnsupportedProvider,

    /** A model is required but was missing. */
    ModelRequired,

    /** An API key is required (e.g. first configure) but none is stored or sent. */
    ApiKeyRequired,

    /** Test was requested before any config was saved (HTTP 409). */
    TestBeforeSave,

    /** The session isn't authorized to perform provider writes (HTTP 401/403). */
    Unauthorized,

    /** Anything else — mapped to a generic, safe message. */
    Unknown,
}

/**
 * Known backend `_bad_config`-style error slugs, in priority order. These slugs
 * are stable identifiers (not secrets), so matching on them does not echo any
 * sensitive payload. Anything not on this list collapses to [AiProviderErrorCode]
 * derived from the HTTP status, and ultimately to [AiProviderErrorCode.Unknown].
 */
private val AI_PROVIDER_ERROR_SLUGS: List<Pair<String, AiProviderErrorCode>> = listOf(
    "encryption_key_required" to AiProviderErrorCode.EncryptionKeyRequired,
    "encryption_key_invalid" to AiProviderErrorCode.EncryptionKeyInvalid,
    "base_url_required" to AiProviderErrorCode.BaseUrlRequired,
    "base_url_must_be_http" to AiProviderErrorCode.BaseUrlMustBeHttp,
    "unsupported_provider" to AiProviderErrorCode.UnsupportedProvider,
    "model_required" to AiProviderErrorCode.ModelRequired,
    "api_key_required" to AiProviderErrorCode.ApiKeyRequired,
)

/**
 * Pure, display-safe classification of a provider action failure. Scans [body]
 * only for known stable slugs (never echoing it) and otherwise classifies by
 * [statusCode]. Always returns a code — never the raw body — so no caller can
 * accidentally surface provider errors, ciphertext, or env var names.
 */
fun parseAiProviderErrorCode(statusCode: Int, body: String?): AiProviderErrorCode {
    val haystack = body?.lowercase().orEmpty()
    AI_PROVIDER_ERROR_SLUGS.forEach { (slug, code) ->
        if (haystack.contains(slug)) return code
    }
    return when (statusCode) {
        409 -> AiProviderErrorCode.TestBeforeSave
        401, 403 -> AiProviderErrorCode.Unauthorized
        else -> AiProviderErrorCode.Unknown
    }
}

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

/**
 * BYOAI-A5 — outcome of an operator provider action (save/test/delete). Carries
 * only a coarse outcome or a safe [AiProviderErrorCode]; never a key, raw body,
 * or provider error. The UI maps these to display copy. The refreshed safe status
 * is published separately via [AiProviderStatusState.Ready].
 */
sealed class AiProviderEditResult {
    object Saved : AiProviderEditResult()
    object Tested : AiProviderEditResult()
    object Cleared : AiProviderEditResult()
    data class Failed(val code: AiProviderErrorCode) : AiProviderEditResult()
}
