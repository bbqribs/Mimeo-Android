package com.mimeo.android.ui.settings

import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.SummaryCapabilitiesOut
import com.mimeo.android.model.SummaryModeOut
import com.mimeo.android.model.SummaryModelCapabilityOut
import com.mimeo.android.model.SummaryProviderCapabilityOut
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BYOAI-A4 — lock-in guardrail for the read-only AI Summaries spoke.
 *
 * Android is status-display only: it must never grow provider editing, key entry,
 * test/delete actions, OAuth, or prompt editing. These tests assert that NONE of
 * the user-visible copy — across every provider status and every enrichment
 * line — invites such an action, regardless of which BYOAI sub-ticket last
 * touched the wording. They are intentionally broad so that any future edit-flow
 * copy added by mistake fails CI here before it can ship.
 */
class AiProviderNoEditGuardTest {

    /** Phrases that would imply an edit/key/test/delete/OAuth/prompt affordance. */
    private val forbiddenPhrases = listOf(
        "manage provider",
        "add provider",
        "edit provider",
        "remove provider",
        "delete provider",
        "save provider",
        "test provider",
        "enter api key",
        "enter your key",
        "enter key",
        "paste key",
        "paste your key",
        "paste",
        "api key field",
        "oauth",
        "sign in with",
        "connect provider",
        "edit prompt",
        "prompt editing",
        "change the prompt",
    )

    /** Every static copy constant the spoke can render. */
    private val copyConstants = listOf(
        AI_SUMMARIES_DEFAULT_DISCLAIMER,
        AI_SUMMARIES_CONFIGURED_BY_LINE,
        AI_SUMMARIES_UNCONFIGURED_MESSAGE,
        AI_SUMMARIES_PROVIDER_UNAVAILABLE_MESSAGE,
        AI_SUMMARIES_ENABLED_MESSAGE,
        AI_SUMMARIES_DISABLED_MESSAGE,
        AI_SUMMARIES_CONFIGURE_ON_WEB_MESSAGE,
        AI_PROVIDER_LAST_TEST_OK,
        AI_PROVIDER_LAST_TEST_UNTESTED,
        AI_PROVIDER_LAST_TEST_AUTH_FAILED,
        AI_PROVIDER_LAST_TEST_UNREACHABLE,
        AI_PROVIDER_LAST_TEST_ERROR,
        AI_PROVIDER_SOURCE_DATABASE,
        AI_PROVIDER_SOURCE_ENVIRONMENT,
    )

    /** Build the full rendered surface for a capabilities + optional enrichment pair. */
    private fun renderedSurface(
        caps: SummaryCapabilitiesOut,
        status: AiProviderConfigStatusOut?,
    ): String {
        val data = aiSummariesSettingsViewData(caps)
        val enrichment = status?.let { aiProviderStatusEnrichment(it) }
        return (
            listOfNotNull(
                data.statusLabel,
                data.providerLine,
                data.modelLine,
                data.dailyLimitLine,
                data.defaultModeLabel,
                data.guidanceMessage,
                data.configureOnWebMessage,
                data.configuredByLine,
                data.disclaimer,
            ) + data.modeLabels +
                listOfNotNull(
                    enrichment?.lastTestLine,
                    enrichment?.lastTestedOnLine,
                    enrichment?.sourceLine,
                    enrichment?.keyLine,
                )
            ).joinToString("\n")
    }

    private fun caps(status: String, enabled: Boolean, name: String?) =
        SummaryCapabilitiesOut(
            enabled = enabled,
            provider = SummaryProviderCapabilityOut(status = status, displayName = name),
            model = SummaryModelCapabilityOut(displayName = "claude-x"),
            supportedModes = listOf(
                SummaryModeOut(kind = "abstract", label = "Standard"),
                SummaryModeOut(kind = "brief", label = "Brief"),
            ),
            defaultKind = "abstract",
        )

    private fun fullStatus(source: String, lastTestStatus: String) =
        AiProviderConfigStatusOut(
            provider = "anthropic",
            model = "claude-x",
            enabled = true,
            configured = true,
            keyPresent = true,
            keyLast4 = "1234",
            lastTestStatus = lastTestStatus,
            lastTestAt = "2026-06-10T14:30:00Z",
            source = source,
        )

    @Test
    fun noProviderStatusCopyConstantImpliesAnEditAffordance() {
        copyConstants.forEach { copy ->
            val lower = copy.lowercase()
            forbiddenPhrases.forEach { phrase ->
                assertFalse(
                    "copy constant invites an edit affordance ($phrase): \"$copy\"",
                    lower.contains(phrase),
                )
            }
        }
    }

    @Test
    fun everyRenderedSurfaceStaysStatusOnly() {
        // Cover each coarse status, each config source, and each last-test outcome
        // so no combination of enrichment + capabilities copy leaks an affordance.
        val capabilitiesMatrix = listOf(
            caps(status = "configured", enabled = true, name = "Anthropic"),
            caps(status = "configured", enabled = false, name = "OpenAI"),
            caps(status = "unconfigured", enabled = false, name = null),
            caps(status = "disabled", enabled = false, name = null),
        )
        val statusMatrix = listOf<AiProviderConfigStatusOut?>(
            null, // silent-fallback: enrichment endpoint unavailable
            fullStatus(source = "database", lastTestStatus = "ok"),
            fullStatus(source = "database", lastTestStatus = "auth_failed"),
            fullStatus(source = "database", lastTestStatus = "unreachable"),
            fullStatus(source = "database", lastTestStatus = "error"),
            fullStatus(source = "environment", lastTestStatus = "untested"),
        )

        for (capsCase in capabilitiesMatrix) {
            for (statusCase in statusMatrix) {
                val rendered = renderedSurface(capsCase, statusCase).lowercase()
                forbiddenPhrases.forEach { phrase ->
                    assertFalse(
                        "rendered surface invites an edit affordance ($phrase): $rendered",
                        rendered.contains(phrase),
                    )
                }
            }
        }
    }

    @Test
    fun envConfiguredProviderIsLabelledAsServerManagedNotEditable() {
        val rendered = renderedSurface(
            caps(status = "configured", enabled = true, name = "Anthropic"),
            fullStatus(source = "environment", lastTestStatus = "ok"),
        )
        // The env-configured note explains *where* the config lives; it must not
        // imply the user can change it from the app.
        assertTrue(rendered.contains(AI_PROVIDER_SOURCE_ENVIRONMENT))
        assertFalse(rendered.lowercase().contains("change"))
    }

    @Test
    fun keyTailIndicatorStaysShortAndAllowedCharsOnly() {
        // Exactly four allowed chars: surfaced as a tail.
        assertTrue(
            aiProviderStatusEnrichment(
                fullStatus(source = "database", lastTestStatus = "ok").copy(keyLast4 = "aZ9_"),
            )?.keyLine == "Key: stored (ending aZ9_)",
        )
        // Five chars (over the safe bound): never echoed; falls back to generic.
        assertTrue(
            aiProviderStatusEnrichment(
                fullStatus(source = "database", lastTestStatus = "ok").copy(keyLast4 = "abcde"),
            )?.keyLine == "Key: stored",
        )
        // Disallowed characters (space, dot, slash) are rejected, not echoed.
        listOf("a b", "a.b", "a/b", "<b>", "a+b").forEach { tail ->
            val line = aiProviderStatusEnrichment(
                fullStatus(source = "database", lastTestStatus = "ok").copy(keyLast4 = tail),
            )?.keyLine
            assertTrue(
                "disallowed key tail \"$tail\" must not be echoed, got: $line",
                line == "Key: stored",
            )
        }
    }
}
