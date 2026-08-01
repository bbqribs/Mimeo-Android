package com.mimeo.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.LivePlaybackSessionSync
import com.mimeo.android.ReaderPromoteRoute
import com.mimeo.android.classifyLivePlaybackSessionSync
import com.mimeo.android.classifyReaderPromoteRoute
import com.mimeo.android.engineCommittedToPlayback
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.PendingProgressSnapshot
import com.mimeo.android.resolveReaderPlaySessionOwner
import com.mimeo.android.shouldReplayCompletedItem
import com.mimeo.android.ui.player.PlaybackEngineState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Time source (injected so snapshot-marker tests are deterministic — §11.1)
// ---------------------------------------------------------------------------

fun interface DiagnosticsTimeSource {
    fun nowMillis(): Long
}

val SystemDiagnosticsTimeSource: DiagnosticsTimeSource = DiagnosticsTimeSource { System.currentTimeMillis() }

// ---------------------------------------------------------------------------
// Pure value types. None of these may declare an item-id field (§7.2.1).
// ---------------------------------------------------------------------------

enum class ProgressSourceField {
    API_FURTHEST_PERCENT,
    API_PROGRESS_PERCENT,
    RESUME_READ_PERCENT,
    LAST_READ_PERCENT,
    DEFAULT_ZERO,
}

enum class ItemRole {
    ACTIVE_ITEM,
    POINTER_ITEM,
    ENGINE_ITEM,
}

enum class PointerEngineRelation {
    AGREE,
    DIFFER,
    NO_ENGINE_ITEM,
}

enum class ReaderOffsetTier {
    DATA_STORE,
    SESSION,
}

sealed interface SessionPointerPosition {
    data class Position(val n: Int, val m: Int) : SessionPointerPosition
    data object Unavailable : SessionPointerPosition
}

data class CursorValue(val chunkIndex: Int, val offsetInChunkChars: Int)

sealed interface ReaderOffsetValue {
    data class Available(val offset: Int, val tier: ReaderOffsetTier) : ReaderOffsetValue
    data object Unavailable : ReaderOffsetValue
}

data class EngineCommitFlags(
    val autoPlayAfterLoad: Boolean,
    val isSpeaking: Boolean,
    val isAutoPlaying: Boolean,
    val hasStartedPlaybackForCurrentItem: Boolean,
)

/** Sections B + C for exactly one item (the active item, the pointer item, or the engine item). */
data class ItemProgressColumn(
    val role: ItemRole,
    val canonicalPercent: Int?,
    val canonicalSourceField: ProgressSourceField?,
    val canonicalCapApplied: Boolean,
    val furthestPercent: Int?,
    val furthestSourceField: ProgressSourceField?,
    val sessionCachedPercent: Int?,
    val doneVerdict: Boolean?,
    val manualStartIntentLabel: String?,
    val liveCursor: CursorValue?,
    val persistedCursor: CursorValue?,
    val persistedCursorObservedDelta: Int?,
    val sessionCursor: CursorValue?,
    val readerOffsetDurable: ReaderOffsetValue,
)

data class PendingProgressRow(
    val present: Boolean,
    val percent: Int?,
    val attemptCount: Int?,
    val snapshotAtMillis: Long,
)

data class FreshnessInfo(
    val secondsSinceLastQueueLoad: Long?,
    val secondsSinceLastSessionWrite: Long?,
    val queueOffline: Boolean,
    val progressSyncBadgeState: ProgressSyncBadgeState,
    val pendingProgress: PendingProgressRow,
)

data class DivergenceRow(val name: String, val lines: List<String>)
data class LagRow(val name: String, val lines: List<String>)

data class ProgressPointerDiagnosticsUiState(
    val hasActiveItem: Boolean,
    val pointerEngineRelation: PointerEngineRelation,
    val engineCommitFlags: EngineCommitFlags,
    val engineItemMembershipLabel: String?,
    val sessionPointerPosition: SessionPointerPosition,
    val readerPlaySessionOwnerVerdict: String,
    val livePlaybackSessionSyncVerdict: String,
    val activeColumn: ItemProgressColumn?,
    val pointerColumn: ItemProgressColumn?,
    val engineColumn: ItemProgressColumn?,
    val freshness: FreshnessInfo,
    val divergences: List<DivergenceRow>,
    val lagNotes: List<LagRow>,
)

// ---------------------------------------------------------------------------
// Pure builder. Ids are used only for lookup/comparison inside this function;
// none reach the returned state (§7.2.1, §11.1).
// ---------------------------------------------------------------------------

private fun rawPreCapProgress(item: PlaybackQueueItem): Int {
    val parsed = item.apiProgressPercent ?: item.resumeReadPercent ?: item.lastReadPercent ?: 0
    return parsed.coerceAtLeast(0)
}

private fun resolveCanonicalSource(item: PlaybackQueueItem): ProgressSourceField = when {
    item.apiProgressPercent != null -> ProgressSourceField.API_PROGRESS_PERCENT
    item.resumeReadPercent != null -> ProgressSourceField.RESUME_READ_PERCENT
    item.lastReadPercent != null -> ProgressSourceField.LAST_READ_PERCENT
    else -> ProgressSourceField.DEFAULT_ZERO
}

private fun resolveFurthestSource(item: PlaybackQueueItem): ProgressSourceField = when {
    item.apiFurthestPercent != null -> ProgressSourceField.API_FURTHEST_PERCENT
    item.lastReadPercent != null -> ProgressSourceField.LAST_READ_PERCENT
    item.apiProgressPercent != null -> ProgressSourceField.API_PROGRESS_PERCENT
    item.resumeReadPercent != null -> ProgressSourceField.RESUME_READ_PERCENT
    else -> ProgressSourceField.DEFAULT_ZERO
}

internal fun buildItemProgressColumn(
    itemId: Int,
    role: ItemRole,
    queueItems: List<PlaybackQueueItem>,
    liveCursorByItem: Map<Int, PlaybackPosition>,
    persistedCursorByItem: Map<Int, PlaybackPosition>,
    session: NowPlayingSession?,
    getReaderScrollOffset: (Int) -> Int?,
    persistedReaderScrollOffset: (Int) -> Int?,
): ItemProgressColumn {
    val queueItem = queueItems.firstOrNull { it.itemId == itemId }
    val sessionItem = session?.items?.firstOrNull { it.itemId == itemId }
        ?: session?.historyItems?.firstOrNull { it.itemId == itemId }

    val canonicalPercent = queueItem?.progressPercent
    val canonicalSource = queueItem?.let(::resolveCanonicalSource)
    val canonicalCapApplied = queueItem?.let { rawPreCapProgress(it) > it.furthestPercent } ?: false
    val furthestPercent = queueItem?.furthestPercent
    val furthestSource = queueItem?.let(::resolveFurthestSource)
    val sessionCachedPercent = sessionItem?.lastReadPercent
    val doneVerdict = furthestPercent?.let { shouldReplayCompletedItem(it) }
    val manualStartIntentLabel = doneVerdict?.let { if (it) "Replay" else "ManualOpen" }

    val liveCursor = liveCursorByItem[itemId]?.let { CursorValue(it.chunkIndex, it.offsetInChunkChars) }
    val persistedCursor = persistedCursorByItem[itemId]?.let { CursorValue(it.chunkIndex, it.offsetInChunkChars) }
    val observedDelta = if (liveCursor != null && persistedCursor != null) {
        kotlin.math.abs(liveCursor.offsetInChunkChars - persistedCursor.offsetInChunkChars)
    } else {
        null
    }
    val sessionCursor = sessionItem?.let { CursorValue(it.chunkIndex, it.offsetInChunkChars) }

    val durableOffset = getReaderScrollOffset(itemId)
    val readerOffsetDurable = when {
        durableOffset == null -> ReaderOffsetValue.Unavailable
        persistedReaderScrollOffset(itemId) != null -> ReaderOffsetValue.Available(durableOffset, ReaderOffsetTier.DATA_STORE)
        else -> ReaderOffsetValue.Available(durableOffset, ReaderOffsetTier.SESSION)
    }

    return ItemProgressColumn(
        role = role,
        canonicalPercent = canonicalPercent,
        canonicalSourceField = canonicalSource,
        canonicalCapApplied = canonicalCapApplied,
        furthestPercent = furthestPercent,
        furthestSourceField = furthestSource,
        sessionCachedPercent = sessionCachedPercent,
        doneVerdict = doneVerdict,
        manualStartIntentLabel = manualStartIntentLabel,
        liveCursor = liveCursor,
        persistedCursor = persistedCursor,
        persistedCursorObservedDelta = observedDelta,
        sessionCursor = sessionCursor,
        readerOffsetDurable = readerOffsetDurable,
    )
}

private fun membershipLabel(route: ReaderPromoteRoute): String? = when (route) {
    ReaderPromoteRoute.SessionItem -> "in session"
    ReaderPromoteRoute.HistoryItem -> "in history"
    ReaderPromoteRoute.ExternalItem -> "outside session"
    ReaderPromoteRoute.None -> null
}

private fun sessionPointerPosition(session: NowPlayingSession?): SessionPointerPosition {
    if (session == null) return SessionPointerPosition.Unavailable
    val index = session.currentIndex
    return if (index in session.items.indices) {
        SessionPointerPosition.Position(n = index + 1, m = session.items.size)
    } else {
        SessionPointerPosition.Unavailable
    }
}

/**
 * Detects genuine pointer/engine disagreement (Band 1). The ids are the comparison input only —
 * the boolean result (and the caller's own membership lookup) is all that reaches display (§6.4).
 */
internal fun detectPointerVsEngineDivergence(pointerItemId: Int?, engineItemId: Int?): Boolean {
    if (engineItemId == null || engineItemId <= 0) return false
    return pointerItemId != engineItemId
}

/**
 * `canonical-vs-session`: fires only when the session cache claims *more* progress than the
 * high-water mark. Deliberately not `R1 != R5` — see §6.4 for the false-positive this narrowing avoids.
 */
internal fun detectCanonicalVsSessionDivergence(sessionCachedPercent: Int?, furthestPercent: Int?): Boolean {
    if (sessionCachedPercent == null || furthestPercent == null) return false
    return sessionCachedPercent > furthestPercent
}

/** `canonical-vs-furthest`: fires when the pre-cap raw value exceeds the furthest ceiling, i.e. the cap fired. */
internal fun detectCanonicalVsFurthestDivergence(item: PlaybackQueueItem): Boolean {
    return rawPreCapProgress(item) > item.furthestPercent
}

/**
 * `live-vs-persisted-cursor` (Band 2, expected lag): only evaluated when both tiers are present —
 * an absent tier is a missing value, not a divergence, and must stay silent (§6.5, never invented).
 */
internal fun detectLiveVsPersistedCursorLag(live: CursorValue?, persisted: CursorValue?): Boolean {
    if (live == null || persisted == null) return false
    return live.chunkIndex != persisted.chunkIndex || live.offsetInChunkChars != persisted.offsetInChunkChars
}

/**
 * `reader-lag` (Band 2): comparing the durable reader offset against "the value it would take at
 * the next persist threshold" requires observing the live viewport. §11.2 forbids hoisting
 * `PlayerScreen`'s live `readerScrollOffsets` state to obtain it, so this bounded implementation has
 * no live signal to compare against and the detector is structurally silent. Kept as a named
 * function (matching the two-lag-detector shape §6.4 requires) rather than removed, so a future
 * change that legitimately obtains a live signal has a single place to wire it in — under its own
 * §12 amendment, since §11.2 is a hard boundary this ticket does not reopen.
 */
internal fun detectReaderLag(): Boolean = false

private fun buildDivergences(
    relation: PointerEngineRelation,
    pointerPosition: SessionPointerPosition,
    engineMembershipLabel: String?,
    livePlaybackSessionSyncVerdict: LivePlaybackSessionSync,
    pointerColumn: ItemProgressColumn?,
    engineColumn: ItemProgressColumn?,
    activeColumn: ItemProgressColumn?,
    activeQueueItem: PlaybackQueueItem?,
    pointerQueueItem: PlaybackQueueItem?,
    engineQueueItem: PlaybackQueueItem?,
): List<DivergenceRow> {
    val rows = mutableListOf<DivergenceRow>()

    if (relation == PointerEngineRelation.DIFFER) {
        val pointerLine = when (pointerPosition) {
            is SessionPointerPosition.Position -> "pointer item = session position ${pointerPosition.n} of ${pointerPosition.m}"
            SessionPointerPosition.Unavailable -> "pointer item = unavailable"
        }
        val toleranceNote = when {
            livePlaybackSessionSyncVerdict != LivePlaybackSessionSync.None ->
                "resolving: ${livePlaybackSessionSyncVerdict.name}"
            engineMembershipLabel == "outside session" -> "tolerated steady state: outside session"
            else -> "transient: load-play handoff"
        }
        val engineLine = "engine item = ${engineMembershipLabel ?: "unavailable"}   ($toleranceNote)"
        rows += DivergenceRow("pointer-vs-engine", listOf(pointerLine, engineLine))
    }

    fun canonicalVsSessionRow(label: String, column: ItemProgressColumn?): DivergenceRow? {
        if (column == null) return null
        if (!detectCanonicalVsSessionDivergence(column.sessionCachedPercent, column.furthestPercent)) return null
        return DivergenceRow(
            "canonical-vs-session",
            listOf(
                "$label furthest percent = ${column.furthestPercent}",
                "$label session-cached percent = ${column.sessionCachedPercent}",
            ),
        )
    }

    fun canonicalVsFurthestRow(label: String, queueItem: PlaybackQueueItem?): DivergenceRow? {
        if (queueItem == null || !detectCanonicalVsFurthestDivergence(queueItem)) return null
        return DivergenceRow(
            "canonical-vs-furthest",
            listOf(
                "$label canonical percent (pre-cap) = ${rawPreCapProgress(queueItem)}",
                "$label furthest percent = ${queueItem.furthestPercent}",
            ),
        )
    }

    if (relation == PointerEngineRelation.DIFFER) {
        canonicalVsSessionRow("pointer item", pointerColumn)?.let(rows::add)
        canonicalVsSessionRow("engine item", engineColumn)?.let(rows::add)
        canonicalVsFurthestRow("pointer item", pointerQueueItem)?.let(rows::add)
        canonicalVsFurthestRow("engine item", engineQueueItem)?.let(rows::add)
    } else {
        canonicalVsSessionRow("active item", activeColumn)?.let(rows::add)
        canonicalVsFurthestRow("active item", activeQueueItem)?.let(rows::add)
    }

    return rows
}

private fun buildLagNotes(
    relation: PointerEngineRelation,
    pointerColumn: ItemProgressColumn?,
    engineColumn: ItemProgressColumn?,
    activeColumn: ItemProgressColumn?,
): List<LagRow> {
    val rows = mutableListOf<LagRow>()

    fun liveVsPersistedRow(label: String, column: ItemProgressColumn?): LagRow? {
        if (column == null) return null
        if (!detectLiveVsPersistedCursorLag(column.liveCursor, column.persistedCursor)) return null
        val delta = column.persistedCursorObservedDelta
        return LagRow(
            "live-vs-persisted-cursor",
            listOf(
                "$label live cursor = chunk ${column.liveCursor?.chunkIndex} offset ${column.liveCursor?.offsetInChunkChars}",
                "$label persisted cursor = chunk ${column.persistedCursor?.chunkIndex} offset ${column.persistedCursor?.offsetInChunkChars} (lag; observed delta=${delta ?: "unavailable"} chars)",
            ),
        )
    }

    if (relation == PointerEngineRelation.DIFFER) {
        liveVsPersistedRow("pointer item", pointerColumn)?.let(rows::add)
        liveVsPersistedRow("engine item", engineColumn)?.let(rows::add)
    } else {
        liveVsPersistedRow("active item", activeColumn)?.let(rows::add)
    }

    if (detectReaderLag()) {
        rows += LagRow("reader-lag", listOf("reader offset lag detected"))
    }

    return rows
}

fun buildProgressPointerDiagnosticsUiState(
    queueItems: List<PlaybackQueueItem>,
    session: NowPlayingSession?,
    engineState: PlaybackEngineState,
    liveCursorByItem: Map<Int, PlaybackPosition>,
    persistedCursorByItem: Map<Int, PlaybackPosition>,
    getReaderScrollOffset: (Int) -> Int?,
    persistedReaderScrollOffset: (Int) -> Int?,
    pendingProgress: PendingProgressSnapshot?,
    lastQueueLoadCompletedAtMs: Long,
    queueOffline: Boolean,
    progressSyncBadgeState: ProgressSyncBadgeState,
    timeSource: DiagnosticsTimeSource = SystemDiagnosticsTimeSource,
): ProgressPointerDiagnosticsUiState {
    val now = timeSource.nowMillis()

    val pointerItemId = session?.currentItem?.itemId?.takeIf { it > 0 }
    val engineItemId = engineState.currentItemId.takeIf { it > 0 }

    val committedToPlayback = engineCommittedToPlayback(
        autoPlayAfterLoad = engineState.autoPlayAfterLoad,
        isSpeaking = engineState.isSpeaking,
        isAutoPlaying = engineState.isAutoPlaying,
        hasStartedPlaybackForCurrentItem = engineState.hasStartedPlaybackForCurrentItem,
    )
    val commitFlags = EngineCommitFlags(
        autoPlayAfterLoad = engineState.autoPlayAfterLoad,
        isSpeaking = engineState.isSpeaking,
        isAutoPlaying = engineState.isAutoPlaying,
        hasStartedPlaybackForCurrentItem = engineState.hasStartedPlaybackForCurrentItem,
    )

    val inSessionItems = engineItemId != null && session?.items?.any { it.itemId == engineItemId } == true
    val inHistory = engineItemId != null && session?.historyItems?.any { it.itemId == engineItemId } == true
    val route = classifyReaderPromoteRoute(engineItemId ?: 0, inSessionItems, inHistory)
    val readerPlaySessionOwnerVerdict = resolveReaderPlaySessionOwner(route, hasSession = session != null)
    val livePlaybackSessionSyncVerdict = classifyLivePlaybackSessionSync(
        engineItemId = engineItemId ?: 0,
        committedToPlayback = committedToPlayback,
        sessionCurrentItemId = pointerItemId,
        inSessionItems = inSessionItems,
        inHistory = inHistory,
    )
    val engineMembershipLabel = if (engineItemId != null) membershipLabel(route) else null

    val relation = when {
        engineItemId == null -> PointerEngineRelation.NO_ENGINE_ITEM
        pointerItemId == engineItemId -> PointerEngineRelation.AGREE
        else -> PointerEngineRelation.DIFFER
    }

    val hasActiveItem = pointerItemId != null || engineItemId != null

    fun column(id: Int?, role: ItemRole): ItemProgressColumn? {
        if (id == null) return null
        return buildItemProgressColumn(
            itemId = id,
            role = role,
            queueItems = queueItems,
            liveCursorByItem = liveCursorByItem,
            persistedCursorByItem = persistedCursorByItem,
            session = session,
            getReaderScrollOffset = getReaderScrollOffset,
            persistedReaderScrollOffset = persistedReaderScrollOffset,
        )
    }

    val activeColumn: ItemProgressColumn?
    val pointerColumn: ItemProgressColumn?
    val engineColumn: ItemProgressColumn?
    when (relation) {
        PointerEngineRelation.DIFFER -> {
            activeColumn = null
            pointerColumn = column(pointerItemId, ItemRole.POINTER_ITEM)
            engineColumn = column(engineItemId, ItemRole.ENGINE_ITEM)
        }
        PointerEngineRelation.AGREE, PointerEngineRelation.NO_ENGINE_ITEM -> {
            activeColumn = column(pointerItemId ?: engineItemId, ItemRole.ACTIVE_ITEM)
            pointerColumn = null
            engineColumn = null
        }
    }

    val activeQueueItem = (pointerItemId ?: engineItemId)?.let { id -> queueItems.firstOrNull { it.itemId == id } }
    val pointerQueueItem = pointerItemId?.let { id -> queueItems.firstOrNull { it.itemId == id } }
    val engineQueueItem = engineItemId?.let { id -> queueItems.firstOrNull { it.itemId == id } }

    val secondsSinceQueueLoad = if (lastQueueLoadCompletedAtMs > 0L) (now - lastQueueLoadCompletedAtMs) / 1000L else null
    val secondsSinceSessionWrite = session?.updatedAt?.let { (now - it) / 1000L }

    val pendingRow = PendingProgressRow(
        present = pendingProgress != null,
        percent = pendingProgress?.percent,
        attemptCount = pendingProgress?.attemptCount,
        snapshotAtMillis = now,
    )

    val freshness = FreshnessInfo(
        secondsSinceLastQueueLoad = secondsSinceQueueLoad,
        secondsSinceLastSessionWrite = secondsSinceSessionWrite,
        queueOffline = queueOffline,
        progressSyncBadgeState = progressSyncBadgeState,
        pendingProgress = pendingRow,
    )

    val divergences = buildDivergences(
        relation = relation,
        pointerPosition = sessionPointerPosition(session),
        engineMembershipLabel = engineMembershipLabel,
        livePlaybackSessionSyncVerdict = livePlaybackSessionSyncVerdict,
        pointerColumn = pointerColumn,
        engineColumn = engineColumn,
        activeColumn = activeColumn,
        activeQueueItem = activeQueueItem,
        pointerQueueItem = pointerQueueItem,
        engineQueueItem = engineQueueItem,
    )

    val lagNotes = buildLagNotes(
        relation = relation,
        pointerColumn = pointerColumn,
        engineColumn = engineColumn,
        activeColumn = activeColumn,
    )

    return ProgressPointerDiagnosticsUiState(
        hasActiveItem = hasActiveItem,
        pointerEngineRelation = relation,
        engineCommitFlags = commitFlags,
        engineItemMembershipLabel = engineMembershipLabel,
        sessionPointerPosition = sessionPointerPosition(session),
        readerPlaySessionOwnerVerdict = readerPlaySessionOwnerVerdict.name,
        livePlaybackSessionSyncVerdict = livePlaybackSessionSyncVerdict.name,
        activeColumn = activeColumn,
        pointerColumn = pointerColumn,
        engineColumn = engineColumn,
        freshness = freshness,
        divergences = divergences,
        lagNotes = lagNotes,
    )
}

// ---------------------------------------------------------------------------
// Pure renderer. Mirrors the shape of playbackObservabilityLines (PlayerScreen.kt)
// but never renders an item id (§6.3.1, §7.2.1).
// ---------------------------------------------------------------------------

private fun formatProgressSource(field: ProgressSourceField?): String = when (field) {
    null -> "unavailable"
    ProgressSourceField.API_FURTHEST_PERCENT -> "furthest_percent"
    ProgressSourceField.API_PROGRESS_PERCENT -> "progress_percent"
    ProgressSourceField.RESUME_READ_PERCENT -> "resume_read_percent"
    ProgressSourceField.LAST_READ_PERCENT -> "last_read_percent"
    ProgressSourceField.DEFAULT_ZERO -> "default"
}

private fun formatCursor(cursor: CursorValue?): String =
    cursor?.let { "chunk ${it.chunkIndex} offset ${it.offsetInChunkChars}" } ?: "unavailable"

private fun formatReaderOffset(value: ReaderOffsetValue): String = when (value) {
    is ReaderOffsetValue.Available -> "${value.offset} (${if (value.tier == ReaderOffsetTier.DATA_STORE) "DataStore" else "session"})"
    ReaderOffsetValue.Unavailable -> "unavailable"
}

private fun columnLabel(column: ItemProgressColumn): String = when (column.role) {
    ItemRole.ACTIVE_ITEM -> "active item"
    ItemRole.POINTER_ITEM -> "pointer item"
    ItemRole.ENGINE_ITEM -> "engine item"
}

internal fun renderSectionA(state: ProgressPointerDiagnosticsUiState): List<String> {
    val relationLabel = when (state.pointerEngineRelation) {
        PointerEngineRelation.AGREE -> "agree"
        PointerEngineRelation.DIFFER -> "differ"
        PointerEngineRelation.NO_ENGINE_ITEM -> "no engine item"
    }
    val positionLabel = when (val position = state.sessionPointerPosition) {
        is SessionPointerPosition.Position -> "${position.n} of ${position.m}"
        SessionPointerPosition.Unavailable -> "unavailable"
    }
    val flags = state.engineCommitFlags
    return listOf(
        "pointer/engine: $relationLabel",
        "engine commitment: autoPlayAfterLoad=${flags.autoPlayAfterLoad} isSpeaking=${flags.isSpeaking} isAutoPlaying=${flags.isAutoPlaying} hasStartedPlaybackForCurrentItem=${flags.hasStartedPlaybackForCurrentItem}",
        "engine item membership: ${state.engineItemMembershipLabel ?: "unavailable"} (in-history is process-local)",
        "session pointer position: $positionLabel",
        "resolveReaderPlaySessionOwner: ${state.readerPlaySessionOwnerVerdict}",
        "classifyLivePlaybackSessionSync: ${state.livePlaybackSessionSyncVerdict}",
    )
}

private fun renderColumnSectionB(column: ItemProgressColumn): List<String> {
    val label = columnLabel(column)
    return listOf(
        "$label canonical percent: ${column.canonicalPercent ?: "unavailable"} (${formatProgressSource(column.canonicalSourceField)}, cap applied=${column.canonicalCapApplied})",
        "$label furthest percent: ${column.furthestPercent ?: "unavailable"} (${formatProgressSource(column.furthestSourceField)})",
        "$label session-cached percent: ${column.sessionCachedPercent ?: "unavailable"}",
        "$label done verdict: ${column.doneVerdict ?: "unavailable"} manual-start intent=${column.manualStartIntentLabel ?: "unavailable"}",
    )
}

private fun renderColumnSectionC(column: ItemProgressColumn): List<String> {
    val label = columnLabel(column)
    return listOf(
        "$label live cursor: ${formatCursor(column.liveCursor)}",
        "$label persisted-segment cursor: ${formatCursor(column.persistedCursor)} (lag marker; may lag live)",
        "$label session-cached cursor: ${formatCursor(column.sessionCursor)}",
        "$label durable reader offset: ${formatReaderOffset(column.readerOffsetDurable)} (lag marker; may lag live viewport)",
        "$label live reader offset: unavailable (reader-local, not observable outside Locus)",
    )
}

internal fun renderSectionB(state: ProgressPointerDiagnosticsUiState): List<String> {
    state.activeColumn?.let { return renderColumnSectionB(it) }
    val rows = mutableListOf<String>()
    state.pointerColumn?.let { rows += renderColumnSectionB(it) }
    state.engineColumn?.let { rows += renderColumnSectionB(it) }
    return rows
}

internal fun renderSectionC(state: ProgressPointerDiagnosticsUiState): List<String> {
    state.activeColumn?.let { return renderColumnSectionC(it) }
    val rows = mutableListOf<String>()
    state.pointerColumn?.let { rows += renderColumnSectionC(it) }
    state.engineColumn?.let { rows += renderColumnSectionC(it) }
    return rows
}

internal fun renderSectionD(state: ProgressPointerDiagnosticsUiState): List<String> {
    val freshness = state.freshness
    val snapshotMarker = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(freshness.pendingProgress.snapshotAtMillis))
    val pending = freshness.pendingProgress
    return listOf(
        "seconds since last queue load: ${freshness.secondsSinceLastQueueLoad ?: "unavailable"}",
        "seconds since last session write: ${freshness.secondsSinceLastSessionWrite ?: "unavailable"}",
        "last successful progress post: unavailable",
        "queue offline: ${freshness.queueOffline}",
        "progress sync badge: ${freshness.progressSyncBadgeState.name}",
        "pending progress present: ${pending.present} percent=${pending.percent ?: "unavailable"} attempts=${pending.attemptCount ?: "unavailable"} (snapshot $snapshotMarker)",
        "Queued progress carries percent only; chunk, offset and reader scroll are not queued and are not resent.",
    )
}

internal fun renderDivergenceRows(state: ProgressPointerDiagnosticsUiState): List<String> {
    if (state.divergences.isEmpty()) return emptyList()
    val rows = mutableListOf("Divergences")
    state.divergences.forEach { divergence ->
        rows += "DIVERGENCE  ${divergence.name}"
        divergence.lines.forEach { rows += "  $it" }
    }
    return rows
}

internal fun renderLagRows(state: ProgressPointerDiagnosticsUiState): List<String> {
    if (state.lagNotes.isEmpty()) return emptyList()
    val rows = mutableListOf("Expected lag")
    state.lagNotes.forEach { lag ->
        rows += "LAG  ${lag.name}"
        lag.lines.forEach { rows += "  $it" }
    }
    return rows
}

// ---------------------------------------------------------------------------
// Screen. Strictly observational — reads only, no lambda taking a writable action.
// ---------------------------------------------------------------------------

@Composable
fun ProgressPointerDiagnosticsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    if (!BuildConfig.DEBUG || !settings.showProgressPointerDiagnostics) {
        return
    }

    val queueItems by vm.queueItems.collectAsState()
    val session by vm.nowPlayingSession.collectAsState()
    val engineState by vm.playbackEngineState.collectAsState()
    val liveCursorByItem by vm.playbackPositionByItem.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val progressSyncBadgeState by vm.progressSyncBadgeState.collectAsState()

    val activeItemId = session?.currentItem?.itemId?.takeIf { it > 0 } ?: engineState.currentItemId.takeIf { it > 0 }
    var pendingProgress by remember { mutableStateOf<PendingProgressSnapshot?>(null) }
    LaunchedEffect(activeItemId) {
        pendingProgress = activeItemId?.let { vm.pendingProgressForItem(it) }
    }

    val uiState = buildProgressPointerDiagnosticsUiState(
        queueItems = queueItems,
        session = session,
        engineState = engineState,
        liveCursorByItem = liveCursorByItem,
        persistedCursorByItem = queueItems.mapNotNull { item ->
            vm.persistedPlaybackPosition(item.itemId)?.let { item.itemId to it }
        }.toMap(),
        getReaderScrollOffset = { id -> vm.getReaderScrollOffset(id) },
        persistedReaderScrollOffset = { id -> vm.persistedReaderScrollOffset(id) },
        pendingProgress = pendingProgress,
        lastQueueLoadCompletedAtMs = vm.lastQueueLoadCompletedAtMillis(),
        queueOffline = queueOffline,
        progressSyncBadgeState = progressSyncBadgeState,
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Progress & pointer state", style = MaterialTheme.typography.titleMedium)
        if (!uiState.hasActiveItem) {
            Text("No active item.")
            return@Column
        }
        Text("A. Relationship & ownership", style = MaterialTheme.typography.titleSmall)
        renderSectionA(uiState).forEach { Text(it) }
        Text("B. Progress", style = MaterialTheme.typography.titleSmall)
        renderSectionB(uiState).forEach { Text(it) }
        Text("C. Position", style = MaterialTheme.typography.titleSmall)
        renderSectionC(uiState).forEach { Text(it) }
        Text("D. Freshness & sync", style = MaterialTheme.typography.titleSmall)
        renderSectionD(uiState).forEach { Text(it) }
        renderDivergenceRows(uiState).forEach { Text(it) }
        renderLagRows(uiState).forEach { Text(it) }
    }
}
