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

