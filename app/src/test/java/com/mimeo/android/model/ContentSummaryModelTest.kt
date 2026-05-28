package com.mimeo.android.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSummaryModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesContentSummaryOutContract() {
        val summary = json.decodeFromString<ContentSummaryOut>(
            """
            {
              "item_id": 42,
              "active_content_version_id": 7,
              "summary_content_version_id": 7,
              "state": "ready",
              "summary_kind": "abstract",
              "summary_text": "Short summary.",
              "provider": "anthropic",
              "model": "claude-test",
              "prompt_version": "summary.v1",
              "current_prompt_version": "summary.v2",
              "is_current_prompt_version": false,
              "can_refresh": true,
              "generated_at": "2026-05-24T12:00:00Z",
              "failure_reason": null,
              "can_request": true,
              "disclaimer": "AI-generated summary"
            }
            """.trimIndent(),
        )

        assertEquals(42, summary.itemId)
        assertEquals(ContentSummaryState.READY, summary.normalizedState())
        assertEquals("Short summary.", summary.summaryText)
        assertEquals("summary.v1", summary.promptVersion)
        assertEquals("summary.v2", summary.currentPromptVersion)
        assertEquals(false, summary.isCurrentPromptVersion)
        assertTrue(summary.canRefresh)
        assertTrue(summary.canRefreshOutdatedSummary())
        assertEquals("AI-generated summary", summary.disclaimer)
    }

    @Test
    fun refreshAffordanceRequiresReadyOutdatedAndPermission() {
        assertTrue(
            "ready + outdated + can_refresh shows soft refresh",
            summary(
                state = "ready",
                isCurrentPromptVersion = false,
                canRefresh = true,
            ).canRefreshOutdatedSummary(),
        )
        assertFalse(
            "ready + current prompt hides refresh",
            summary(
                state = "ready",
                isCurrentPromptVersion = true,
                canRefresh = true,
            ).canRefreshOutdatedSummary(),
        )
        assertFalse(
            "outdated but can_refresh=false hides refresh (provider gated server-side)",
            summary(
                state = "ready",
                isCurrentPromptVersion = false,
                canRefresh = false,
            ).canRefreshOutdatedSummary(),
        )
        assertFalse(
            "unknown freshness (older backend) hides refresh",
            summary(
                state = "ready",
                isCurrentPromptVersion = null,
                canRefresh = true,
            ).canRefreshOutdatedSummary(),
        )
        assertFalse(
            "pending state never offers refresh",
            summary(
                state = "pending",
                isCurrentPromptVersion = false,
                canRefresh = true,
            ).canRefreshOutdatedSummary(),
        )
        assertFalse(
            "stale state uses generate flow, not refresh",
            summary(
                state = "stale",
                isCurrentPromptVersion = false,
                canRefresh = true,
            ).canRefreshOutdatedSummary(),
        )
    }

    @Test
    fun requestabilityRequiresManualEligibleStates() {
        assertTrue(summary("missing", canRequest = true).canRequestGeneration())
        assertTrue(summary("failed", canRequest = true).canRequestGeneration())
        assertTrue(summary("stale", canRequest = true).canRequestGeneration())
        assertFalse(summary("pending", canRequest = true).canRequestGeneration())
        assertFalse(summary("ready", canRequest = true).canRequestGeneration())
        assertFalse(summary("missing", canRequest = false).canRequestGeneration())
    }

    @Test
    fun extractsSummaryErrorReasonFromFastApiEnvelope() {
        val message = """HTTP 503: {"detail":{"detail":"Summaries are not enabled","reason":"summaries_disabled"}}"""

        assertEquals(
            ContentSummaryFailureReason.SUMMARIES_DISABLED,
            contentSummaryFailureReasonFromApiMessage(message),
        )
    }

    @Test
    fun mapsKnownSummaryErrorReasonsToUserMessages() {
        assertEquals(
            "Summaries are temporarily unavailable.",
            contentSummaryFailureMessage(ContentSummaryFailureReason.PROVIDER_NOT_CONFIGURED),
        )
        assertEquals(
            "This item is too short to summarize.",
            contentSummaryFailureMessage(ContentSummaryFailureReason.CONTENT_TOO_SHORT),
        )
    }

    private fun summary(state: String, canRequest: Boolean): ContentSummaryOut {
        return ContentSummaryOut(
            itemId = 1,
            state = state,
            canRequest = canRequest,
        )
    }

    private fun summary(
        state: String,
        isCurrentPromptVersion: Boolean?,
        canRefresh: Boolean,
    ): ContentSummaryOut {
        return ContentSummaryOut(
            itemId = 1,
            state = state,
            isCurrentPromptVersion = isCurrentPromptVersion,
            canRefresh = canRefresh,
        )
    }
}
