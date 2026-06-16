package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigIn
import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.AiProviderErrorCode
import com.mimeo.android.model.AiProviderStatusState

/**
 * BYOAI-A5 — pure, display-safe logic for the operator AI Provider edit flow.
 *
 * Everything here is Compose-free and unit-testable, mirroring the BYOAI-A1/A3
 * pattern in [AiSummariesSettings.kt]. It owns:
 *   - gating (only an operator-capable session may see the edit entry),
 *   - the write-form model and its no-secret guarantees,
 *   - the request builder (omits `api_key` when blank, `base_url` when unused),
 *   - the safe-status projection shown above the form, and
 *   - friendly copy mapping for backend states/errors.
 *
 * Hard no-secret rules enforced here (see ticket §4 / design §6):
 *   - the API key is never prefilled from any backend value;
 *   - the API key is never part of the persistable form snapshot, so it can
 *     never land in saved-instance state;
 *   - a blank key is omitted from the request (keep-existing semantics), never
 *     sent as an empty string;
 *   - no raw backend body, provider error, ciphertext, or env var name is ever
 *     represented in copy — only coarse [AiProviderErrorCode] codes are mapped.
 */

// ---------------------------------------------------------------------------
// Gating
// ---------------------------------------------------------------------------

/**
 * True only when the backend explicitly reports this session can edit the
 * provider. Anything else — not-yet-loaded, unavailable, or `can_edit=false` —
 * keeps the app status-only. Android never probes a privileged endpoint to infer
 * capability; it reads [AiProviderConfigStatusOut.canEdit] only.
 */
internal fun aiProviderManageEntryVisible(state: AiProviderStatusState): Boolean =
    state is AiProviderStatusState.Ready && state.status.canEdit

internal const val AI_PROVIDER_MANAGE_ENTRY_LABEL = "Manage AI provider"
internal const val AI_PROVIDER_MANAGE_ENTRY_HINT =
    "Operator tools for the server's AI provider. Keys are sent to your Mimeo " +
        "server only and are never stored on this device."

// ---------------------------------------------------------------------------
// Provider options
// ---------------------------------------------------------------------------

internal data class AiProviderOption(
    val slug: String,
    val label: String,
    val defaultModel: String,
    /** Whether this provider needs an operator-entered base URL. */
    val usesBaseUrl: Boolean,
)

/**
 * Provider options mirroring the backend operator page. `openai_compatible` and
 * `local` are the only ones that take a base URL; default models match the
 * backend `default_model_for_provider` so Android and web behave identically.
 */
internal val AI_PROVIDER_OPTIONS: List<AiProviderOption> = listOf(
    AiProviderOption("anthropic", "Anthropic", "claude-3-5-sonnet-latest", usesBaseUrl = false),
    AiProviderOption("openai", "OpenAI", "gpt-4o-mini", usesBaseUrl = false),
    AiProviderOption("deepseek", "DeepSeek", "deepseek-chat", usesBaseUrl = false),
    AiProviderOption("gemini", "Google Gemini", "gemini-1.5-flash", usesBaseUrl = false),
    AiProviderOption("openai_compatible", "OpenAI-compatible", "", usesBaseUrl = true),
    AiProviderOption("local", "Local", "", usesBaseUrl = true),
)

private val DEFAULT_PROVIDER_SLUG = AI_PROVIDER_OPTIONS.first().slug

internal fun aiProviderOptionFor(slug: String?): AiProviderOption {
    val normalized = slug?.trim()?.lowercase()
    return AI_PROVIDER_OPTIONS.firstOrNull { it.slug == normalized }
        ?: AI_PROVIDER_OPTIONS.first()
}

/** Base URL entry is offered only for providers that actually use one. */
internal fun aiProviderShowBaseUrl(slug: String?): Boolean = aiProviderOptionFor(slug).usesBaseUrl

// ---------------------------------------------------------------------------
// Write form
// ---------------------------------------------------------------------------

/**
 * The editable provider form. [apiKey] is transient, write-only data: it is
 * never prefilled, never persisted (see [aiProviderSaveableFields]), and cleared
 * on every action/exit by the screen.
 */
internal data class AiProviderEditForm(
    val provider: String,
    val model: String,
    val baseUrl: String,
    val enabled: Boolean,
    val apiKey: String,
) {
    /** Return a copy with the transient key wiped — used on every action/exit. */
    fun withClearedKey(): AiProviderEditForm = if (apiKey.isEmpty()) this else copy(apiKey = "")
}

/**
 * Build the initial form from the safe backend status. Non-secret fields are
 * prefilled; the model falls back to the provider default when the backend has
 * none. The API key is ALWAYS empty — it is never populated from any response.
 */
internal fun aiProviderEditFormFrom(status: AiProviderConfigStatusOut?): AiProviderEditForm {
    val option = aiProviderOptionFor(status?.provider)
    val model = status?.model?.takeIf { it.isNotBlank() } ?: option.defaultModel
    val baseUrl = if (option.usesBaseUrl) status?.baseUrl?.trim().orEmpty() else ""
    // First-configure defaults to enabled; otherwise reflect the stored state.
    val enabled = status?.let { if (it.configured) it.enabled else true } ?: true
    return AiProviderEditForm(
        provider = option.slug,
        model = model,
        baseUrl = baseUrl,
        enabled = enabled,
        apiKey = "",
    )
}

internal const val AI_PROVIDER_FORM_KEY_PROVIDER = "provider"
internal const val AI_PROVIDER_FORM_KEY_MODEL = "model"
internal const val AI_PROVIDER_FORM_KEY_BASE_URL = "base_url"
internal const val AI_PROVIDER_FORM_KEY_ENABLED = "enabled"

/**
 * The persistable subset of the form, used as the `rememberSaveable` snapshot so
 * the non-secret fields survive a configuration change. The API key is
 * deliberately excluded so it can never be written to the saved-instance bundle.
 * This is the single definition of "what gets persisted", so the no-secret test
 * can assert the key is absent here.
 */
internal fun aiProviderSaveableFields(form: AiProviderEditForm): Map<String, String> = mapOf(
    AI_PROVIDER_FORM_KEY_PROVIDER to form.provider,
    AI_PROVIDER_FORM_KEY_MODEL to form.model,
    AI_PROVIDER_FORM_KEY_BASE_URL to form.baseUrl,
    AI_PROVIDER_FORM_KEY_ENABLED to form.enabled.toString(),
)

/** Restore a form from a [aiProviderSaveableFields] snapshot; the key stays empty. */
internal fun aiProviderFormFromSaveable(fields: Map<String, String>): AiProviderEditForm =
    AiProviderEditForm(
        provider = fields[AI_PROVIDER_FORM_KEY_PROVIDER].orEmpty().ifBlank { DEFAULT_PROVIDER_SLUG },
        model = fields[AI_PROVIDER_FORM_KEY_MODEL].orEmpty(),
        baseUrl = fields[AI_PROVIDER_FORM_KEY_BASE_URL].orEmpty(),
        enabled = fields[AI_PROVIDER_FORM_KEY_ENABLED]?.toBooleanStrictOrNull() ?: true,
        apiKey = "",
    )

/**
 * Build the backend write payload. Trims text fields; omits `api_key` entirely
 * when blank (keep-existing semantics — never an empty string); omits `base_url`
 * for providers that don't use one.
 */
internal fun aiProviderSaveRequest(form: AiProviderEditForm): AiProviderConfigIn {
    val option = aiProviderOptionFor(form.provider)
    val trimmedKey = form.apiKey.trim()
    val trimmedBaseUrl = form.baseUrl.trim()
    return AiProviderConfigIn(
        provider = option.slug,
        model = form.model.trim(),
        enabled = form.enabled,
        baseUrl = if (option.usesBaseUrl) trimmedBaseUrl.takeIf { it.isNotEmpty() } else null,
        apiKey = trimmedKey.takeIf { it.isNotEmpty() },
    )
}

// ---------------------------------------------------------------------------
// Safe-status projection (read-only section above the form)
// ---------------------------------------------------------------------------

internal data class AiProviderEditStatusView(
    val configured: Boolean,
    val providerLine: String?,
    val modelLine: String?,
    val baseUrlLine: String?,
    val enabledLine: String,
    val keyLine: String?,
    val lastTestLine: String?,
    val lastTestedOnLine: String?,
    val sourceLine: String?,
    /** Plain-English state summary (the §7 row copy). Always safe. */
    val stateMessage: String,
    /** Extra read-only note when the config comes from a server env var. */
    val environmentNote: String?,
)

internal const val AI_PROVIDER_EDIT_UNCONFIGURED =
    "No AI provider is set up on your server yet."
internal const val AI_PROVIDER_EDIT_CONFIGURED_UNTESTED =
    "Provider saved but not yet tested. Run a test to confirm it works."
internal const val AI_PROVIDER_EDIT_CONFIGURED_OK =
    "Provider is configured and the last test passed."
internal const val AI_PROVIDER_EDIT_AUTH_FAILED =
    "The provider rejected the configured key. Re-enter and test the key."
internal const val AI_PROVIDER_EDIT_UNREACHABLE =
    "The provider endpoint couldn't be reached. Check the base URL and network."
internal const val AI_PROVIDER_EDIT_TEST_ERROR =
    "The provider test didn't complete. Re-check the configuration and try again."
internal const val AI_PROVIDER_EDIT_ENVIRONMENT_NOTE =
    "This provider is set by a server environment variable. Saving here will " +
        "override it in the database."

internal fun aiProviderEditStatusView(status: AiProviderConfigStatusOut): AiProviderEditStatusView {
    val option = aiProviderOptionFor(status.provider)
    val providerLine = status.provider?.takeIf { it.isNotBlank() }?.let { "Provider: ${option.label}" }
    val modelLine = status.model?.takeIf { it.isNotBlank() }?.let { "Model: $it" }
    val baseUrlLine = status.baseUrl
        ?.trim()
        ?.takeIf { it.isNotEmpty() && option.usesBaseUrl }
        ?.let { "Base URL: $it" }
    val enabledLine = "Enabled: ${if (status.enabled) "Yes" else "No"}"
    return AiProviderEditStatusView(
        configured = status.configured,
        providerLine = providerLine,
        modelLine = modelLine,
        baseUrlLine = baseUrlLine,
        enabledLine = enabledLine,
        keyLine = editKeyLineFor(status),
        lastTestLine = editLastTestLineFor(status.lastTestStatus),
        lastTestedOnLine = editLastTestedOnLine(status.lastTestAt),
        sourceLine = editSourceLineFor(status.source),
        stateMessage = aiProviderStateMessage(status),
        environmentNote = AI_PROVIDER_EDIT_ENVIRONMENT_NOTE
            .takeIf { status.source.trim().lowercase() == "environment" },
    )
}

/** Plain-English state copy derived from the safe status fields (design §7). */
internal fun aiProviderStateMessage(status: AiProviderConfigStatusOut): String {
    if (!status.configured && status.source.trim().lowercase() == "none") {
        return AI_PROVIDER_EDIT_UNCONFIGURED
    }
    return when (status.lastTestStatus?.trim()?.lowercase()) {
        "ok" -> AI_PROVIDER_EDIT_CONFIGURED_OK
        "auth_failed" -> AI_PROVIDER_EDIT_AUTH_FAILED
        "unreachable" -> AI_PROVIDER_EDIT_UNREACHABLE
        "error" -> AI_PROVIDER_EDIT_TEST_ERROR
        else -> AI_PROVIDER_EDIT_CONFIGURED_UNTESTED
    }
}

// Reuse the BYOAI-A3 read-only enrichment copy where it already exists so the
// edit screen and the status spoke describe the same fields identically.
private fun editLastTestLineFor(rawStatus: String?): String? =
    when (rawStatus?.trim()?.lowercase()) {
        "ok" -> AI_PROVIDER_LAST_TEST_OK
        "untested" -> AI_PROVIDER_LAST_TEST_UNTESTED
        "auth_failed" -> AI_PROVIDER_LAST_TEST_AUTH_FAILED
        "unreachable" -> AI_PROVIDER_LAST_TEST_UNREACHABLE
        "error" -> AI_PROVIDER_LAST_TEST_ERROR
        else -> null
    }

private fun editLastTestedOnLine(rawTimestamp: String?): String? {
    val trimmed = rawTimestamp?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val datePart = trimmed.substringBefore('T')
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(datePart)) return null
    return "Last tested: $datePart"
}

private fun editSourceLineFor(rawSource: String?): String? =
    when (rawSource?.trim()?.lowercase()) {
        "database" -> AI_PROVIDER_SOURCE_DATABASE
        "environment" -> AI_PROVIDER_SOURCE_ENVIRONMENT
        else -> null
    }

/**
 * Non-secret key indicator for the safe-status section. Only the bounded
 * `key_last4` tail is ever shown; the full key never exists on the client.
 */
private fun editKeyLineFor(status: AiProviderConfigStatusOut): String? {
    if (!status.keyPresent) return null
    val last4 = status.keyLast4?.trim().orEmpty()
    return if (last4.matches(Regex("""[A-Za-z0-9_-]{1,4}"""))) {
        "Stored key ending $last4"
    } else {
        "A key is stored on the server"
    }
}

// ---------------------------------------------------------------------------
// Action result copy
// ---------------------------------------------------------------------------

internal const val AI_PROVIDER_SAVE_SUCCESS = "Provider configuration saved."
internal const val AI_PROVIDER_TEST_COMPLETE = "Test complete. See current status above."
internal const val AI_PROVIDER_DELETE_SUCCESS = "Provider configuration cleared."

internal const val AI_PROVIDER_ERROR_ENCRYPTION =
    "Your server isn't ready to store provider keys yet. The server operator " +
        "must configure encryption before saving a key."
internal const val AI_PROVIDER_ERROR_BASE_URL =
    "Enter a base URL starting with http:// or https:// for this provider."
internal const val AI_PROVIDER_ERROR_PROVIDER = "Choose a provider."
internal const val AI_PROVIDER_ERROR_MODEL = "Enter a model."
internal const val AI_PROVIDER_ERROR_API_KEY = "Enter an API key."
internal const val AI_PROVIDER_ERROR_TEST_BEFORE_SAVE = "Save the configuration before testing it."
internal const val AI_PROVIDER_ERROR_UNAUTHORIZED =
    "This session can't change the provider configuration."
internal const val AI_PROVIDER_ERROR_GENERIC =
    "Something went wrong. Re-check the configuration and try again."

/** Map a coarse [AiProviderErrorCode] to friendly, display-safe copy (design §7). */
internal fun aiProviderErrorMessage(code: AiProviderErrorCode): String = when (code) {
    AiProviderErrorCode.EncryptionKeyRequired,
    AiProviderErrorCode.EncryptionKeyInvalid -> AI_PROVIDER_ERROR_ENCRYPTION
    AiProviderErrorCode.BaseUrlRequired,
    AiProviderErrorCode.BaseUrlMustBeHttp -> AI_PROVIDER_ERROR_BASE_URL
    AiProviderErrorCode.UnsupportedProvider -> AI_PROVIDER_ERROR_PROVIDER
    AiProviderErrorCode.ModelRequired -> AI_PROVIDER_ERROR_MODEL
    AiProviderErrorCode.ApiKeyRequired -> AI_PROVIDER_ERROR_API_KEY
    AiProviderErrorCode.TestBeforeSave -> AI_PROVIDER_ERROR_TEST_BEFORE_SAVE
    AiProviderErrorCode.Unauthorized -> AI_PROVIDER_ERROR_UNAUTHORIZED
    AiProviderErrorCode.Unknown -> AI_PROVIDER_ERROR_GENERIC
}
