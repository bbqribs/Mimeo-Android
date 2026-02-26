package com.mimeo.android.repository

import android.content.Context
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.entities.NowPlayingEntity
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.PendingProgressEntity
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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
) {
    val currentItem: NowPlayingSessionItem?
        get() = items.getOrNull(currentIndex)
}

@Serializable
private data class StoredNowPlayingItem(
    val itemId: Int,
    val title: String? = null,
    val url: String,
    val host: String? = null,
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
    }

    suspend fun loadQueueAndPrefetch(baseUrl: String, token: String, prefetchCount: Int = PREFETCH_DEFAULT): PlaybackQueueResponse {
        val queue = apiClient.getQueue(baseUrl, token)
        val targets = queue.items.take(prefetchCount.coerceIn(1, PREFETCH_MAX))
        for (item in targets) {
            runCatching { apiClient.getItemText(baseUrl, token, item.itemId) }
                .onSuccess { payload -> cacheItem(payload) }
        }
        return queue
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

    suspend fun startSession(queueItems: List<PlaybackQueueItem>, startItemId: Int): NowPlayingSession {
        if (queueItems.isEmpty()) {
            throw IllegalArgumentException("Cannot start now playing session from empty queue")
        }
        val stored = queueItems.map { item ->
            StoredNowPlayingItem(
                itemId = item.itemId,
                title = item.title,
                url = item.url,
                host = item.host,
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
        )
        database.nowPlayingDao().upsert(row)
        return row.toSession(stored)
    }

    suspend fun getSession(): NowPlayingSession? {
        val row = database.nowPlayingDao().getSession() ?: return null
        val stored = parseStoredNowPlaying(row.queueJson)
        if (stored.isEmpty()) {
            database.nowPlayingDao().clear()
            return null
        }
        return row.toSession(stored)
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
                    status = item.status,
                    activeContentVersionId = item.activeContentVersionId,
                    lastReadPercent = item.lastReadPercent,
                    chunkIndex = item.chunkIndex,
                    offsetInChunkChars = item.offsetInChunkChars,
                )
            },
            currentIndex = safeIndex,
            updatedAt = updatedAt,
        )
    }
}
