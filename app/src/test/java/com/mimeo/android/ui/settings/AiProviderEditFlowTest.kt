package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderCatalogueItemOut
import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.AiProviderErrorCode
import com.mimeo.android.model.AiProviderStatusState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BYOAI-A6 — the edit flow consumes the backend-provided provider catalogue
 * instead of a mirrored Android list. These tests prove the dropdown, default
 * models, and base-URL rules all derive from the backend `catalogue`, that the
 * BYOAI-A5 gating/copy/request behavior is preserved, and that a missing
 * catalogue degrades safely without producing provider writes.
 */
class AiProviderEditFlowTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** A representative backend catalogue, mirroring the BYOAI-B4 contract shape. */
    private fun backendCatalogue() = listOf(
        AiProviderCatalogueItemOut(
            key = "anthropic",
            label = "Anthropic",
            defaultModel = "claude-haiku-4-5-20251001",
            baseUrlRequired = false,
            baseUrlAllowed = false,
            endpointKind = "fixed_endpoint",
        ),
        AiProviderCatalogueItemOut(
            key = "openai",
            label = "OpenAI",
            defaultModel = "gpt-4o-mini",
            baseUrlRequired = false,
            baseUrlAllowed = false,
            endpointKind = "fixed_endpoint",
        ),
        AiProviderCatalogueItemOut(
            key = "openai_compatible",
            label = "OpenAI-compatible",
            defaultModel = "model-name",
            baseUrlRequired = true,
            baseUrlAllowed = true,
            endpointKind = "openai_compatible",
            help = "Use an OpenAI-compatible HTTPS endpoint.",
        ),
        AiProviderCatalogueItemOut(
            key = "local",
            label = "Local OpenAI-compatible",
            defaultModel = "local-model",
            baseUrlRequired = true,
            baseUrlAllowed = true,
            endpointKind = "local_openai_compatible",
        ),
    )

    private fun status(
        configured: Boolean = true,
        canEdit: Boolean = true,
        provider: String? = "anthropic",
        model: String? = "claude-x",
        baseUrl: String? = null,
        enabled: Boolean = true,
        keyPresent: Boolean = true,
        keyLast4: String? = "1234",
        lastTestStatus: String? = "ok",
        lastTestAt: String? = "2026-06-10T14:30:00Z",
        source: String = "database",
        catalogue: List<AiProviderCatalogueItemOut> = backendCatalogue(),
    ) = AiProviderConfigStatusOut(
        provider = provider,
        model = model,
        baseUrl = baseUrl,
        enabled = enabled,
        configured = configured,
        keyPresent = keyPresent,
        keyLast4 = keyLast4,
        lastTestStatus = lastTestStatus,
        lastTestAt = lastTestAt,
        source = source,
        canEdit = canEdit,
        catalogue = catalogue,
    )

    private fun options(status: AiProviderConfigStatusOut? = status()) =
        aiProviderOptionsFrom(status)

    // ----- Gating -----

    @Test
    fun manageEntryVisibleOnlyWhenReadyAndCanEdit() {
        assertTrue(aiProviderManageEntryVisible(AiProviderStatusState.Ready(status(canEdit = true))))
        assertFalse(aiProviderManageEntryVisible(AiProviderStatusState.Ready(status(canEdit = false))))
        assertFalse(aiProviderManageEntryVisible(AiProviderStatusState.Loading))
        assertFalse(aiProviderManageEntryVisible(AiProviderStatusState.Idle))
        assertFalse(aiProviderManageEntryVisible(AiProviderStatusState.Unavailable))
    }

    @Test
    fun canEditDefaultsFalseWhenFieldAbsent() {
        // An older backend that omits can_edit must decode to false (status-only).
        val decoded = json.decodeFromString<AiProviderConfigStatusOut>(
            """{"provider":"anthropic","configured":true}""",
        )
        assertFalse(decoded.canEdit)
        assertFalse(aiProviderManageEntryVisible(AiProviderStatusState.Ready(decoded)))
    }

    // ----- Catalogue consumption -----

    @Test
    fun catalogueParsesFromBackendStatusJson() {
        val decoded = json.decodeFromString<AiProviderConfigStatusOut>(
            """
            {"provider":"anthropic","configured":true,"can_edit":true,
             "catalogue":[
               {"key":"anthropic","label":"Anthropic","default_model":"claude-haiku-4-5-20251001",
                "base_url_required":false,"base_url_allowed":false,"endpoint_kind":"fixed_endpoint","help":null},
               {"key":"openai_compatible","label":"OpenAI-compatible","default_model":"model-name",
                "base_url_required":true,"base_url_allowed":true,"endpoint_kind":"openai_compatible",
                "help":"Use an OpenAI-compatible HTTPS endpoint."}
             ]}
            """.trimIndent(),
        )
        val opts = aiProviderOptionsFrom(decoded)
        assertEquals(listOf("anthropic", "openai_compatible"), opts.map { it.slug })
        assertEquals("Anthropic", opts[0].label)
        assertEquals("claude-haiku-4-5-20251001", opts[0].defaultModel)
        assertFalse(opts[0].baseUrlAllowed)
        assertTrue(opts[1].baseUrlAllowed)
        assertTrue(opts[1].baseUrlRequired)
    }

    @Test
    fun dropdownOptionsComeFromBackendCatalogueInOrder() {
        // The dropdown iterates exactly what the backend supplied, in order.
        assertEquals(
            listOf("anthropic", "openai", "openai_compatible", "local"),
            options().map { it.slug },
        )
        assertEquals(
            listOf("Anthropic", "OpenAI", "OpenAI-compatible", "Local OpenAI-compatible"),
            options().map { it.label },
        )
    }

    @Test
    fun unknownCatalogueFieldsAreIgnoredSafely() {
        val decoded = json.decodeFromString<AiProviderConfigStatusOut>(
            """
            {"provider":"anthropic","configured":true,
             "catalogue":[
               {"key":"anthropic","label":"Anthropic","default_model":"claude-x",
                "base_url_required":false,"base_url_allowed":false,
                "future_field":"ignored","nested":{"a":1},"score":42}
             ]}
            """.trimIndent(),
        )
        val opts = aiProviderOptionsFrom(decoded)
        assertEquals(1, opts.size)
        assertEquals("anthropic", opts[0].slug)
        assertEquals("claude-x", opts[0].defaultModel)
    }

    @Test
    fun catalogueItemsWithoutAKeyAreDropped() {
        val catalogue = listOf(
            AiProviderCatalogueItemOut(key = "", label = "Bogus", defaultModel = "x"),
            AiProviderCatalogueItemOut(key = "openai", label = "OpenAI", defaultModel = "gpt-4o-mini"),
        )
        val opts = aiProviderOptionsFrom(status(catalogue = catalogue))
        assertEquals(listOf("openai"), opts.map { it.slug })
    }

    // ----- State copy -----

    @Test
    fun stateMessageCoversEveryRow() {
        assertEquals(
            AI_PROVIDER_EDIT_UNCONFIGURED,
            aiProviderStateMessage(status(configured = false, source = "none", keyPresent = false, lastTestStatus = null)),
        )
        assertEquals(
            AI_PROVIDER_EDIT_CONFIGURED_OK,
            aiProviderStateMessage(status(lastTestStatus = "ok")),
        )
        assertEquals(
            AI_PROVIDER_EDIT_CONFIGURED_UNTESTED,
            aiProviderStateMessage(status(lastTestStatus = "untested")),
        )
        assertEquals(
            AI_PROVIDER_EDIT_AUTH_FAILED,
            aiProviderStateMessage(status(lastTestStatus = "auth_failed")),
        )
        assertEquals(
            AI_PROVIDER_EDIT_UNREACHABLE,
            aiProviderStateMessage(status(lastTestStatus = "unreachable")),
        )
        assertEquals(
            AI_PROVIDER_EDIT_TEST_ERROR,
            aiProviderStateMessage(status(lastTestStatus = "error")),
        )
    }

    @Test
    fun environmentSourceAddsOverrideNoteAndStaysConfigured() {
        val view = aiProviderEditStatusView(options(), status(source = "environment"))
        assertEquals(AI_PROVIDER_EDIT_ENVIRONMENT_NOTE, view.environmentNote)
        assertEquals(AI_PROVIDER_SOURCE_ENVIRONMENT, view.sourceLine)
    }

    @Test
    fun statusViewUsesBackendLabelForProvider() {
        val view = aiProviderEditStatusView(options(), status(provider = "openai_compatible"))
        assertEquals("Provider: OpenAI-compatible", view.providerLine)
    }

    @Test
    fun errorCopyMapsEveryCode() {
        assertEquals(AI_PROVIDER_ERROR_ENCRYPTION, aiProviderErrorMessage(AiProviderErrorCode.EncryptionKeyRequired))
        assertEquals(AI_PROVIDER_ERROR_ENCRYPTION, aiProviderErrorMessage(AiProviderErrorCode.EncryptionKeyInvalid))
        assertEquals(AI_PROVIDER_ERROR_BASE_URL, aiProviderErrorMessage(AiProviderErrorCode.BaseUrlRequired))
        assertEquals(AI_PROVIDER_ERROR_BASE_URL, aiProviderErrorMessage(AiProviderErrorCode.BaseUrlMustBeHttp))
        assertEquals(AI_PROVIDER_ERROR_PROVIDER, aiProviderErrorMessage(AiProviderErrorCode.UnsupportedProvider))
        assertEquals(AI_PROVIDER_ERROR_MODEL, aiProviderErrorMessage(AiProviderErrorCode.ModelRequired))
        assertEquals(AI_PROVIDER_ERROR_API_KEY, aiProviderErrorMessage(AiProviderErrorCode.ApiKeyRequired))
        assertEquals(AI_PROVIDER_ERROR_TEST_BEFORE_SAVE, aiProviderErrorMessage(AiProviderErrorCode.TestBeforeSave))
        assertEquals(AI_PROVIDER_ERROR_UNAUTHORIZED, aiProviderErrorMessage(AiProviderErrorCode.Unauthorized))
        assertEquals(AI_PROVIDER_ERROR_GENERIC, aiProviderErrorMessage(AiProviderErrorCode.Unknown))
    }

    // ----- Form + request building -----

    @Test
    fun unconfiguredFormPrefillsBackendDefaultModelForFirstProvider() {
        // An unconfigured server shows the first catalogue provider with its
        // backend-provided default model already filled in.
        val opts = options()
        val form = aiProviderEditFormFrom(opts, null)
        assertEquals("anthropic", form.provider)
        assertEquals("claude-haiku-4-5-20251001", form.model)
    }

    @Test
    fun formUsesStoredModelOverDefaultWhenConfigured() {
        val opts = options()
        val form = aiProviderEditFormFrom(opts, status(provider = "openai", model = "gpt-4o"))
        assertEquals("openai", form.provider)
        assertEquals("gpt-4o", form.model)
    }

    @Test
    fun formFallsBackToBackendDefaultWhenStoredModelBlank() {
        val opts = options()
        val form = aiProviderEditFormFrom(opts, status(provider = "openai", model = ""))
        assertEquals("gpt-4o-mini", form.model)
    }

    @Test
    fun switchingProviderUsesBackendDefaultModel() {
        // Simulate the dropdown selecting a different provider: the option carries
        // the backend default model the screen applies.
        val opts = options()
        assertEquals("model-name", aiProviderOptionFor(opts, "openai_compatible").defaultModel)
        assertEquals("local-model", aiProviderOptionFor(opts, "local").defaultModel)
    }

    @Test
    fun baseUrlVisibilityFollowsBackendRules() {
        val opts = options()
        assertFalse(aiProviderShowBaseUrl(opts, "anthropic"))
        assertFalse(aiProviderShowBaseUrl(opts, "openai"))
        assertTrue(aiProviderShowBaseUrl(opts, "openai_compatible"))
        assertTrue(aiProviderShowBaseUrl(opts, "local"))
    }

    @Test
    fun baseUrlRequiredFollowsBackendRules() {
        val opts = options()
        assertFalse(aiProviderOptionFor(opts, "anthropic").baseUrlRequired)
        assertTrue(aiProviderOptionFor(opts, "openai_compatible").baseUrlRequired)
    }

    @Test
    fun baseUrlAllowedButNotRequiredIsHonored() {
        // A provider the backend marks allowed-but-optional shows the field without
        // forcing a value.
        val catalogue = listOf(
            AiProviderCatalogueItemOut(
                key = "azure",
                label = "Azure",
                defaultModel = "gpt-4o",
                baseUrlRequired = false,
                baseUrlAllowed = true,
            ),
        )
        val opts = aiProviderOptionsFrom(status(catalogue = catalogue))
        assertTrue(aiProviderShowBaseUrl(opts, "azure"))
        assertFalse(aiProviderOptionFor(opts, "azure").baseUrlRequired)
    }

    @Test
    fun saveRequestOmitsBaseUrlForProvidersThatDoNotUseIt() {
        val opts = options()
        val form = aiProviderEditFormFrom(opts, status(provider = "anthropic", model = "claude-x"))
            .copy(baseUrl = "https://example.com", apiKey = "sk-secret")
        val request = aiProviderSaveRequest(opts, form)
        assertNull(request.baseUrl)
        assertEquals("anthropic", request.provider)
        assertEquals("claude-x", request.model)
        assertEquals("sk-secret", request.apiKey)
    }

    @Test
    fun saveRequestKeepsBaseUrlForCompatibleProvider() {
        val opts = options()
        val form = AiProviderEditForm(
            provider = "openai_compatible",
            model = "local-model",
            baseUrl = " https://host:1234 ",
            enabled = true,
            apiKey = "  ",
        )
        val request = aiProviderSaveRequest(opts, form)
        assertEquals("https://host:1234", request.baseUrl)
        // Blank/whitespace key is omitted entirely (keep-existing semantics).
        assertNull(request.apiKey)
    }

    @Test
    fun saveRequestTrimsAndOmitsBlankKey() {
        val opts = options()
        val request = aiProviderSaveRequest(
            opts,
            AiProviderEditForm("openai", " gpt-4o-mini ", "", enabled = false, apiKey = ""),
        )
        assertEquals("gpt-4o-mini", request.model)
        assertEquals(false, request.enabled)
        assertNull(request.apiKey)
    }

    // ----- Fallback: missing/empty catalogue -----

    @Test
    fun missingCatalogueReportsUnavailableAndEmptyOptions() {
        val noCatalogue = status(catalogue = emptyList())
        assertFalse(aiProviderCatalogueAvailable(noCatalogue))
        assertTrue(aiProviderOptionsFrom(noCatalogue).isEmpty())
        assertFalse(aiProviderCatalogueAvailable(null))
    }

    @Test
    fun fallbackStatusViewStillRendersAFriendlyProviderLabel() {
        // With no catalogue, the read-only status section still shows a sensible
        // label derived from the slug rather than failing.
        val view = aiProviderEditStatusView(emptyList(), status(provider = "openai_compatible", catalogue = emptyList()))
        assertEquals("Provider: Openai Compatible", view.providerLine)
    }

    @Test
    fun fallbackOptionCannotSeedAProviderWrite() {
        // A synthesized fallback option carries no default model and no base URL
        // affordance, so it can never produce stale provider settings.
        val option = aiProviderOptionFor(emptyList(), "openai_compatible")
        assertEquals("", option.defaultModel)
        assertFalse(option.baseUrlAllowed)
        assertFalse(option.baseUrlRequired)
    }
}
