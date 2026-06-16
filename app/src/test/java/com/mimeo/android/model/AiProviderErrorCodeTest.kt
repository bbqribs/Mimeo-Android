package com.mimeo.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BYOAI-A5 — [parseAiProviderErrorCode] maps a backend response to a coarse,
 * display-safe code by matching only known stable slugs and otherwise the HTTP
 * status. It must never depend on echoing the raw body.
 */
class AiProviderErrorCodeTest {

    @Test
    fun mapsKnownBadConfigSlugs() {
        val cases = mapOf(
            "encryption_key_required" to AiProviderErrorCode.EncryptionKeyRequired,
            "encryption_key_invalid" to AiProviderErrorCode.EncryptionKeyInvalid,
            "base_url_required" to AiProviderErrorCode.BaseUrlRequired,
            "base_url_must_be_http" to AiProviderErrorCode.BaseUrlMustBeHttp,
            "unsupported_provider" to AiProviderErrorCode.UnsupportedProvider,
            "model_required" to AiProviderErrorCode.ModelRequired,
            "api_key_required" to AiProviderErrorCode.ApiKeyRequired,
        )
        cases.forEach { (slug, expected) ->
            val body = """{"detail":{"code":"$slug"}}"""
            assertEquals(expected, parseAiProviderErrorCode(400, body))
        }
    }

    @Test
    fun matchesSlugCaseInsensitively() {
        assertEquals(
            AiProviderErrorCode.EncryptionKeyRequired,
            parseAiProviderErrorCode(400, "ENCRYPTION_KEY_REQUIRED"),
        )
    }

    @Test
    fun classifiesByStatusWhenNoSlugPresent() {
        assertEquals(AiProviderErrorCode.TestBeforeSave, parseAiProviderErrorCode(409, "{}"))
        assertEquals(AiProviderErrorCode.Unauthorized, parseAiProviderErrorCode(401, "{}"))
        assertEquals(AiProviderErrorCode.Unauthorized, parseAiProviderErrorCode(403, null))
        assertEquals(AiProviderErrorCode.Unknown, parseAiProviderErrorCode(500, "boom"))
        assertEquals(AiProviderErrorCode.Unknown, parseAiProviderErrorCode(400, ""))
    }

    @Test
    fun knownSlugWinsOverStatus() {
        // A 403 body that also carries a config slug should classify by the slug.
        assertEquals(
            AiProviderErrorCode.ModelRequired,
            parseAiProviderErrorCode(403, """{"code":"model_required"}"""),
        )
    }
}
