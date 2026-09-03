package com.mimeo.android.repository

import com.mimeo.android.model.UpNextSession
import kotlinx.serialization.Serializable

internal enum class UpNextCapability {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

internal enum class UpNextSemanticMoveCapability {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

internal enum class PendingUpNextMovePhase {
    QUEUED,
    IN_FLIGHT,
    AMBIGUOUS,
}

@Serializable
internal data class PendingUpNextSemanticMove(
    val sessionId: Long,
    val expectedStructureVersion: Long,
    val itemId: Int,
    val originalPosition: Int,
    val toPosition: Int,
    val phase: PendingUpNextMovePhase = PendingUpNextMovePhase.QUEUED,
)

@Serializable
internal data class UpNextMoveDiagnostic(
    val outcome: String,
    val code: String? = null,
    val domain: String? = null,
    val expectedVersion: Long? = null,
    val actualVersion: Long? = null,
    val correlationId: String? = null,
)

internal sealed interface StageUpNextSemanticMoveResult {
    data class Staged(
        val intent: PendingUpNextSemanticMove,
        val session: NowPlayingSession,
    ) : StageUpNextSemanticMoveResult

    data object PendingIntentExists : StageUpNextSemanticMoveResult
    data object MissingServerContext : StageUpNextSemanticMoveResult
    data object Unsupported : StageUpNextSemanticMoveResult
    data object DirtyLegacySnapshot : StageUpNextSemanticMoveResult
    data object InvalidTarget : StageUpNextSemanticMoveResult
}

@Serializable
internal data class PendingUpNextPointerTransition(
    val sessionId: Long,
    val expectedPointerVersion: Long,
    val fromItemId: Int,
    val toItemId: Int?,
)

internal data class LocalUpNextSnapshot(
    val itemIds: List<Int>,
    val currentItemId: Int?,
    val seedSourceKind: String,
    val seedSourceLabel: String,
) {
    init {
        require(itemIds.distinct().size == itemIds.size)
        require(currentItemId == null || currentItemId in itemIds)
    }
}

internal enum class LegacyDirtySnapshotClassification {
    NON_REORDER,
    ORDER_ONLY_REORDER,
    AMBIGUOUS_MIXED_REORDER,
}

/**
 * Classifies pre-semantic dirty snapshots without inventing a move. A changed relative order
 * among shared members is the only reorder signal; membership/current/provenance changes on
 * their own remain existing whole-session mutations.
 */
internal fun classifyLegacyDirtySnapshot(
    local: LocalUpNextSnapshot?,
    server: UpNextSession?,
): LegacyDirtySnapshotClassification {
    if (local == null || server == null) return LegacyDirtySnapshotClassification.NON_REORDER
    val serverIds = server.items.sortedBy { it.position }.map { it.itemId }
    val localSet = local.itemIds.toSet()
    val serverSet = serverIds.toSet()
    val sameMembership = local.itemIds.size == serverIds.size && localSet == serverSet
    val localSharedOrder = local.itemIds.filter { it in serverSet }
    val serverSharedOrder = serverIds.filter { it in localSet }
    val sharedOrderChanged = localSharedOrder != serverSharedOrder
    val samePointerAndProvenance =
        local.currentItemId == server.currentItemId &&
            local.seedSourceKind == server.seedSourceKind &&
            local.seedSourceLabel == server.seedSourceLabel
    return when {
        sameMembership && sharedOrderChanged && samePointerAndProvenance ->
            LegacyDirtySnapshotClassification.ORDER_ONLY_REORDER
        sharedOrderChanged -> LegacyDirtySnapshotClassification.AMBIGUOUS_MIXED_REORDER
        else -> LegacyDirtySnapshotClassification.NON_REORDER
    }
}

internal sealed interface UpNextSyncPlan {
    data object MarkCleanAbsent : UpNextSyncPlan
    data class Adopt(val session: UpNextSession?) : UpNextSyncPlan
    data class Replace(val snapshot: LocalUpNextSnapshot, val expectedVersion: Long?) : UpNextSyncPlan
    data class Clear(val expectedVersion: Long) : UpNextSyncPlan
}

/** Ratified first-adoption rule: an existing server projection always wins. */
internal fun planFirstUpNextAdoption(
    serverSession: UpNextSession?,
    localSnapshot: LocalUpNextSnapshot?,
): UpNextSyncPlan = when {
    serverSession != null -> UpNextSyncPlan.Adopt(serverSession)
    localSnapshot != null -> UpNextSyncPlan.Replace(localSnapshot, expectedVersion = null)
    else -> UpNextSyncPlan.MarkCleanAbsent
}

/** Ratified reconnect rule: only a dirty snapshot based on an observed version is replayed. */
internal fun planUpNextReconnect(
    dirty: Boolean,
    observedVersion: Long?,
    localSnapshot: LocalUpNextSnapshot?,
    refreshedServerSession: UpNextSession? = null,
): UpNextSyncPlan {
    if (!dirty) return UpNextSyncPlan.Adopt(refreshedServerSession)
    if (localSnapshot != null) return UpNextSyncPlan.Replace(localSnapshot, observedVersion)
    return observedVersion?.let(UpNextSyncPlan::Clear) ?: UpNextSyncPlan.MarkCleanAbsent
}
