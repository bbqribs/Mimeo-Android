package com.mimeo.android.ui.reader

import com.mimeo.android.model.ContentSummaryOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarySheetPresentationTest {
    @Test
    fun unavailableCopyUsesBackendReasonCodes() {
        val disabled = summary(state = "missing", failureReason = "summaries_disabled")
        val providerMissing = summary(state = "missing", failureReason = "provider_not_configured")
        val tooShort = summary(state = "missing", failureReason = "content_too_short")

        assertEquals("Summaries are not available right now.", summaryUnavailableBody(disabled))
        assertEquals("Summaries are temporarily unavailable.", summaryUnavailableBody(providerMissing))
        assertEquals("This item is too short to summarize.", summaryUnavailableBody(tooShort))
    }

    @Test
    fun failedCopyHidesRawReasonCodes() {
        val providerMissing = summary(state = "failed", failureReason = "provider_not_configured")

        val copy = summaryFailedBody(providerMissing)

        assertEquals("Summaries are temporarily unavailable.", copy)
        assertFalse(copy.contains("provider_not_configured"))
    }

    @Test
    fun disclaimerFallsBackToAiCaveat() {
        assertEquals(
            "AI-generated summary. Verify important details.",
            summaryDisclaimerText(summary(state = "ready")),
        )
        assertEquals(
            "Server caveat.",
            summaryDisclaimerText(summary(state = "ready", disclaimer = "Server caveat.")),
        )
    }

    @Test
    fun outdatedReadyCopyExplainsRefreshIsManual() {
        val title = summaryOutdatedTitle()
        val body = summaryOutdatedBody()

        assertTrue(title.contains("older prompt"))
        assertTrue(body.contains("Refresh"))
        assertFalse("must not promise automatic regeneration", body.contains("automatic"))
    }

    @Test
    fun metadataStaysConcise() {
        val parts = summaryMetadataParts(
            summary(
                state = "ready",
                provider = "deepseek",
                model = "deepseek-chat",
                promptVersion = "summary.v3",
                generatedAt = "2026-05-25T10:30:00Z",
            ),
        )

        assertEquals(listOf("Generated 2026-05-25T10:30:00Z", "Model deepseek/deepseek-chat"), parts)
        assertTrue(parts.none { it.contains("summary.v3") })
    }

    private fun summary(
        state: String,
        failureReason: String? = null,
        disclaimer: String? = null,
        provider: String? = null,
        model: String? = null,
        promptVersion: String? = null,
        generatedAt: String? = null,
    ): ContentSummaryOut {
        return ContentSummaryOut(
            itemId = 1,
            state = state,
            failureReason = failureReason,
            disclaimer = disclaimer,
            provider = provider,
            model = model,
            promptVersion = promptVersion,
            generatedAt = generatedAt,
        )
    }
}
