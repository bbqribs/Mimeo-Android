package com.mimeo.android.repository

import com.mimeo.android.model.UpNextSession

internal enum class UpNextCapability {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

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

internal sealed interface UpNextSyncPlan {
    data object MarkCleanAbsent : UpNextSyncPlan
    data class Adopt(val session: UpNextSession?) : UpNextSyncPlan
    data class Replace(val snapshot: LocalUpNextSnapshot, val expectedVersion: Long?) : UpNextSyncPlan
    data class Clear(val expectedVersion: Long) : UpNextSyncPlan
}

/** Reasserting the authoritative active item is presentation work, not a queue mutation. */
internal fun shouldMutateUpNextActiveItem(currentItemId: Int?, requestedItemId: Int): Boolean =
    currentItemId != requestedItemId

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
