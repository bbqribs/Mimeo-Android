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
}
