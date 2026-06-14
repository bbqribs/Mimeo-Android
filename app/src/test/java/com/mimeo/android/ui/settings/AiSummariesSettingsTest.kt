package com.mimeo.android.ui.settings

import com.mimeo.android.model.SummaryCapabilitiesOut
import com.mimeo.android.model.SummaryDailyLimitOut
import com.mimeo.android.model.SummaryModeOut
import com.mimeo.android.model.SummaryModelCapabilityOut
import com.mimeo.android.model.SummaryProviderCapabilityOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-AUX-3 — the AI Summaries settings spoke shows only backend-owned, safe
 * values. These guard the copy and confirm no raw provider internals leak.
 */
class AiSummariesSettingsTest {

    @Test
    fun enabledCapabilitiesProjectSafeDisplayValues() {
        val caps = SummaryCapabilitiesOut(
            enabled = true,
            provider = SummaryProviderCapabilityOut(status = "configured", displayName = "Anthropic"),
            model = SummaryModelCapabilityOut(displayName = "claude-x"),
            supportedModes = listOf(
                SummaryModeOut(kind = "abstract", label = "Standard"),
                SummaryModeOut(kind = "brief", label = "Brief"),
                SummaryModeOut(kind = "key_points", label = "Key points"),
            ),
            defaultKind = "abstract",
            dailyLimit = SummaryDailyLimitOut(limit = 20, used = 3, windowHours = 24),
            disclaimer = "Server caveat.",
        )

        val data = aiSummariesSettingsViewData(caps)

        assertTrue(data.enabled)
        assertEquals("Enabled", data.statusLabel)
        assertEquals("Anthropic", data.providerLine)
        assertEquals("claude-x", data.modelLine)
        assertEquals("3 of 20 used today", data.dailyLimitLine)
        assertEquals(listOf("Standard", "Brief", "Key points"), data.modeLabels)
        assertEquals("Standard", data.defaultModeLabel)
        assertEquals("Server caveat.", data.disclaimer)
    }

    @Test
    fun disabledCapabilitiesHideProviderAndModelLines() {
        val caps = SummaryCapabilitiesOut(
            enabled = false,
            provider = SummaryProviderCapabilityOut(status = "disabled", displayName = null),
            model = SummaryModelCapabilityOut(displayName = null),
            supportedModes = listOf(
                SummaryModeOut(kind = "abstract", label = "Standard"),
                SummaryModeOut(kind = "brief", label = "Brief"),
            ),
            dailyLimit = SummaryDailyLimitOut(limit = 0, used = 0),
        )

        val data = aiSummariesSettingsViewData(caps)

        assertFalse(data.enabled)
        assertEquals("Disabled", data.statusLabel)
        assertNull(data.providerLine)
        assertNull(data.modelLine)
        // Zero/absent limit -> no daily-limit line.
        assertNull(data.dailyLimitLine)
        // Falls back to the default disclaimer.
        assertEquals(AI_SUMMARIES_DEFAULT_DISCLAIMER, data.disclaimer)
    }

    @Test
    fun dailyLimitAnnotatesNonDefaultWindow() {
        val caps = SummaryCapabilitiesOut(
            enabled = true,
            dailyLimit = SummaryDailyLimitOut(limit = 10, used = 2, windowHours = 12),
        )
        assertEquals("2 of 10 used per 12h", aiSummariesSettingsViewData(caps).dailyLimitLine)
    }

    @Test
    fun viewDataNeverExposesRawProviderStatusToken() {
        val caps = SummaryCapabilitiesOut(
            enabled = true,
            provider = SummaryProviderCapabilityOut(status = "unconfigured", displayName = "OpenAI"),
        )
        val data = aiSummariesSettingsViewData(caps)
        // Only the display name is surfaced; the raw status slug is never shown.
        assertEquals("OpenAI", data.providerLine)
        assertFalse(data.statusLabel.contains("unconfigured"))
    }

    // --- BYOAI-A1: richer status display (configured / unconfigured / disabled /
    // provider unavailable) derived from existing safe fields, status-display only.

    @Test
    fun configuredEnabledStatusShowsEnabledWithNoConfigureOnWebPrompt() {
        val caps = SummaryCapabilitiesOut(
            enabled = true,
            provider = SummaryProviderCapabilityOut(status = "configured", displayName = "Anthropic"),
            model = SummaryModelCapabilityOut(displayName = "claude-x"),
        )
        val data = aiSummariesSettingsViewData(caps)

        assertEquals(AiSummariesStatus.Enabled, data.status)
        assertEquals("Enabled", data.statusLabel)
        assertEquals(AI_SUMMARIES_ENABLED_MESSAGE, data.guidanceMessage)
        // Nothing to fix on the web when summaries are already usable.
        assertNull(data.configureOnWebMessage)
        assertEquals("Your Mimeo server", data.configuredByLine)
    }

    @Test
    fun unconfiguredProviderShowsSetupCopyAndConfigureOnWeb() {
        val caps = SummaryCapabilitiesOut(
            enabled = false,
            provider = SummaryProviderCapabilityOut(status = "unconfigured", displayName = null),
        )
        val data = aiSummariesSettingsViewData(caps)

        assertEquals(AiSummariesStatus.Unconfigured, data.status)
        assertEquals("Not configured", data.statusLabel)
        assertEquals(
            "Set up an AI provider on your Mimeo server to enable summaries.",
            data.guidanceMessage,
        )
        assertEquals(AI_SUMMARIES_CONFIGURE_ON_WEB_MESSAGE, data.configureOnWebMessage)
    }

    @Test
    fun configuredButNotEnabledIsReportedAsProviderUnavailable() {
        // Mirrors a server whose stored key became unusable (e.g. after restore):
        // the provider row is still "configured" but summaries are not enabled.
        val caps = SummaryCapabilitiesOut(
            enabled = false,
            provider = SummaryProviderCapabilityOut(status = "configured", displayName = "OpenAI"),
        )
        val data = aiSummariesSettingsViewData(caps)

        assertEquals(AiSummariesStatus.ProviderUnavailable, data.status)
        assertEquals("Provider unavailable", data.statusLabel)
        assertEquals(AI_SUMMARIES_PROVIDER_UNAVAILABLE_MESSAGE, data.guidanceMessage)
        assertEquals(AI_SUMMARIES_CONFIGURE_ON_WEB_MESSAGE, data.configureOnWebMessage)
    }

    @Test
    fun disabledServerShowsDisabledCopyWithoutConfigureOnWeb() {
        val caps = SummaryCapabilitiesOut(
            enabled = false,
            provider = SummaryProviderCapabilityOut(status = "disabled", displayName = null),
        )
        val data = aiSummariesSettingsViewData(caps)

        assertEquals(AiSummariesStatus.Disabled, data.status)
        assertEquals("Disabled", data.statusLabel)
        assertEquals(AI_SUMMARIES_DISABLED_MESSAGE, data.guidanceMessage)
        // Operator chose to disable; there is no web action to surface.
        assertNull(data.configureOnWebMessage)
    }

    @Test
    fun renderedCopyNeverLeaksSecretsOrEditingAffordances() {
        // A defensive payload carrying secret-shaped strings the backend should
        // never send. None of them must appear in any displayed field, and the
        // copy must never invite key entry within the app.
        val caps = SummaryCapabilitiesOut(
            enabled = true,
            provider = SummaryProviderCapabilityOut(status = "configured", displayName = "Anthropic"),
            model = SummaryModelCapabilityOut(displayName = "claude-x"),
            disclaimer = "Server caveat.",
        )
        val data = aiSummariesSettingsViewData(caps)

        val rendered = listOfNotNull(
            data.statusLabel,
            data.providerLine,
            data.modelLine,
            data.dailyLimitLine,
            data.defaultModeLabel,
            data.guidanceMessage,
            data.configureOnWebMessage,
            data.configuredByLine,
            data.disclaimer,
        ).joinToString("\n") + data.modeLabels.joinToString("\n")

        listOf("sk-", "AI_PROVIDER_ENCRYPTION_KEY", "ciphertext", "Traceback").forEach {
            assertFalse("leaked secret-shaped token: $it", rendered.contains(it))
        }
        // Status display only: never prompt the user to paste a key in-app.
        assertFalse(rendered.contains("paste"))
        assertFalse(rendered.lowercase().contains("enter your key"))
    }
}
