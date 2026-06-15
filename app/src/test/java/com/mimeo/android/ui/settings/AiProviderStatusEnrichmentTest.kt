package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigStatusOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BYOAI-A3 — the read-only provider status enrichment is status-display only and
 * must never surface secrets, raw provider detail, raw status slugs, or any
 * editing affordance. These guard the safe copy and the silent-degrade behavior.
 */
class AiProviderStatusEnrichmentTest {

    private fun status(
        provider: String? = "anthropic",
        configured: Boolean = true,
        enabled: Boolean = true,
        keyPresent: Boolean = true,
        keyLast4: String? = "1234",
        lastTestStatus: String? = "ok",
        lastTestAt: String? = "2026-06-10T14:30:00Z",
        source: String = "database",
    ) = AiProviderConfigStatusOut(
        provider = provider,
        model = "claude-x",
        baseUrl = null,
        enabled = enabled,
        configured = configured,
        keyPresent = keyPresent,
        keyLast4 = keyLast4,
        lastTestStatus = lastTestStatus,
        lastTestAt = lastTestAt,
        source = source,
    )

    @Test
    fun configuredHealthyStatusProjectsSafeEnrichmentLines() {
        val enrichment = aiProviderStatusEnrichment(status())

        assertTrue(enrichment != null)
        requireNotNull(enrichment)
        assertEquals(AI_PROVIDER_LAST_TEST_OK, enrichment.lastTestLine)
        assertEquals("Last tested: 2026-06-10", enrichment.lastTestedOnLine)
        assertEquals(AI_PROVIDER_SOURCE_DATABASE, enrichment.sourceLine)
        assertEquals("Key: stored (ending 1234)", enrichment.keyLine)
    }

    @Test
    fun eachTestStatusMapsToFriendlyCopy() {
        assertEquals(
            AI_PROVIDER_LAST_TEST_UNTESTED,
            aiProviderStatusEnrichment(status(lastTestStatus = "untested"))?.lastTestLine,
        )
        assertEquals(
            AI_PROVIDER_LAST_TEST_AUTH_FAILED,
            aiProviderStatusEnrichment(status(lastTestStatus = "auth_failed"))?.lastTestLine,
        )
        assertEquals(
            AI_PROVIDER_LAST_TEST_UNREACHABLE,
            aiProviderStatusEnrichment(status(lastTestStatus = "unreachable"))?.lastTestLine,
        )
        assertEquals(
            AI_PROVIDER_LAST_TEST_ERROR,
            aiProviderStatusEnrichment(status(lastTestStatus = "error"))?.lastTestLine,
        )
    }

    @Test
    fun unknownOrAbsentTestStatusYieldsNoTestLine() {
        assertNull(aiProviderStatusEnrichment(status(lastTestStatus = null))?.lastTestLine)
        // An unexpected slug is dropped, never echoed raw.
        assertNull(
            aiProviderStatusEnrichment(status(lastTestStatus = "Traceback: boom"))?.lastTestLine,
        )
    }

    @Test
    fun environmentSourceIsNotedAndNoneSourceIsHidden() {
        assertEquals(
            AI_PROVIDER_SOURCE_ENVIRONMENT,
            aiProviderStatusEnrichment(status(source = "environment"))?.sourceLine,
        )
        // source=none with no other safe detail -> no enrichment block at all.
        val none = aiProviderStatusEnrichment(
            status(
                configured = false,
                keyPresent = false,
                keyLast4 = null,
                lastTestStatus = null,
                lastTestAt = null,
                source = "none",
            ),
        )
        assertNull(none)
    }

    @Test
    fun keyLineRequiresKeyPresentAndToleratesMissingLast4() {
        // No key present -> no key line.
        assertNull(
            aiProviderStatusEnrichment(status(keyPresent = false, keyLast4 = null))?.keyLine,
        )
        // Key present but no last4 -> generic stored line.
        assertEquals(
            "Key: stored",
            aiProviderStatusEnrichment(status(keyLast4 = null))?.keyLine,
        )
        // Malformed last4 is not echoed; falls back to generic stored line.
        assertEquals(
            "Key: stored",
            aiProviderStatusEnrichment(status(keyLast4 = "sk-secrettail"))?.keyLine,
        )
    }

    @Test
    fun malformedTimestampIsDroppedNotEchoed() {
        assertNull(aiProviderStatusEnrichment(status(lastTestAt = "not-a-date"))?.lastTestedOnLine)
        assertNull(aiProviderStatusEnrichment(status(lastTestAt = ""))?.lastTestedOnLine)
        // Date-only ISO value still works.
        assertEquals(
            "Last tested: 2026-01-02",
            aiProviderStatusEnrichment(status(lastTestAt = "2026-01-02"))?.lastTestedOnLine,
        )
    }

    @Test
    fun enrichmentNeverLeaksSecretsOrEditingCopy() {
        // A defensive payload carrying secret-shaped strings the backend should
        // never send in these fields. None may appear in any rendered line.
        val hostile = AiProviderConfigStatusOut(
            provider = "anthropic",
            model = "claude-x",
            baseUrl = "http://internal",
            enabled = true,
            configured = true,
            keyPresent = true,
            keyLast4 = "sk-supersecretkeymaterial",
            lastTestStatus = "AI_PROVIDER_ENCRYPTION_KEY leaked",
            lastTestAt = "Traceback (most recent call last)",
            source = "database",
        )
        val enrichment = aiProviderStatusEnrichment(hostile)
        requireNotNull(enrichment)

        val rendered = listOfNotNull(
            enrichment.lastTestLine,
            enrichment.lastTestedOnLine,
            enrichment.sourceLine,
            enrichment.keyLine,
        ).joinToString("\n")

        listOf("sk-", "AI_PROVIDER_ENCRYPTION_KEY", "Traceback", "supersecret").forEach {
            assertFalse("leaked secret-shaped token: $it", rendered.contains(it))
        }
        // Status display only: never invite key entry / editing in-app.
        assertFalse(rendered.lowercase().contains("paste"))
        assertFalse(rendered.lowercase().contains("enter"))
    }

    @Test
    fun isEmptyEnrichmentCollapsesToNull() {
        val empty = aiProviderStatusEnrichment(
            AiProviderConfigStatusOut(source = "none"),
        )
        assertNull(empty)
    }
}
