package com.mimeo.android.repository

import android.content.Context
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.entities.CachedItemEntity
import com.mimeo.android.data.entities.PendingProgressEntity
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
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
            apiClient.postProgress(baseUrl, token, itemId, clamped)
            ProgressPostResult(queued = false)
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
                apiClient.postProgress(baseUrl, token, entry.itemId, entry.percent.coerceIn(0, 100))
                dao.deleteById(entry.id)
                flushedCount += 1
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
}
