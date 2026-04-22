package com.mimeo.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackObservabilityTest {

    @Test
    fun manualStartSourceUsesQueueProgressWhenKnownProgressPresent() {
        val source = resolveOpenStartSource(
            openIntent = PlaybackOpenIntent.ManualOpen,
            knownProgress = 42,
            hasChunks = true,
        )

        assertEquals("manual:queue_progress_percent", source)
    }

    @Test
    fun autoContinueStartSourceAlwaysUsesBeginning() {
        val source = resolveOpenStartSource(
            openIntent = PlaybackOpenIntent.AutoContinue,
            knownProgress = 88,
            hasChunks = true,
        )

        assertEquals("autocontinue:start_of_item", source)
    }

    @Test
    fun observabilityLinesExposeExpectedHandoffAndSeedFields() {
        val lines = playbackObservabilityLines(
            PlaybackObservabilityUiState(
                currentItemId = 123,
                requestedItemId = 456,
                openIntent = PlaybackOpenIntent.ManualOpen,
                startSource = "manual:queue_progress_percent",
                knownProgress = 34,
                seededChunk = 2,
                seededOffset = 80,
                handoffPending = true,
                handoffSettled = false,
                autoPath = false,
            ),
        )

        assertEquals(5, lines.size)
        assertTrue(lines[0].contains("current=123"))
        assertTrue(lines[0].contains("requested=456"))
        assertTrue(lines[1].contains("open_intent=ManualOpen"))
        assertTrue(lines[2].contains("start_source=manual:queue_progress_percent"))
        assertTrue(lines[3].contains("chunk=2"))
        assertTrue(lines[3].contains("offset=80"))
        assertTrue(lines[4].contains("pending=true"))
        assertTrue(lines[4].contains("settled=false"))
    }

    @Test
    fun loadingPlaceholderShownDuringHandoffOrLoad() {
        val waitingForRequestedItem = shouldShowReaderLoadingPlaceholder(
            waitingForRequestedItem = true,
            hasStalePayloadForCurrentItem = false,
            isLoading = false,
            transitionSettled = false,
        )
        val activeLoad = shouldShowReaderLoadingPlaceholder(
            waitingForRequestedItem = false,
            hasStalePayloadForCurrentItem = false,
            isLoading = true,
            transitionSettled = false,
        )

        assertTrue(waitingForRequestedItem)
        assertTrue(activeLoad)
    }

    @Test
    fun loadingPlaceholderHiddenWhenTransitionSettledAndIdle() {
        val show = shouldShowReaderLoadingPlaceholder(
            waitingForRequestedItem = false,
            hasStalePayloadForCurrentItem = false,
            isLoading = false,
            transitionSettled = true,
        )

        assertEquals(false, show)
    }

    @Test
    fun skipInitialReopenWhenSameItemAlreadyActive() {
        val shouldSkip = shouldSkipInitialReopen(
            resolvedItemId = 42,
            currentItemId = 42,
            engineCurrentItemId = 42,
            autoPlayAfterLoad = false,
            isSpeaking = true,
            isAutoPlaying = false,
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun doNotSkipInitialReopenWhenDifferentItemRequested() {
        val shouldSkip = shouldSkipInitialReopen(
            resolvedItemId = 42,
            currentItemId = 41,
            engineCurrentItemId = 41,
            autoPlayAfterLoad = true,
            isSpeaking = true,
            isAutoPlaying = true,
        )

        assertEquals(false, shouldSkip)
    }

    @Test
    fun skipInitialReopenWhenSameItemPausedDuringSurfaceSwap() {
        val shouldSkip = shouldSkipInitialReopen(
            resolvedItemId = 42,
            currentItemId = 42,
            engineCurrentItemId = 42,
            autoPlayAfterLoad = false,
            isSpeaking = false,
            isAutoPlaying = false,
        )

        assertEquals(false, shouldSkip)
    }

    @Test
    fun doNotSkipInitialReopenWhenEngineNotAttachedYet() {
        val shouldSkip = shouldSkipInitialReopen(
            resolvedItemId = 42,
            currentItemId = 42,
            engineCurrentItemId = -1,
            autoPlayAfterLoad = false,
            isSpeaking = false,
            isAutoPlaying = false,
        )

        assertEquals(false, shouldSkip)
    }

    @Test
    fun preserveActivePlaybackDuringLoadWhenAlreadySpeaking() {
        val preserve = shouldPreserveActivePlaybackDuringLoad(
            autoPlayAfterLoad = false,
            isSpeaking = true,
            isAutoPlaying = false,
        )

        assertTrue(preserve)
    }

    @Test
    fun doNotPreserveActivePlaybackDuringLoadWhenPausedWithoutAutoplay() {
        val preserve = shouldPreserveActivePlaybackDuringLoad(
            autoPlayAfterLoad = false,
            isSpeaking = false,
            isAutoPlaying = false,
        )

        assertFalse(preserve)
    }

    @Test
    fun updateReaderScrollOffsetsAddsAndUpdatesPerItemOffset() {
        val initial = mapOf(100 to 24)
        val updated = updateReaderScrollOffsets(
            offsets = initial,
            itemId = 101,
            offset = 88,
        )
        val replaced = updateReaderScrollOffsets(
            offsets = updated,
            itemId = 100,
            offset = 12,
        )

        assertEquals(24, initial[100])
        assertEquals(88, updated[101])
        assertEquals(12, replaced[100])
    }

    @Test
    fun resetReaderScrollOffsetRemovesOnlyTargetItem() {
        val initial = mapOf(100 to 24, 101 to 88)
        val cleared = resetReaderScrollOffset(
            offsets = initial,
            itemId = 100,
        )

        assertEquals(false, cleared.containsKey(100))
        assertEquals(88, cleared[101])
    }

    @Test
    fun locusTabTapPreviewDefaultsToReaderPositionOnReturn() {
        val action = resolveLocusTabTapAction(
            previewModeActive = true,
            currentItemId = 101,
            returnToPlaybackPositionAfterPreview = false,
        )

        assertTrue(action.returnToNowPlayingItem)
        assertEquals(false, action.triggerScrollToPlaybackImmediately)
        assertEquals(false, action.triggerScrollToPlaybackAfterReturn)
    }

    @Test
    fun locusTabTapPreviewCanRequestPlaybackPositionOnReturn() {
        val action = resolveLocusTabTapAction(
            previewModeActive = true,
            currentItemId = 101,
            returnToPlaybackPositionAfterPreview = true,
        )

        assertTrue(action.returnToNowPlayingItem)
        assertEquals(false, action.triggerScrollToPlaybackImmediately)
        assertTrue(action.triggerScrollToPlaybackAfterReturn)
    }

    @Test
    fun locusTabTapOutsidePreviewJumpsToPlaybackPositionImmediately() {
        val action = resolveLocusTabTapAction(
            previewModeActive = false,
            currentItemId = 101,
            returnToPlaybackPositionAfterPreview = false,
        )

        assertEquals(false, action.returnToNowPlayingItem)
        assertTrue(action.triggerScrollToPlaybackImmediately)
        assertEquals(false, action.triggerScrollToPlaybackAfterReturn)
    }

    @Test
    fun locusPlaybackOwner_prefersEngineThenSessionThenFallback() {
        val fromEngine = resolveLocusPlaybackOwnerItemId(
            engineCurrentItemId = 88,
            sessionCurrentItemId = 77,
            fallbackItemId = 66,
        )
        val fromSession = resolveLocusPlaybackOwnerItemId(
            engineCurrentItemId = -1,
            sessionCurrentItemId = 77,
            fallbackItemId = 66,
        )
        val fromFallback = resolveLocusPlaybackOwnerItemId(
            engineCurrentItemId = -1,
            sessionCurrentItemId = null,
            fallbackItemId = 66,
        )

        assertEquals(88, fromEngine)
        assertEquals(77, fromSession)
        assertEquals(66, fromFallback)
    }

    @Test
    fun locusTitle_showsPlaybackOwnerDuringPreviewWhenOwnerExists() {
        val title = resolveLocusActionBarTitle(
            playbackActive = true,
            playbackOwnerItemId = 42,
            playbackOwnerTitle = "Now Playing A",
            playbackOwnerUrl = "",
            previewModeActive = true,
            previewTitle = "Preview B",
            fallbackItemId = 42,
        )

        assertEquals("Now Playing A", title)
    }

    @Test
    fun locusTitle_allowsPreviewOverrideWhenNoPlaybackOwner() {
        val title = resolveLocusActionBarTitle(
            playbackActive = false,
            playbackOwnerItemId = -1,
            playbackOwnerTitle = "",
            playbackOwnerUrl = "",
            previewModeActive = true,
            previewTitle = "Preview B",
            fallbackItemId = 99,
        )

        assertEquals("Preview B", title)
    }

    @Test
    fun locusTitle_allowsPreviewOverrideWhenPlaybackInactiveEvenIfOwnerExists() {
        val title = resolveLocusActionBarTitle(
            playbackActive = false,
            playbackOwnerItemId = 42,
            playbackOwnerTitle = "Now Playing A",
            playbackOwnerUrl = "",
            previewModeActive = true,
            previewTitle = "Preview B",
            fallbackItemId = 42,
        )

        assertEquals("Preview B", title)
    }

    @Test
    fun preservePlaybackOwnerForPreviewOpen_whenPlaybackActiveAndTargetDiffers() {
        val shouldPreserve = shouldPreservePlaybackOwnerForPreviewOpen(
            targetItemId = 20,
            currentItemId = 10,
            playbackActive = true,
        )
        assertTrue(shouldPreserve)
    }

    @Test
    fun preservePlaybackOwnerForPreviewOpen_falseWhenPlaybackInactiveOrSameItem() {
        val inactive = shouldPreservePlaybackOwnerForPreviewOpen(
            targetItemId = 20,
            currentItemId = 10,
            playbackActive = false,
        )
        val sameItem = shouldPreservePlaybackOwnerForPreviewOpen(
            targetItemId = 10,
            currentItemId = 10,
            playbackActive = true,
        )
        assertEquals(false, inactive)
        assertEquals(false, sameItem)
    }

    @Test
    fun requestedItemTransitionMode_previewOnlyWhenPlaybackActiveAndTargetDiffers() {
        val mode = resolveRequestedItemTransitionMode(
            targetItemId = 20,
            currentItemId = 10,
            playbackActive = true,
            hasLockedPlaybackOwner = false,
        )

        assertEquals(RequestedItemTransitionMode.PreviewOnly, mode)
    }

    @Test
    fun requestedItemTransitionMode_previewOnlyWhenLockedPlaybackOwner() {
        val mode = resolveRequestedItemTransitionMode(
            targetItemId = 20,
            currentItemId = 10,
            playbackActive = false,
            hasLockedPlaybackOwner = true,
        )

        assertEquals(RequestedItemTransitionMode.PreviewOnly, mode)
    }

    @Test
    fun requestedItemTransitionMode_replaceCurrentWhenPlaybackInactiveAndUnlocked() {
        val mode = resolveRequestedItemTransitionMode(
            targetItemId = 20,
            currentItemId = 10,
            playbackActive = false,
            hasLockedPlaybackOwner = false,
        )

        assertEquals(RequestedItemTransitionMode.ReplaceCurrent, mode)
    }

    @Test
    fun requestedItemTransitionMode_alreadyCurrentWhenInvalidOrSameTarget() {
        val invalid = resolveRequestedItemTransitionMode(
            targetItemId = -1,
            currentItemId = 10,
            playbackActive = true,
            hasLockedPlaybackOwner = true,
        )
        val same = resolveRequestedItemTransitionMode(
            targetItemId = 10,
            currentItemId = 10,
            playbackActive = true,
            hasLockedPlaybackOwner = false,
        )

        assertEquals(RequestedItemTransitionMode.AlreadyCurrent, invalid)
        assertEquals(RequestedItemTransitionMode.AlreadyCurrent, same)
        assertFalse(invalid == RequestedItemTransitionMode.ReplaceCurrent)
    }
}
