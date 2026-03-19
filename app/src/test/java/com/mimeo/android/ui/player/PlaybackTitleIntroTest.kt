package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackChunk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTitleIntroTest {
    @Test
    fun `toggle off keeps title intro disabled`() {
        val shouldUse = shouldUseTitleIntroOnPlaybackStart(
            allowTitleIntro = true,
            hasStartedPlaybackForItem = false,
            speakTitleBeforeArticleEnabled = false,
            title = "Example title",
            chunks = listOf(sampleChunk("Body starts here.")),
        )

        assertFalse(shouldUse)
    }

    @Test
    fun `toggle on enables title intro for first start including autoplay start path`() {
        val shouldUse = shouldUseTitleIntroOnPlaybackStart(
            allowTitleIntro = true,
            hasStartedPlaybackForItem = false,
            speakTitleBeforeArticleEnabled = true,
            title = "Example title",
            chunks = listOf(sampleChunk("Body starts here.")),
        )

        assertTrue(shouldUse)
    }

    @Test
    fun `duplicate title suppression skips intro when opening body repeats title`() {
        val shouldUse = shouldSpeakTitleBeforeBody(
            enabled = true,
            title = "Iran's Leaders Play Diplomatic Hardball",
            chunks = listOf(sampleChunk("Iran's Leaders Play Diplomatic Hardball, Emboldened by Oil Shock - WSJ ...")),
        )

        assertFalse(shouldUse)
    }

    @Test
    fun `missing title skips intro`() {
        val missingTitle = shouldSpeakTitleBeforeBody(
            enabled = true,
            title = "  ",
            chunks = listOf(sampleChunk("Body starts here.")),
        )
        val nullTitle = shouldSpeakTitleBeforeBody(
            enabled = true,
            title = null,
            chunks = listOf(sampleChunk("Body starts here.")),
        )

        assertFalse(missingTitle)
        assertFalse(nullTitle)
    }

    private fun sampleChunk(text: String): PlaybackChunk {
        return PlaybackChunk(
            index = 0,
            startChar = 0,
            endChar = text.length,
            text = text,
        )
    }
}

