package com.mimeo.android

/**
 * Pure helpers that encode playback engine vs. Now Playing session precedence.
 *
 * The engine's [PlaybackEngineState.currentItemId] takes priority over the session's
 * current item whenever the engine actually owns an item (id > 0). When the engine is
 * absent (id <= 0) the session's current item, if any, owns playback. Keeping this in a
 * pure helper lets archive/bin cleanup decisions be pinned by tests without constructing
 * the full view model.
 */
internal fun resolvePlaybackOwnerItemId(
    engineCurrentItemId: Int,
    sessionCurrentItemId: Int?,
): Int? = if (engineCurrentItemId > 0) engineCurrentItemId else sessionCurrentItemId

/**
 * Decision describing how an archive/bin action should treat the active playback session.
 *
 * [affectsCurrentOwner] is true when the item being archived/binned is the current
 * playback owner (per [resolvePlaybackOwnerItemId]). [deferCleanup] is true when the
 * owner is still actively playing and session teardown must be postponed so playback can
 * finish. [clearSessionNow] is the immediate pause + clear signal.
 */
internal data class PlaybackCleanupDecision(
    val affectsCurrentOwner: Boolean,
    val deferCleanup: Boolean,
) {
    val clearSessionNow: Boolean
        get() = affectsCurrentOwner && !deferCleanup
}

/**
 * Cleanup decision for archiving [itemId]. Archiving the actively-playing owner defers
 * cleanup so playback continues; otherwise the session is cleared immediately.
 */
internal fun archivePlaybackCleanupDecision(
    engineCurrentItemId: Int,
    sessionCurrentItemId: Int?,
    itemId: Int,
    isItemActivelyPlaying: Boolean,
): PlaybackCleanupDecision {
    val affects = resolvePlaybackOwnerItemId(engineCurrentItemId, sessionCurrentItemId) == itemId
    return PlaybackCleanupDecision(
        affectsCurrentOwner = affects,
        deferCleanup = affects && isItemActivelyPlaying,
    )
}

/**
 * Cleanup decision for binning [itemId]. Binning never defers cleanup, so the session is
 * cleared immediately whenever the binned item is the current playback owner.
 */
internal fun binPlaybackCleanupDecision(
    engineCurrentItemId: Int,
    sessionCurrentItemId: Int?,
    itemId: Int,
): PlaybackCleanupDecision {
    val affects = resolvePlaybackOwnerItemId(engineCurrentItemId, sessionCurrentItemId) == itemId
    return PlaybackCleanupDecision(
        affectsCurrentOwner = affects,
        deferCleanup = false,
    )
}
