package com.mimeo.android.ui.player

import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import org.junit.Assert.assertEquals
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
            speakTitleBeforeArticleOnAutoplayEnabled = false,
            openIntent = PlaybackOpenIntent.ManualOpen,
            startPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
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
            speakTitleBeforeArticleOnAutoplayEnabled = true,
            openIntent = PlaybackOpenIntent.AutoContinue,
            startPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            title = "Example title",
            chunks = listOf(sampleChunk("Body starts here.")),
        )

        assertTrue(shouldUse)
    }

    @Test
    fun `autoplay title intro disabled by default control`() {
        val shouldUse = shouldUseTitleIntroOnPlaybackStart(
            allowTitleIntro = true,
            hasStartedPlaybackForItem = false,
            speakTitleBeforeArticleEnabled = true,
            speakTitleBeforeArticleOnAutoplayEnabled = false,
            openIntent = PlaybackOpenIntent.AutoContinue,
            startPosition = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            title = "Example title",
            chunks = listOf(sampleChunk("Body starts here.")),
        )

        assertFalse(shouldUse)
    }

    @Test
    fun `duplicate title no longer suppresses intro`() {
        val shouldUse = shouldSpeakTitleBeforeBody(
            enabled = true,
            title = "Iran's Leaders Play Diplomatic Hardball",
            chunks = listOf(sampleChunk("Iran's Leaders Play Diplomatic Hardball, Emboldened by Oil Shock - WSJ ...")),
        )

        assertTrue(shouldUse)
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

    @Test
    fun `title intro done event is accepted regardless of current chunk`() {
        assertTrue(shouldAcceptDoneEventChunk(eventChunkIndex = -1, currentChunkIndex = 7))
        assertTrue(shouldAcceptDoneEventChunk(eventChunkIndex = 7, currentChunkIndex = 7))
        assertFalse(shouldAcceptDoneEventChunk(eventChunkIndex = 3, currentChunkIndex = 7))
    }

    @Test
    fun `prefix skip requires at least three matched words`() {
        val skipTwoWords = computeTitlePrefixSkipChars(
            title = "Tories only",
            openingText = "Tories only party with a plan, says Badenoch.",
            minMatchedWords = 3,
        )
        val skipThreeWords = computeTitlePrefixSkipChars(
            title = "Tories only party",
            openingText = "Tories only party with a plan, says Badenoch.",
            minMatchedWords = 3,
        )

        assertEquals(0, skipTwoWords)
        assertTrue(skipThreeWords > 0)
    }

    @Test
    fun `prefix skip advances opening playback offset when intro is enabled`() {
        val opening = "Tories only party with a plan, says Badenoch as she launches election campaign."
        val skip = computeTitlePrefixSkipChars(
            title = "Tories only party with a plan - BBC",
            openingText = opening,
            minMatchedWords = 3,
        )
        val start = applyTitlePrefixSkipToStartPosition(
            start = PlaybackPosition(chunkIndex = 0, offsetInChunkChars = 0),
            chunks = listOf(sampleChunk(opening)),
            skipCharsFromOpening = skip,
        )

        assertTrue(skip > 0)
        assertEquals(0, start.chunkIndex)
        assertEquals(skip, start.offsetInChunkChars)
    }

    @Test
    fun `prefix skip tolerates filler words in opening sentence`() {
        val opening = "Tories are the only party with a plan, says Badenoch."
        val skip = computeTitlePrefixSkipChars(
            title = "Tories only party with a plan - BBC",
            openingText = opening,
            minMatchedWords = 3,
        )

        assertTrue(skip > 0)
    }

    @Test
    fun `prefix skip handles source suffix in title for BBC-style headline`() {
        val title = "UK sets targets to boost steel making and cut imports - BBC News"
        val opening = "UK sets targets to boost steel making and cut imports IMAGE SOURCE,PA MEDIA By Jemma Crew Business..."
        val skip = computeTitlePrefixSkipChars(
            title = title,
            openingText = opening,
            minMatchedWords = 3,
        )

        assertTrue(skip > 0)
        val remainder = opening.substring(skip).trimStart()
        assertTrue(remainder.startsWith("IMAGE SOURCE", ignoreCase = true))
    }

    @Test
    fun `prefix skip is delimiter agnostic for source tail`() {
        val title = "UK sets targets to boost steel making and cut imports | BBC News"
        val opening = "UK sets targets to boost steel making and cut imports IMAGE SOURCE,PA MEDIA..."
        val skip = computeTitlePrefixSkipChars(
            title = title,
            openingText = opening,
            minMatchedWords = 3,
        )

        assertTrue(skip > 0)
        val remainder = opening.substring(skip).trimStart()
        assertTrue(remainder.startsWith("IMAGE SOURCE", ignoreCase = true))
    }

    @Test
    fun `prefix skip does not modify resumed nonzero playback position`() {
        val opening = "Tories only party with a plan, says Badenoch as she launches election campaign."
        val start = applyTitlePrefixSkipToStartPosition(
            start = PlaybackPosition(chunkIndex = 1, offsetInChunkChars = 42),
            chunks = listOf(sampleChunk(opening), sampleChunk("Second chunk")),
            skipCharsFromOpening = 20,
        )

        assertEquals(1, start.chunkIndex)
        assertEquals(42, start.offsetInChunkChars)
    }

    @Test
    fun `title intro is skipped for non-top resume start position`() {
        val shouldUse = shouldUseTitleIntroOnPlaybackStart(
            allowTitleIntro = true,
            hasStartedPlaybackForItem = false,
            speakTitleBeforeArticleEnabled = true,
            speakTitleBeforeArticleOnAutoplayEnabled = true,
            openIntent = PlaybackOpenIntent.ManualOpen,
            startPosition = PlaybackPosition(chunkIndex = 2, offsetInChunkChars = 10),
            title = "Example title",
            chunks = listOf(sampleChunk("Example title then body.")),
        )

        assertFalse(shouldUse)
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
