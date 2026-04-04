package com.mimeo.android.ui.reader

internal enum class ReaderScrollTriggerKind {
    NONE,
    STANDARD,
    FORCE_REATTACH,
    CENTER_IF_OFFSCREEN,
}

internal fun classifyReaderScrollTrigger(
    scrollTriggerSignal: Int,
    lastHandledScrollTrigger: Int,
): ReaderScrollTriggerKind {
    if (scrollTriggerSignal == lastHandledScrollTrigger) return ReaderScrollTriggerKind.NONE
    if (scrollTriggerSignal < 0) return ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN
    return if (scrollTriggerSignal % 2 != 0) {
        ReaderScrollTriggerKind.FORCE_REATTACH
    } else {
        ReaderScrollTriggerKind.STANDARD
    }
}

internal fun shouldKeepDetachedAfterTrigger(
    manualScrollDetached: Boolean,
    triggerKind: ReaderScrollTriggerKind,
): Boolean = manualScrollDetached && triggerKind != ReaderScrollTriggerKind.FORCE_REATTACH

internal fun nextManualDetachState(
    currentDetached: Boolean,
    triggerKind: ReaderScrollTriggerKind,
): Boolean = when (triggerKind) {
    ReaderScrollTriggerKind.FORCE_REATTACH -> false
    else -> currentDetached
}

internal fun shouldCenterForTrigger(
    triggerKind: ReaderScrollTriggerKind,
    anchorOffscreen: Boolean,
): Boolean = triggerKind == ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN && anchorOffscreen

internal fun shouldDetachOnManualScroll(
    manualScrollDetached: Boolean,
    anchorFullyVisible: Boolean,
): Boolean = !manualScrollDetached && !anchorFullyVisible

internal fun shouldAutoReattachAfterManualScroll(
    manualScrollDetached: Boolean,
    anchorFullyVisible: Boolean,
    triggerKind: ReaderScrollTriggerKind,
): Boolean {
    if (!manualScrollDetached) return false
    if (!anchorFullyVisible) return false
    // Keep FF/RW center-if-offscreen semantics independent from manual-scroll follow logic.
    if (triggerKind == ReaderScrollTriggerKind.CENTER_IF_OFFSCREEN) return false
    return true
}

internal fun shouldAutoScrollForStandardPlayback(
    triggerKind: ReaderScrollTriggerKind,
    autoScrollWhileListening: Boolean,
    manualScrollDetached: Boolean,
    hiddenByBottom: Boolean,
    nowMs: Long,
    suppressUntilMs: Long,
): Boolean {
    if (triggerKind != ReaderScrollTriggerKind.STANDARD) return false
    if (!autoScrollWhileListening || manualScrollDetached || !hiddenByBottom) return false
    return nowMs >= suppressUntilMs
}

internal fun shouldAutoScrollForPlaybackBoundary(
    autoScrollWhileListening: Boolean,
    manualScrollDetached: Boolean,
    anchorChanged: Boolean,
    hiddenByBottom: Boolean,
    nowMs: Long,
    suppressUntilMs: Long,
): Boolean {
    if (!autoScrollWhileListening || manualScrollDetached) return false
    if (!anchorChanged || !hiddenByBottom) return false
    return nowMs >= suppressUntilMs
}

internal fun shouldUseCenteredJumpAnchor(
    centerIfOffscreenTrigger: Boolean,
    standardFollowTrigger: Boolean,
    boundaryFollowTrigger: Boolean,
    forceReattach: Boolean,
): Boolean {
    if (!centerIfOffscreenTrigger) return false
    // FF/RW center-if-offscreen should win over boundary/top-follow in that same pass.
    // Standard/boundary flags can also evaluate true for the same frame when the anchor is
    // near/beyond bottom, but that must not downgrade a user-initiated FF/RW center action.
    if (forceReattach) return false
    return true
}

