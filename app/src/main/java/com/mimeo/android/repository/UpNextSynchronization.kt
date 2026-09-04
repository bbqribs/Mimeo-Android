package com.mimeo.android.repository

import com.mimeo.android.data.ApiClient
import com.mimeo.android.model.UpNextSession
import com.mimeo.android.model.UpNextMoveRequest
import kotlinx.coroutines.CancellationException
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

internal sealed interface PendingUpNextMovePublicationResult {
    data object None : PendingUpNextMovePublicationResult
    data object RequiresReconciliation : PendingUpNextMovePublicationResult
    data object Blocked : PendingUpNextMovePublicationResult
    data class PreflightFailed(val error: Throwable) : PendingUpNextMovePublicationResult
    data class Acknowledged(val session: UpNextSession) : PendingUpNextMovePublicationResult
    data class PostFailed(
        val inFlight: PendingUpNextSemanticMove,
        val error: Throwable,
    ) : PendingUpNextMovePublicationResult
}

/**
 * Publishes one queued move only after the exact authenticated Up Next route answers a read.
 * The preflight projection is deliberately discarded: it proves route usability but cannot
 * replace local order or alter the move's original structure-version precondition.
 */
internal class PendingUpNextMovePublisher(
    private val repository: PlaybackRepository,
    private val apiClient: ApiClient,
) {
    suspend fun publish(
        baseUrl: String,
        token: String,
        requestStillCurrent: suspend () -> Boolean,
        onPostStarting: suspend () -> Unit = {},
    ): PendingUpNextMovePublicationResult {
        val pending = repository.pendingUpNextSemanticMove()
            ?: return PendingUpNextMovePublicationResult.None
        if (pending.phase != PendingUpNextMovePhase.QUEUED) {
            return PendingUpNextMovePublicationResult.RequiresReconciliation
        }

        try {
            apiClient.getUpNextSession(baseUrl, token)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return PendingUpNextMovePublicationResult.PreflightFailed(error)
        }
        if (!requestStillCurrent()) return PendingUpNextMovePublicationResult.Blocked

        val inFlight = repository.updatePendingUpNextSemanticMovePhase(
            expected = pending,
            phase = PendingUpNextMovePhase.IN_FLIGHT,
        ) ?: return PendingUpNextMovePublicationResult.Blocked
        onPostStarting()

        return try {
            val acknowledged = apiClient.moveUpNextSessionItem(
                baseUrl = baseUrl,
                token = token,
                payload = UpNextMoveRequest(
                    expectedVersion = inFlight.expectedStructureVersion,
                    itemId = inFlight.itemId,
                    toPosition = inFlight.toPosition,
                ),
            )
            if (requestStillCurrent()) {
                PendingUpNextMovePublicationResult.Acknowledged(acknowledged)
            } else {
                PendingUpNextMovePublicationResult.Blocked
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PendingUpNextMovePublicationResult.PostFailed(inFlight, error)
        }
    }
}

@Serializable
internal data class UpNextMoveDiagnostic(
    val outcome: String,
    val code: String? = null,
    val domain: String? = null,
    val expectedVersion: Long? = null,
    val actualVersion: Long? = null,
    val correlationId: String? = null,
)

/**
 * User-facing copy derived only from the durable move intent and its sanitized outcome.
 * A terminal diagnostic remains scoped with the Up Next metadata until the next move clears it.
 */
internal fun upNextReorderFeedback(
    pending: PendingUpNextSemanticMove?,
    semanticMoveUnsupported: Boolean,
    dirtyLegacySnapshot: Boolean,
    diagnostic: UpNextMoveDiagnostic?,
): String? = when {
    pending?.phase == PendingUpNextMovePhase.QUEUED ->
        "Move pending — reconnect to synchronize this move with its original queue version."
    pending?.phase == PendingUpNextMovePhase.IN_FLIGHT ->
        "Move in progress — waiting for the server acknowledgement."
    pending?.phase == PendingUpNextMovePhase.AMBIGUOUS ->
        "Move outcome uncertain — reconnect or refresh Up Next to learn the server order. The move will not be resent."
    semanticMoveUnsupported ->
        "Reorder is unavailable because this server does not support Up Next moves."
    dirtyLegacySnapshot ->
        "Reorder is unavailable while an older local queue change is reconciled."
    diagnostic?.outcome == "conflict_discarded" ->
        "Up Next changed on another device. The server order was kept; repeat the move only if still wanted."
    diagnostic?.outcome == "ambiguous_reconciled" ->
        "The move outcome was uncertain. Up Next was refreshed from the server; repeat the move only if still needed."
    diagnostic?.outcome == "legacy_order_discarded" ->
        "An older local reorder was not uploaded. Up Next was refreshed; repeat the move only if still wanted."
    diagnostic?.outcome == "legacy_ambiguous_discarded" ->
        "An older ambiguous queue change was not uploaded. Up Next was refreshed."
    diagnostic?.outcome == "legacy_unclassified_discarded" ->
        "An older local queue change could not be classified safely and was not uploaded. Up Next was refreshed."
    diagnostic?.outcome == "rejected" ->
        "The move was rejected. Up Next was refreshed and was not changed by this attempt."
    else -> null
}

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
