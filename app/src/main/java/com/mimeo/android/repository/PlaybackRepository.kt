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
        private const val DEBUG_TARGET_ITEM_ID = 409
    }

    suspend fun loadQueueAndPrefetch(
        baseUrl: String,
        token: String,
        playlistId: Int? = null,
        prefetchCount: Int = PREFETCH_DEFAULT,
    ): QueueFetchResult {
        val queueResult = apiClient.getQueue(baseUrl, token, playlistId = playlistId)
        val queue = queueResult.payload
        val targets = if (prefetchCount <= 0) {
            emptyList()
        } else {
            queue.items.take(prefetchCount.coerceAtMost(PREFETCH_MAX))
        }
        for (item in targets) {
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

    suspend fun getItemText(baseUrl: String, token: String, itemId: Int, expectedActiveVersionId: Int?): ItemTextResult {
        return try {
            val payload = apiClient.getItemText(baseUrl, token, itemId)
            cacheItem(payload)
            ItemTextResult(payload = payload, usingCache = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val cached = database.cachedItemDao().findByItemId(itemId)
            if (cached == null) {
                throw error
            }

            if (expectedActiveVersionId != null && cached.activeContentVersionId != expectedActiveVersionId) {
                throw IllegalStateException("Offline and not cached for current active version")
            }

            ItemTextResult(payload = cached.toPayload(), usingCache = true)
        }
    }

    suspend fun postProgress(baseUrl: String, token: String, itemId: Int, percent: Int): ProgressPostResult {
        val clamped = percent.coerceIn(0, 100)
        return try {
            apiClient.postProgress(baseUrl, token, itemId, clamped, source = "playback")
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
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun CachedItemEntity.toPayload(): ItemTextResponse {
        val parsedParagraphs = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), paragraphsJson)
        }.getOrElse { emptyList() }
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
                )
            },
            currentIndex = safeIndex,
            updatedAt = updatedAt,
            sourcePlaylistId = sourcePlaylistId,
        )
    }
}
