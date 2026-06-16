package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.AiProviderErrorCode
import com.mimeo.android.model.AiProviderStatusState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BYOAI-A5 — pure logic for the operator provider edit flow: gating, copy
 * mapping, the safe-status projection, and the request builder.
 */
class AiProviderEditFlowTest {

    private val json = Json { ignoreUnknownKeys = true }

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
    )

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
        val view = aiProviderEditStatusView(status(source = "environment"))
        assertEquals(AI_PROVIDER_EDIT_ENVIRONMENT_NOTE, view.environmentNote)
        assertEquals(AI_PROVIDER_SOURCE_ENVIRONMENT, view.sourceLine)
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
    fun baseUrlShownOnlyForCompatibleAndLocal() {
        assertFalse(aiProviderShowBaseUrl("anthropic"))
        assertFalse(aiProviderShowBaseUrl("openai"))
        assertTrue(aiProviderShowBaseUrl("openai_compatible"))
        assertTrue(aiProviderShowBaseUrl("local"))
    }

    @Test
    fun saveRequestOmitsBaseUrlForProvidersThatDoNotUseIt() {
        val form = aiProviderEditFormFrom(status(provider = "anthropic", model = "claude-x"))
            .copy(baseUrl = "https://example.com", apiKey = "sk-secret")
        val request = aiProviderSaveRequest(form)
        assertNull(request.baseUrl)
        assertEquals("anthropic", request.provider)
        assertEquals("claude-x", request.model)
        assertEquals("sk-secret", request.apiKey)
    }

    @Test
    fun saveRequestKeepsBaseUrlForCompatibleProvider() {
        val form = AiProviderEditForm(
            provider = "openai_compatible",
            model = "local-model",
            baseUrl = " https://host:1234 ",
            enabled = true,
            apiKey = "  ",
        )
        val request = aiProviderSaveRequest(form)
        assertEquals("https://host:1234", request.baseUrl)
        // Blank/whitespace key is omitted entirely (keep-existing semantics).
        assertNull(request.apiKey)
    }

    @Test
    fun saveRequestTrimsAndOmitsBlankKey() {
        val request = aiProviderSaveRequest(
            AiProviderEditForm("openai", " gpt-4o-mini ", "", enabled = false, apiKey = ""),
        )
        assertEquals("gpt-4o-mini", request.model)
        assertEquals(false, request.enabled)
        assertNull(request.apiKey)
    }
}
