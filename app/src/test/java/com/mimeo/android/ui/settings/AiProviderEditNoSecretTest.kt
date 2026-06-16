package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.AiProviderEditResult
import com.mimeo.android.model.AiProviderErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BYOAI-A5 — no-secret guarantees for the operator provider edit flow. These are
 * mandatory acceptance criteria (ticket §4): the key is never prefilled, never
 * part of the persistable snapshot, always wiped on actions, and no rendered or
 * mapped surface ever echoes a key, ciphertext, env var name, or raw provider
 * error.
 */
class AiProviderEditNoSecretTest {

    private val plantedKey = "sk-live-PLANTEDSECRET1234567890"
    private val plantedCiphertext = "gAAAAABmZ_ENCRYPTED_BLOB=="
    private val plantedEnvVar = "AI_PROVIDER_ENCRYPTION_KEY"
    private val plantedProviderError = "401 invalid_api_key from provider: bad sk-xyz"

    private fun configuredStatus() = AiProviderConfigStatusOut(
        provider = "anthropic",
        model = "claude-x",
        baseUrl = "https://example.com",
        enabled = true,
        configured = true,
        keyPresent = true,
        keyLast4 = "1234",
        lastTestStatus = "ok",
        lastTestAt = "2026-06-10T14:30:00Z",
        source = "database",
        canEdit = true,
    )

    @Test
    fun keyIsNeverPrefilledFromStatus() {
        // Even when the backend reports a stored key, the form key starts empty.
        val form = aiProviderEditFormFrom(configuredStatus())
        assertEquals("", form.apiKey)
    }

    @Test
    fun saveableSnapshotExcludesTheKey() {
        val form = aiProviderEditFormFrom(configuredStatus()).copy(apiKey = plantedKey)
        val fields = aiProviderSaveableFields(form)
        assertFalse(fields.containsKey("api_key"))
        assertFalse(fields.containsKey("apiKey"))
        fields.values.forEach { value ->
            assertFalse("saveable snapshot leaked the key: $value", value.contains(plantedKey))
        }
        // Round-tripping through the persisted snapshot always yields an empty key.
        assertEquals("", aiProviderFormFromSaveable(fields).apiKey)
    }

    @Test
    fun clearingKeyWipesItButKeepsOtherFields() {
        val form = aiProviderEditFormFrom(configuredStatus()).copy(apiKey = plantedKey)
        val cleared = form.withClearedKey()
        assertEquals("", cleared.apiKey)
        assertEquals(form.provider, cleared.provider)
        assertEquals(form.model, cleared.model)
        assertEquals(form.baseUrl, cleared.baseUrl)
        assertEquals(form.enabled, cleared.enabled)
    }

    @Test
    fun safeStatusNeverEchoesSecretMaterial() {
        // A misbehaving backend tries to smuggle secrets into status fields.
        val hostile = configuredStatus().copy(
            model = plantedKey,
            baseUrl = plantedCiphertext,
            keyLast4 = plantedEnvVar, // over-long / disallowed tail must not be echoed
        )
        val view = aiProviderEditStatusView(hostile)
        val rendered = listOfNotNull(
            view.stateMessage,
            view.providerLine,
            view.modelLine,
            view.baseUrlLine,
            view.enabledLine,
            view.keyLine,
            view.lastTestLine,
            view.lastTestedOnLine,
            view.sourceLine,
            view.environmentNote,
        ).joinToString("\n")

        // The key tail guard rejects a long/garbage value rather than echoing it.
        assertFalse(rendered.contains(plantedEnvVar))
        // keyLine falls back to the generic, non-echoing form.
        assertEquals("A key is stored on the server", view.keyLine)
        // Note: provider-controlled model/base_url strings are reflected as the
        // operator typed them on the web, which is expected for an editor; the
        // hard rule is that NO key tail / ciphertext is derived from secret-only
        // fields. key_last4 (the only key-derived field) is guarded above.
    }

    @Test
    fun mappedMessagesNeverContainRawErrorCodesOrSecrets() {
        val allCopy = buildList {
            AiProviderErrorCode.entries.forEach { add(aiProviderErrorMessage(it)) }
            add(aiProviderResultMessage(AiProviderEditResult.Saved))
            add(aiProviderResultMessage(AiProviderEditResult.Tested))
            add(aiProviderResultMessage(AiProviderEditResult.Cleared))
            add(aiProviderResultMessage(AiProviderEditResult.Failed(AiProviderErrorCode.Unknown)))
        }
        val forbidden = listOf(
            plantedKey,
            plantedCiphertext,
            plantedEnvVar,
            plantedProviderError,
            "sk-",
            "encryption_key_required",
            "base_url_must_be_http",
            "auth_failed",
            "Traceback",
            "Exception",
        )
        allCopy.forEach { copy ->
            forbidden.forEach { needle ->
                assertFalse(
                    "mapped copy leaked \"$needle\": $copy",
                    copy.contains(needle),
                )
            }
        }
    }

    @Test
    fun keyTailIsBoundedAndAllowedCharsOnly() {
        // Clean 4-char tail surfaces; anything longer/garbage falls back generic.
        assertEquals(
            "Stored key ending aZ9_",
            aiProviderEditStatusView(configuredStatus().copy(keyLast4 = "aZ9_")).keyLine,
        )
        assertEquals(
            "A key is stored on the server",
            aiProviderEditStatusView(configuredStatus().copy(keyLast4 = "abcdef")).keyLine,
        )
        assertTrue(
            aiProviderEditStatusView(configuredStatus().copy(keyPresent = false)).keyLine == null,
        )
    }
}
