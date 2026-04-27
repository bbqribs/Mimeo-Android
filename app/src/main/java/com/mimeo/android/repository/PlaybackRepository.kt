package com.mimeo.android.repository

import android.content.Context
import android.util.Log
import com.mimeo.android.BuildConfig
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.QueueFetchResult
import com.mimeo.android.data.entities.NowPlayingEntity
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.PendingProgressEntity
import com.mimeo.android.model.ArticleSummary
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.ItemTextContentBlock
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ItemTextPrefetchAttempt(
    val itemId: Int,
    val success: Boolean,
    val errorSummary: String? = null,
    val retryable: Boolean = true,
)

data class ItemTextResult(
    val payload: ItemTextResponse,
    val usingCache: Boolean,
    val staleCachedVersion: Boolean = false,
)

data class ProgressPostResult(
    val queued: Boolean,
)

data class FlushProgressResult(
    val flushedCount: Int,
    val retryableFailures: Int,
    val pendingCount: Int,
)

data class NowPlayingSessionItem(
    val itemId: Int,
    val title: String?,
    val url: String,
    val host: String?,
    val sourceType: String?,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val captureKind: String?,
    val sourceAppPackage: String?,
    val status: String?,
    val activeContentVersionId: Int?,
    val lastReadPercent: Int?,
    val chunkIndex: Int,
    val offsetInChunkChars: Int,
    val readerScrollOffset: Int,
)

data class NowPlayingSession(
    val items: List<NowPlayingSessionItem>,
    val currentIndex: Int,
    val updatedAt: Long,
    val sourcePlaylistId: Int?,
) {
    val currentItem: NowPlayingSessionItem?
        get() = items.getOrNull(currentIndex)
}

data class SessionLoadResult(
    val session: NowPlayingSession?,
    val wasCorrupt: Boolean,
)

data class PlaylistMembershipToggleResult(
    val added: Boolean,
)

internal fun shouldAllowStaleCacheFallback(error: Exception): Boolean {
    return when (error) {
        is IOException -> true
        is ApiException -> error.statusCode in 500..599
        else -> false
    }
}

internal data class SessionReorderPlan(
    val fromIndex: Int,
    val toIndex: Int,
    val currentIndex: Int,
)

internal fun computeSessionReorderPlan(
    itemCount: Int,
    currentIndex: Int,
    fromIndex: Int,
    toIndex: Int,
): SessionReorderPlan? {
    if (itemCount <= 1) return null
    val maxIndex = itemCount - 1
    val normalizedFrom = fromIndex.coerceIn(0, maxIndex)
    val normalizedTo = toIndex.coerceIn(0, maxIndex)
    if (normalizedFrom == normalizedTo) return null
    val safeCurrent = currentIndex.coerceIn(0, maxIndex)
    val normalizedCurrent = when {
        safeCurrent == normalizedFrom -> normalizedTo
        normalizedFrom < safeCurrent && normalizedTo >= safeCurrent -> safeCurrent - 1
        normalizedFrom > safeCurrent && normalizedTo <= safeCurrent -> safeCurrent + 1
        else -> safeCurrent
    }
    return SessionReorderPlan(
        fromIndex = normalizedFrom,
        toIndex = normalizedTo,
        currentIndex = normalizedCurrent,
    )
}

internal fun computeClearUpcomingKeepCount(
    itemCount: Int,
    currentIndex: Int,
): Int {
    if (itemCount <= 0) return 0
    return currentIndex.coerceIn(0, itemCount - 1) + 1
}

@Serializable
private data class StoredNowPlayingItem(
    val itemId: Int,
    val title: String? = null,
    val url: String,
    val host: String? = null,
    val sourceType: String? = null,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
    val captureKind: String? = null,
    val sourceAppPackage: String? = null,
    val status: String? = null,
    val activeContentVersionId: Int? = null,
    val lastReadPercent: Int? = null,
    val chunkIndex: Int = 0,
    val offsetInChunkChars: Int = 0,
    val readerScrollOffset: Int = 0,
)

class PlaybackRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val appContext: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        private const val PREFETCH_DEFAULT = 5
        private const val PREFETCH_MAX = 10
        private const val MAX_ATTEMPTS = 10
        private const val MAX_ERROR_CHARS = 240
        private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
        private const val TEXT_LOAD_POLICY_DEBUG_TAG = "MimeoTextLoadPolicy"
        private const val DEBUG_TARGET_ITEM_ID = 409
    }

    suspend fun loadQueueAndPrefetch(
        baseUrl: String,
        token: String,
        playlistId: Int? = null,
        prefetchCount: Int = PREFETCH_DEFAULT,
        sortField: String = "created",
        sortDir: String = "desc",
    ): QueueFetchResult {
        val queueResult = apiClient.getQueue(baseUrl, token, playlistId = playlistId, sortField = sortField, sortDir = sortDir)
        val queue = queueResult.payload
        val targets = if (prefetchCount <= 0) {
            emptyList()
        } else {
            queue.items.take(prefetchCount.coerceAtMost(PREFETCH_MAX))
        }
        for (item in targets) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TEXT_LOAD_POLICY_DEBUG_TAG,
                    "trigger=queue_prefetch item=${item.itemId} requested_policy=network_first",
                )
            }
            runCatching { apiClient.getItemText(baseUrl, token, item.itemId) }
                .onSuccess { payload -> cacheItem(payload) }
        }
        val snapshot = queueResult.debugSnapshot.copy(
            appliedItemCount = queue.items.size,
            appliedContains409 = queue.items.any { it.itemId == DEBUG_TARGET_ITEM_ID },
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                QUEUE_DEBUG_TAG,
                "repositoryApply playlistId=$playlistId appliedCount=${snapshot.appliedItemCount} appliedContains409=${snapshot.appliedContains409}",
            )
        }
        return queueResult.copy(debugSnapshot = snapshot)
    }

    suspend fun prefetchItemTexts(
        baseUrl: String,
        token: String,
        itemIds: List<Int>,
    ): List<ItemTextPrefetchAttempt> {
        return itemIds.distinct().map { itemId ->
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TEXT_LOAD_POLICY_DEBUG_TAG,
                        "trigger=prefetch_batch item=$itemId requested_policy=network_first",
                    )
                }
                val payload = apiClient.getItemText(baseUrl, token, itemId)
                cacheItem(payload)
                ItemTextPrefetchAttempt(itemId = itemId, success = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val summary = when (error) {
                    is ApiException -> "api:${error.statusCode}:${error.message.orEmpty()}"
                    else -> "${error::class.simpleName}:${error.message.orEmpty()}"
                }.take(240)
                ItemTextPrefetchAttempt(
                    itemId = itemId,
                    success = false,
                    errorSummary = summary,
                    retryable = isRetryablePrefetchFailure(error),
                )
            }
        }
    }

    suspend fun listPlaylists(baseUrl: String, token: String): List<PlaylistSummary> {
        return apiClient.getPlaylists(baseUrl, token)
    }

    suspend fun listTrashedItems(baseUrl: String, token: String): List<ArticleSummary> {
        return apiClient.getTrashedItems(baseUrl, token)
    }

    suspend fun listArchivedItems(baseUrl: String, token: String): List<ArticleSummary> {
        return apiClient.getArchivedItems(baseUrl, token)
    }

    suspend fun listItemsByView(
        baseUrl: String,
        token: String,
        view: ApiClient.ItemsView,
        sort: String? = null,
        dir: String? = null,
        q: String? = null,
    ): List<ArticleSummary> {
        return apiClient.getItemsByView(baseUrl, token, view, sort = sort, dir = dir, q = q)
    }

    suspend fun createPlaylist(baseUrl: String, token: String, name: String): PlaylistSummary {
        return apiClient.createPlaylist(baseUrl, token, name)
    }

    suspend fun renamePlaylist(baseUrl: String, token: String, playlistId: Int, name: String): PlaylistSummary {
        return apiClient.renamePlaylist(baseUrl, token, playlistId, name)
    }

    suspend fun deletePlaylist(baseUrl: String, token: String, playlistId: Int) {
        apiClient.deletePlaylist(baseUrl, token, playlistId)
    }

    suspend fun togglePlaylistMembership(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemId: Int,
        isCurrentlyMember: Boolean,
    ): PlaylistMembershipToggleResult {
        if (isCurrentlyMember) {
            apiClient.removeItemFromPlaylist(baseUrl, token, playlistId, itemId)
            return PlaylistMembershipToggleResult(added = false)
        }

        apiClient.addItemToPlaylist(baseUrl, token, playlistId, itemId)
        return PlaylistMembershipToggleResult(added = true)
    }

    suspend fun getItemText(
        baseUrl: String,
        token: String,
        itemId: Int,
        expectedActiveVersionId: Int?,
        preferLocal: Boolean = false,
    ): ItemTextResult {
        if (BuildConfig.DEBUG) {
            Log.d(
                TEXT_LOAD_POLICY_DEBUG_TAG,
                "trigger=repository_get_item_text item=$itemId requested_policy=${if (preferLocal) "cache_first" else "network_first"}",
            )
        }
        if (preferLocal) {
            getCachedItemText(itemId = itemId, expectedActiveVersionId = expectedActiveVersionId)?.let { cached ->
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TEXT_LOAD_POLICY_DEBUG_TAG,
                        "trigger=repository_get_item_text item=$itemId resolved_source=cache",
                    )
                }
                return cached
            }
        }
        return try {
            val payload = enrichItemTextWithContentBlocks(
                baseUrl = baseUrl,
                token = token,
                payload = apiClient.getItemText(baseUrl, token, itemId),
            )
            cacheItem(payload)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TEXT_LOAD_POLICY_DEBUG_TAG,
                    "trigger=repository_get_item_text item=$itemId resolved_source=network",
                )
            }
            ItemTextResult(payload = payload, usingCache = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val allowStaleVersionFallback = shouldAllowStaleCacheFallback(error)
            val fallback = getCachedItemText(
                itemId = itemId,
                expectedActiveVersionId = expectedActiveVersionId,
                allowVersionMismatch = allowStaleVersionFallback,
            )
            if (BuildConfig.DEBUG) {
                Log.d(
                    TEXT_LOAD_POLICY_DEBUG_TAG,
                    if (fallback != null) {
                        "trigger=repository_get_item_text item=$itemId resolved_source=cache_fallback stale_cached_version=${fallback.staleCachedVersion}"
                    } else {
                        "trigger=repository_get_item_text item=$itemId failed=${error::class.simpleName}:${error.message.orEmpty()}"
                    },
                )
            }
            fallback ?: throw error
        }
    }

    private suspend fun getCachedItemText(
        itemId: Int,
        expectedActiveVersionId: Int?,
        allowVersionMismatch: Boolean = false,
    ): ItemTextResult? {
        val cached = database.cachedItemDao().findByItemId(itemId) ?: return null
        val hasVersionMismatch =
            expectedActiveVersionId != null && cached.activeContentVersionId != expectedActiveVersionId
        if (hasVersionMismatch && !allowVersionMismatch) {
            return null
        }
        return ItemTextResult(
            payload = cached.toPayload(),
            usingCache = true,
            staleCachedVersion = hasVersionMismatch,
        )
    }

    suspend fun postProgress(
        baseUrl: String,
        token: String,
        itemId: Int,
        percent: Int,
        chunkIndex: Int? = null,
        offsetInChunkChars: Int? = null,
        readerScrollOffset: Int? = null,
    ): ProgressPostResult {
        val clamped = percent.coerceIn(0, 100)
        return try {
            apiClient.postProgress(
                baseUrl = baseUrl,
                token = token,
                itemId = itemId,
                percent = clamped,
                source = "playback",
                chunkIndex = chunkIndex?.coerceAtLeast(0),
                offsetInChunkChars = offsetInChunkChars?.coerceAtLeast(0),
                readerScrollOffset = readerScrollOffset?.coerceAtLeast(0),
            )
            ProgressPostResult(queued = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isRetryableProgressFailure(error)) {
                enqueuePendingProgress(itemId = itemId, percent = clamped)
                ProgressPostResult(queued = true)
            } else {
                throw error
            }
        }
    }

    suspend fun toggleCompletion(baseUrl: String, token: String, itemId: Int, markDone: Boolean): ProgressPostResult {
        return if (markDone) {
            apiClient.markItemDone(baseUrl, token, itemId, autoArchive = false)
            ProgressPostResult(queued = false)
        } else {
            apiClient.resetItemDone(baseUrl, token, itemId)
            ProgressPostResult(queued = false)
        }
    }

    suspend fun archiveItem(baseUrl: String, token: String, itemId: Int): ProgressPostResult {
        apiClient.markItemDone(baseUrl, token, itemId, autoArchive = true)
        return ProgressPostResult(queued = false)
    }

    suspend fun unarchiveItem(baseUrl: String, token: String, itemId: Int): ProgressPostResult {
        apiClient.unarchiveItem(baseUrl, token, itemId)
        return ProgressPostResult(queued = false)
    }

    suspend fun moveItemToBin(baseUrl: String, token: String, itemId: Int): ProgressPostResult {
        apiClient.moveItemToBin(baseUrl, token, itemId)
        return ProgressPostResult(queued = false)
    }

    suspend fun restoreItemFromBin(baseUrl: String, token: String, itemId: Int): ProgressPostResult {
        apiClient.restoreItemFromBin(baseUrl, token, itemId)
        return ProgressPostResult(queued = false)
    }

    suspend fun purgeItemFromBin(baseUrl: String, token: String, itemId: Int): ProgressPostResult {
        apiClient.purgeItemFromBin(baseUrl, token, itemId)
        return ProgressPostResult(queued = false)
    }

    suspend fun setFavoriteState(
        baseUrl: String,
        token: String,
        itemId: Int,
        favorited: Boolean,
    ): ProgressPostResult {
        apiClient.setFavoriteState(baseUrl, token, itemId, favorited)
        return ProgressPostResult(queued = false)
    }

    suspend fun batchItemAction(
        baseUrl: String,
        token: String,
        action: String,
        itemIds: List<Int>,
    ) = apiClient.batchItemAction(baseUrl, token, action, itemIds)

    suspend fun reorderPlaylistEntries(baseUrl: String, token: String, playlistId: Int, entryIds: List<Int>) {
        apiClient.reorderPlaylistEntries(baseUrl, token, playlistId, entryIds)
    }

    suspend fun batchAddItemsToPlaylist(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemIds: List<Int>,
    ) = apiClient.batchAddItemsToPlaylist(baseUrl, token, playlistId, itemIds)

    suspend fun flushPendingProgress(baseUrl: String, token: String): FlushProgressResult {
        val dao = database.pendingProgressDao()
        val pending = dao.listPending()
        var flushedCount = 0
        var retryableFailures = 0

        for (entry in pending) {
            if (entry.attemptCount >= MAX_ATTEMPTS) {
                continue
            }

            try {
                apiClient.postProgress(
                    baseUrl,
                    token,
                    entry.itemId,
                    entry.percent.coerceIn(0, 100),
                    source = "playback",
                )
                dao.deleteById(entry.id)
                flushedCount += 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val nextAttempt = entry.attemptCount + 1
                if (!isRetryableProgressFailure(error)) {
                    dao.recordAttempt(
                        id = entry.id,
                        attemptCount = nextAttempt,
                        lastAttemptAt = System.currentTimeMillis(),
                        lastError = truncateError(error.message),
                    )
                    continue
                }
                retryableFailures += 1
                dao.recordAttempt(
                    id = entry.id,
                    attemptCount = nextAttempt,
                    lastAttemptAt = System.currentTimeMillis(),
                    lastError = truncateError(error.message),
                )
            }
        }

        val pendingCount = dao.countPending()
        return FlushProgressResult(
            flushedCount = flushedCount,
            retryableFailures = retryableFailures,
            pendingCount = pendingCount,
        )
    }

    suspend fun countPendingProgress(): Int {
        return database.pendingProgressDao().countPending()
    }

    suspend fun getCachedItemIds(itemIds: List<Int>): Set<Int> {
        if (itemIds.isEmpty()) return emptySet()
        return database.cachedItemDao().findCachedIds(itemIds.distinct()).toSet()
    }

    suspend fun getCachedActiveContentVersionIds(versionIds: List<Int>): Set<Int> {
        if (versionIds.isEmpty()) return emptySet()
        return database.cachedItemDao().findCachedActiveContentVersionIds(versionIds.distinct()).toSet()
    }

    suspend fun isItemCached(itemId: Int): Boolean {
        return database.cachedItemDao().findByItemId(itemId) != null
    }

    suspend fun startSession(
        queueItems: List<PlaybackQueueItem>,
        startItemId: Int,
        sourcePlaylistId: Int?,
    ): NowPlayingSession {
        if (queueItems.isEmpty()) {
            throw IllegalArgumentException("Cannot start now playing session from empty queue")
        }
        val stored = queueItems.map { item ->
            StoredNowPlayingItem(
                itemId = item.itemId,
                title = item.title,
                url = item.url,
                host = item.host,
                sourceType = item.sourceType,
                sourceLabel = item.sourceLabel,
                sourceUrl = item.sourceUrl,
                captureKind = item.captureKind,
                sourceAppPackage = item.sourceAppPackage,
                status = item.status,
                activeContentVersionId = item.activeContentVersionId,
                lastReadPercent = item.lastReadPercent,
                chunkIndex = 0,
                offsetInChunkChars = 0,
                readerScrollOffset = 0,
            )
        }
        val currentIndex = queueItems.indexOfFirst { it.itemId == startItemId }.let { if (it >= 0) it else 0 }
        val updatedAt = System.currentTimeMillis()
        val row = NowPlayingEntity(
            id = 1,
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            currentIndex = currentIndex,
            updatedAt = updatedAt,
            sourcePlaylistId = sourcePlaylistId,
        )
        database.nowPlayingDao().upsert(row)
        return row.toSession(stored)
    }

    suspend fun reseedSessionFromSource(
        sourceItems: List<PlaybackQueueItem>,
        preferredCurrentItemId: Int?,
        sourcePlaylistId: Int?,
    ): NowPlayingSession? {
        if (sourceItems.isEmpty()) {
            database.nowPlayingDao().clear()
            return null
        }
        val startItemId = preferredCurrentItemId
            ?.takeIf { preferredId -> sourceItems.any { it.itemId == preferredId } }
            ?: sourceItems.first().itemId
        return startSession(
            queueItems = sourceItems,
            startItemId = startItemId,
            sourcePlaylistId = sourcePlaylistId,
        )
    }

    suspend fun getSession(): NowPlayingSession? {
        return getSessionLoadResult().session
    }

    suspend fun getSessionLoadResult(): SessionLoadResult {
        val row = database.nowPlayingDao().getSession() ?: return SessionLoadResult(
            session = null,
            wasCorrupt = false,
        )
        val stored = parseStoredNowPlaying(row.queueJson)
        if (stored.isEmpty()) {
            database.nowPlayingDao().clear()
            return SessionLoadResult(session = null, wasCorrupt = true)
        }
        return SessionLoadResult(session = row.toSession(stored), wasCorrupt = false)
    }

    suspend fun restartSession(): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }

        val restarted = stored.map {
            it.copy(
                chunkIndex = 0,
                offsetInChunkChars = 0,
            )
        }
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), restarted),
            currentIndex = 0,
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(restarted)
    }

    suspend fun setCurrentIndex(currentIndex: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson)
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val normalized = currentIndex.coerceIn(0, stored.lastIndex)
        val updatedAt = System.currentTimeMillis()
        dao.updateCurrentIndex(currentIndex = normalized, updatedAt = updatedAt)
        return row.copy(currentIndex = normalized, updatedAt = updatedAt).toSession(stored)
    }

    suspend fun setCurrentPlaybackPosition(itemId: Int, chunkIndex: Int, offsetInChunkChars: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val idx = stored.indexOfFirst { it.itemId == itemId }
        if (idx < 0) {
            return row.toSession(stored)
        }

        stored[idx] = stored[idx].copy(
            chunkIndex = chunkIndex.coerceAtLeast(0),
            offsetInChunkChars = offsetInChunkChars.coerceAtLeast(0),
        )
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun setCurrentReaderScrollOffset(itemId: Int, readerScrollOffset: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val idx = stored.indexOfFirst { it.itemId == itemId }
        if (idx < 0) {
            return row.toSession(stored)
        }
        val safeOffset = readerScrollOffset.coerceAtLeast(0)
        if (stored[idx].readerScrollOffset == safeOffset) {
            return row.toSession(stored)
        }
        stored[idx] = stored[idx].copy(readerScrollOffset = safeOffset)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun setNowPlayingItemProgress(itemId: Int, percent: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val idx = stored.indexOfFirst { it.itemId == itemId }
        if (idx < 0) {
            return row.toSession(stored)
        }

        val clamped = percent.coerceIn(0, 100)
        val merged = maxOf(stored[idx].lastReadPercent ?: 0, clamped)
        stored[idx] = stored[idx].copy(lastReadPercent = merged)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun setNowPlayingItemCanonicalProgress(itemId: Int, percent: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val idx = stored.indexOfFirst { it.itemId == itemId }
        if (idx < 0) {
            return row.toSession(stored)
        }

        val clamped = percent.coerceIn(0, 100)
        stored[idx] = stored[idx].copy(lastReadPercent = clamped)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun reconcileSessionWithQueue(queueItems: List<PlaybackQueueItem>): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }

        val queueById = queueItems.associateBy { it.itemId }
        val reconciled = stored.map { item ->
            val refreshed = queueById[item.itemId] ?: return@map item
            item.copy(
                title = refreshed.title,
                url = refreshed.url,
                host = refreshed.host,
                sourceType = refreshed.sourceType,
                sourceLabel = refreshed.sourceLabel,
                sourceUrl = refreshed.sourceUrl,
                captureKind = refreshed.captureKind,
                sourceAppPackage = refreshed.sourceAppPackage,
                status = refreshed.status,
                activeContentVersionId = refreshed.activeContentVersionId,
                lastReadPercent = refreshed.lastReadPercent,
            )
        }
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), reconciled),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(reconciled)
    }

    suspend fun insertItemAfterCurrent(item: PlaybackQueueItem): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        // Remove any existing occurrence so we don't accumulate duplicates.
        val existingIdx = stored.indexOfFirst { it.itemId == item.itemId }
        if (existingIdx >= 0 && existingIdx != row.currentIndex) {
            stored.removeAt(existingIdx)
        } else if (existingIdx == row.currentIndex) {
            // Item is currently playing — no-op for Play Next.
            return row.toSession(stored)
        }
        val insertIndex = (row.currentIndex + 1).coerceIn(0, stored.size)
        val newItem = StoredNowPlayingItem(
            itemId = item.itemId,
            title = item.title,
            url = item.url,
            host = item.host,
            sourceType = item.sourceType,
            sourceLabel = item.sourceLabel,
            sourceUrl = item.sourceUrl,
            captureKind = item.captureKind,
            sourceAppPackage = item.sourceAppPackage,
            status = item.status,
            activeContentVersionId = item.activeContentVersionId,
            lastReadPercent = item.lastReadPercent,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
        )
        stored.add(insertIndex, newItem)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun playNowInSession(item: PlaybackQueueItem): NowPlayingSession {
        val dao = database.nowPlayingDao()
        val row = dao.getSession()
        if (row == null) {
            return startSession(listOf(item), item.itemId, null)
        }
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return startSession(listOf(item), item.itemId, null)
        }
        var currentIndex = row.currentIndex.coerceIn(0, stored.lastIndex)
        val existingIdx = stored.indexOfFirst { it.itemId == item.itemId }
        if (existingIdx == currentIndex) {
            // Item is already the active item — no-op.
            return row.toSession(stored)
        }
        if (existingIdx >= 0) {
            stored.removeAt(existingIdx)
            if (existingIdx < currentIndex) currentIndex--
        }
        val newItem = StoredNowPlayingItem(
            itemId = item.itemId,
            title = item.title,
            url = item.url,
            host = item.host,
            sourceType = item.sourceType,
            sourceLabel = item.sourceLabel,
            sourceUrl = item.sourceUrl,
            captureKind = item.captureKind,
            sourceAppPackage = item.sourceAppPackage,
            status = item.status,
            activeContentVersionId = item.activeContentVersionId,
            lastReadPercent = item.lastReadPercent,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
        )
        // Insert at currentIndex: new item becomes active, old active shifts to upcoming.
        stored.add(currentIndex, newItem)
        val updatedRow = row.copy(
            currentIndex = currentIndex,
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun appendItemToSession(item: PlaybackQueueItem): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        // Remove any existing occurrence so we don't accumulate duplicates.
        val existingIdx = stored.indexOfFirst { it.itemId == item.itemId }
        if (existingIdx >= 0 && existingIdx != row.currentIndex) {
            stored.removeAt(existingIdx)
        } else if (existingIdx == row.currentIndex) {
            // Item is currently playing — already at end semantically; no-op.
            return row.toSession(stored)
        }
        val newItem = StoredNowPlayingItem(
            itemId = item.itemId,
            title = item.title,
            url = item.url,
            host = item.host,
            sourceType = item.sourceType,
            sourceLabel = item.sourceLabel,
            sourceUrl = item.sourceUrl,
            captureKind = item.captureKind,
            sourceAppPackage = item.sourceAppPackage,
            status = item.status,
            activeContentVersionId = item.activeContentVersionId,
            lastReadPercent = item.lastReadPercent,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
        )
        stored.add(newItem)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun appendItemsToSession(items: List<PlaybackQueueItem>): NowPlayingSession? {
        val orderedItems = items.distinctBy { it.itemId }
        if (orderedItems.isEmpty()) return getSession()
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }

        val currentItemId = stored.getOrNull(row.currentIndex.coerceIn(0, stored.lastIndex))?.itemId
        val appendItemIds = orderedItems
            .map { it.itemId }
            .filter { it != currentItemId }
            .toSet()
        if (appendItemIds.isEmpty()) return row.toSession(stored)

        stored.removeAll { it.itemId in appendItemIds }
        val updatedCurrentIndex = currentItemId
            ?.let { id -> stored.indexOfFirst { it.itemId == id }.takeIf { it >= 0 } }
            ?: row.currentIndex.coerceIn(0, stored.lastIndex)

        orderedItems
            .filter { it.itemId in appendItemIds }
            .forEach { item -> stored.add(item.toStoredNowPlayingItem()) }

        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            currentIndex = updatedCurrentIndex,
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun removeItemFromSession(itemId: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val removeIdx = stored.indexOfFirst { it.itemId == itemId }
        if (removeIdx < 0) return row.toSession(stored)
        stored.removeAt(removeIdx)
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        // Adjust currentIndex if needed so it still points to a valid item.
        val newCurrentIndex = when {
            removeIdx < row.currentIndex -> (row.currentIndex - 1).coerceAtLeast(0)
            else -> row.currentIndex.coerceIn(0, stored.lastIndex)
        }
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            currentIndex = newCurrentIndex,
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun reorderSessionItem(fromIndex: Int, toIndex: Int): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson).toMutableList()
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val reorder = computeSessionReorderPlan(
            itemCount = stored.size,
            currentIndex = row.currentIndex,
            fromIndex = fromIndex,
            toIndex = toIndex,
        ) ?: return row.toSession(stored)
        val moved = stored.removeAt(reorder.fromIndex)
        stored.add(reorder.toIndex, moved)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), stored),
            currentIndex = reorder.currentIndex,
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(stored)
    }

    suspend fun clearUpcomingFromSession(): NowPlayingSession? {
        val dao = database.nowPlayingDao()
        val row = dao.getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson)
        if (stored.isEmpty()) {
            dao.clear()
            return null
        }
        val keepCount = computeClearUpcomingKeepCount(
            itemCount = stored.size,
            currentIndex = row.currentIndex,
        )
        if (keepCount >= stored.size) {
            return row.toSession(stored)
        }
        val retained = stored.take(keepCount)
        val updatedAt = System.currentTimeMillis()
        val updatedRow = row.copy(
            queueJson = json.encodeToString(ListSerializer(StoredNowPlayingItem.serializer()), retained),
            currentIndex = row.currentIndex.coerceIn(0, retained.lastIndex),
            updatedAt = updatedAt,
        )
        dao.upsert(updatedRow)
        return updatedRow.toSession(retained)
    }

    suspend fun clearSession() {
        database.nowPlayingDao().clear()
    }

    private suspend fun enqueuePendingProgress(itemId: Int, percent: Int) {
        database.pendingProgressDao().upsert(
            PendingProgressEntity(
                itemId = itemId,
                percent = percent,
                createdAt = System.currentTimeMillis(),
                attemptCount = 0,
                lastAttemptAt = null,
                lastError = null,
            ),
        )
        WorkScheduler.enqueueProgressSync(appContext)
    }

    private suspend fun cacheItem(payload: ItemTextResponse) {
        val paragraphs = payload.paragraphs.orEmpty()
        val paragraphsJson = json.encodeToString(ListSerializer(String.serializer()), paragraphs)
        val contentBlocksJson = payload.contentBlocks
            ?.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(ListSerializer(ItemTextContentBlock.serializer()), it) }
        database.cachedItemDao().upsert(
            CachedItemEntity(
                itemId = payload.itemId,
                activeContentVersionId = payload.activeContentVersionId,
                title = payload.title,
                url = payload.url,
                host = payload.host,
                status = payload.status,
                wordCount = payload.wordCount,
                text = payload.text,
                paragraphsJson = paragraphsJson,
                contentBlocksJson = contentBlocksJson,
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun enrichItemTextWithContentBlocks(
        baseUrl: String,
        token: String,
        payload: ItemTextResponse,
    ): ItemTextResponse {
        if (!payload.contentBlocks.isNullOrEmpty()) return payload
        val fallbackBlocks = runCatching { apiClient.getItemContentBlocks(baseUrl, token, payload.itemId) }
            .getOrNull()
            .orEmpty()
        if (fallbackBlocks.isEmpty()) return payload
        return payload.copy(contentBlocks = fallbackBlocks)
    }

    private fun CachedItemEntity.toPayload(): ItemTextResponse {
        val parsedParagraphs = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), paragraphsJson)
        }.getOrElse { emptyList() }
        val parsedContentBlocks = contentBlocksJson
            ?.takeIf { it.isNotBlank() }
            ?.let { serialized ->
                runCatching {
                    json.decodeFromString(
                        ListSerializer(ItemTextContentBlock.serializer()),
                        serialized,
                    )
                }.getOrElse { emptyList() }
            }
        return ItemTextResponse(
            itemId = itemId,
            title = title,
            url = url,
            host = host,
            status = status,
            activeContentVersionId = activeContentVersionId,
            strategyUsed = null,
            wordCount = wordCount,
            text = text,
            paragraphs = parsedParagraphs,
            contentBlocks = parsedContentBlocks,
        )
    }

    private fun isRetryableProgressFailure(error: Exception): Boolean {
        return error is IOException
    }

    private fun isRetryablePrefetchFailure(error: Exception): Boolean {
        return when (error) {
            is IOException -> true
            is ApiException -> {
                when {
                    error.statusCode == 404 -> false
                    error.statusCode == 409 &&
                        error.message.orEmpty().contains("No active content", ignoreCase = true) -> false
                    error.statusCode in 500..599 -> true
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun truncateError(message: String?): String? {
        val clean = message?.trim().orEmpty()
        if (clean.isBlank()) return null
        return clean.take(MAX_ERROR_CHARS)
    }

    private fun parseStoredNowPlaying(queueJson: String): List<StoredNowPlayingItem> {
        return runCatching {
            json.decodeFromString(ListSerializer(StoredNowPlayingItem.serializer()), queueJson)
        }.getOrElse { emptyList() }
    }

    private fun NowPlayingEntity.toSession(stored: List<StoredNowPlayingItem>): NowPlayingSession {
        val safeIndex = if (stored.isEmpty()) 0 else currentIndex.coerceIn(0, stored.lastIndex)
        return NowPlayingSession(
            items = stored.map { item ->
                NowPlayingSessionItem(
                    itemId = item.itemId,
                    title = item.title,
                    url = item.url,
                    host = item.host,
                    sourceType = item.sourceType,
                    sourceLabel = item.sourceLabel,
                    sourceUrl = item.sourceUrl,
                    captureKind = item.captureKind,
                    sourceAppPackage = item.sourceAppPackage,
                    status = item.status,
                    activeContentVersionId = item.activeContentVersionId,
                    lastReadPercent = item.lastReadPercent,
                    chunkIndex = item.chunkIndex,
                    offsetInChunkChars = item.offsetInChunkChars,
                    readerScrollOffset = item.readerScrollOffset,
                )
            },
            currentIndex = safeIndex,
            updatedAt = updatedAt,
            sourcePlaylistId = sourcePlaylistId,
        )
    }

    private fun PlaybackQueueItem.toStoredNowPlayingItem(): StoredNowPlayingItem =
        StoredNowPlayingItem(
            itemId = itemId,
            title = title,
            url = url,
            host = host,
            sourceType = sourceType,
            sourceLabel = sourceLabel,
            sourceUrl = sourceUrl,
            captureKind = captureKind,
            sourceAppPackage = sourceAppPackage,
            status = status,
            activeContentVersionId = activeContentVersionId,
            lastReadPercent = lastReadPercent,
            chunkIndex = 0,
            offsetInChunkChars = 0,
            readerScrollOffset = 0,
        )
}
