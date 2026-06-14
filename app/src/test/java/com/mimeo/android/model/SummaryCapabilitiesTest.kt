package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-AUX-3 — guards parsing of the backend `/summary/capabilities` contract and
 * the pure kind-resolution helpers the reader/settings rely on.
 */
class SummaryCapabilitiesTest {

    // Mirror ApiClient's serializer leniency.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesEnabledCapabilitiesContract() {
        val payload = """
            {
              "enabled": true,
              "provider": { "status": "configured", "display_name": "Anthropic" },
              "model": { "display_name": "claude-x" },
              "supported_modes": [
                { "kind": "abstract", "label": "Standard", "description": "Full summary." },
                { "kind": "brief", "label": "Brief", "description": "Short." },
                { "kind": "key_points", "label": "Key points", "description": "Bullets." }
              ],
              "default_kind": "abstract",
              "daily_limit": { "limit": 20, "used": 3, "window_hours": 24 },
              "disclaimer": "Server caveat."
            }
        """.trimIndent()

        val caps = json.decodeFromString<SummaryCapabilitiesOut>(payload)

        assertTrue(caps.enabled)
        assertEquals("configured", caps.provider.status)
        assertEquals("Anthropic", caps.provider.displayName)
        assertEquals("claude-x", caps.model.displayName)
        assertEquals(listOf("abstract", "brief", "key_points"), caps.supportedModes.map { it.kind })
        assertEquals("Standard", caps.supportedModes.first().label)
        assertEquals("abstract", caps.defaultKind)
        assertEquals(20, caps.dailyLimit?.limit)
        assertEquals(3, caps.dailyLimit?.used)
        assertEquals("Server caveat.", caps.disclaimer)
    }

    @Test
    fun parsesDisabledCapabilitiesWithSafeNulls() {
        val payload = """
            {
              "enabled": false,
              "provider": { "status": "disabled" },
              "model": {},
              "supported_modes": [],
              "default_kind": "abstract",
              "daily_limit": { "limit": 0, "used": 0, "window_hours": 24 }
            }
        """.trimIndent()

        val caps = json.decodeFromString<SummaryCapabilitiesOut>(payload)

        assertFalse(caps.enabled)
        assertEquals("disabled", caps.provider.status)
        assertNull(caps.provider.displayName)
        assertNull(caps.model.displayName)
        assertNull(caps.disclaimer)
    }

    @Test
    fun toleratesMissingFieldsViaDefaults() {
        // Minimal/forward-compatible payload: unknown keys ignored, missing
        // optional fields fall back to safe defaults rather than throwing.
        val caps = json.decodeFromString<SummaryCapabilitiesOut>(
            """{ "enabled": true, "future_flag": 1 }""",
        )
        assertTrue(caps.enabled)
        assertEquals("disabled", caps.provider.status)
        assertEquals(SUMMARY_KIND_ABSTRACT, caps.defaultKind)
        assertNull(caps.dailyLimit)
    }

    @Test
    fun availableModesFallBackToSingleStandardWhenEmpty() {
        val caps = SummaryCapabilitiesOut(enabled = true, supportedModes = emptyList())
        val modes = caps.availableModes()
        assertEquals(1, modes.size)
        assertEquals(SUMMARY_KIND_ABSTRACT, modes.first().kind)
    }

    @Test
    fun availableModesDedupeBlankAndDuplicateKinds() {
        val caps = SummaryCapabilitiesOut(
            supportedModes = listOf(
                SummaryModeOut(kind = "abstract", label = "Standard"),
                SummaryModeOut(kind = "", label = "Bogus"),
                SummaryModeOut(kind = "abstract", label = "Dup"),
                SummaryModeOut(kind = "brief", label = "Brief"),
            ),
        )
        assertEquals(listOf("abstract", "brief"), caps.availableModes().map { it.kind })
    }

    @Test
    fun resolvedDefaultKindPrefersBackendDefaultWhenSupported() {
        val caps = SummaryCapabilitiesOut(
            supportedModes = listOf(
                SummaryModeOut(kind = "abstract"),
                SummaryModeOut(kind = "brief"),
            ),
            defaultKind = "brief",
        )
        assertEquals("brief", caps.resolvedDefaultKind())
    }

    @Test
    fun resolvedDefaultKindFallsBackWhenDefaultUnsupported() {
        val caps = SummaryCapabilitiesOut(
            supportedModes = listOf(SummaryModeOut(kind = "brief"), SummaryModeOut(kind = "abstract")),
            defaultKind = "nonexistent",
        )
        // abstract present -> prefer it over arbitrary first mode.
        assertEquals("abstract", caps.resolvedDefaultKind())

        val noAbstract = SummaryCapabilitiesOut(
            supportedModes = listOf(SummaryModeOut(kind = "brief"), SummaryModeOut(kind = "key_points")),
            defaultKind = "nonexistent",
        )
        assertEquals("brief", noAbstract.resolvedDefaultKind())
    }

    @Test
    fun coerceSelectedKindKeepsSupportedAndReplacesUnsupported() {
        val caps = SummaryCapabilitiesOut(
            supportedModes = listOf(SummaryModeOut(kind = "abstract"), SummaryModeOut(kind = "brief")),
            defaultKind = "abstract",
        )
        assertEquals("brief", caps.coerceSelectedKind("brief"))
        assertEquals("abstract", caps.coerceSelectedKind("key_points"))
        assertEquals("abstract", caps.coerceSelectedKind(null))
    }

    @Test
    fun labelForKindUsesBackendLabelThenTitleCasedSlug() {
        val caps = SummaryCapabilitiesOut(
            supportedModes = listOf(SummaryModeOut(kind = "key_points", label = "Key points")),
        )
        assertEquals("Key points", caps.labelForKind("key_points"))
        // Unknown kind -> title-cased slug fallback.
        assertEquals("Brief", caps.labelForKind("brief"))
        assertEquals("Key points", defaultLabelForKind("key_points"))
    }
}
