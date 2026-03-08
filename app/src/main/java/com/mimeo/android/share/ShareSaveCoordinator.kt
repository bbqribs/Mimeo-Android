package com.mimeo.android.share

import android.content.Context
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.LinkedHashMap

data class ShareRefreshEvent(
    val playlistId: Int?,
)

object ShareSaveRefreshBus {
    val events = MutableSharedFlow<ShareRefreshEvent>(extraBufferCapacity = 8)
}

sealed interface ShareSaveResult {
    val notificationText: String
    val opensSettings: Boolean

    data class Saved(
        val destinationLabel: String,
    ) : ShareSaveResult {
        override val notificationText: String = "Saved to $destinationLabel ✅"
        override val opensSettings: Boolean = false
    }

    data class AlreadySaved(
        val destinationLabel: String? = null,
    ) : ShareSaveResult {
        override val notificationText: String =
            destinationLabel?.takeIf { it.isNotBlank() }?.let { "Already in $it ✅" } ?: "Already saved ✅"
        override val opensSettings: Boolean = false
    }

    data object MissingToken : ShareSaveResult {
        override val notificationText: String = "Configure API token in Settings"
        override val opensSettings: Boolean = true
    }

    data object Unauthorized : ShareSaveResult {
        override val notificationText: String = "Check your API token"
        override val opensSettings: Boolean = true
    }

    data object NoValidUrl : ShareSaveResult {
        override val notificationText: String = "No valid URL found"
        override val opensSettings: Boolean = false
    }

    data object NetworkError : ShareSaveResult {
        override val notificationText: String = "Couldn't reach server"
        override val opensSettings: Boolean = false
    }

    data object ServerError : ShareSaveResult {
        override val notificationText: String = "Server error. Try again."
        override val opensSettings: Boolean = false
    }

    data object SaveFailed : ShareSaveResult {
        override val notificationText: String = "Couldn't save article"
        override val opensSettings: Boolean = false
    }
}

class ShareSaveCoordinator(
    context: Context,
    private val apiClient: ApiClient = ApiClient(),
    private val settingsStore: SettingsStore = SettingsStore(context.applicationContext),
) {
    suspend fun saveSharedText(sharedText: String?, sharedTitle: String?): ShareSaveResult {
        val url = extractFirstHttpUrl(sharedText) ?: return ShareSaveResult.NoValidUrl
        val idempotencyKey = buildShareIdempotencyKey(url)
        val current = settingsStore.settingsFlow.first()
        if (current.apiToken.isBlank()) {
            return ShareSaveResult.MissingToken
        }
        val destinationPlaylistId = current.defaultSavePlaylistId
        val wasRecentDuplicate = ShareRecentDuplicateDetector.wasSeenRecently(
            idempotencyKey = idempotencyKey,
            playlistId = destinationPlaylistId,
        )

        return try {
            val article = apiClient.createItem(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                url = url,
                idempotencyKey = idempotencyKey,
                title = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
            )
            val routeResult = routeSavedItem(
                itemId = article.id,
                playlistId = destinationPlaylistId,
                current = current,
            )
            ShareRecentDuplicateDetector.remember(
                idempotencyKey = idempotencyKey,
                playlistId = destinationPlaylistId,
            )
            ShareSaveRefreshBus.events.tryEmit(ShareRefreshEvent(destinationPlaylistId))
            if (routeResult.alreadySaved || wasRecentDuplicate) {
                ShareSaveResult.AlreadySaved(routeResult.destinationLabel)
            } else {
                ShareSaveResult.Saved(routeResult.destinationLabel)
            }
        } catch (error: ApiException) {
            when {
                error.statusCode == 401 -> ShareSaveResult.Unauthorized
                error.statusCode == 409 -> {
                    ShareRecentDuplicateDetector.remember(
                        idempotencyKey = idempotencyKey,
                        playlistId = destinationPlaylistId,
                    )
                    if (destinationPlaylistId == null) {
                        ShareSaveResult.AlreadySaved(destinationLabel = SMART_QUEUE_LABEL)
                    } else {
                        ShareSaveResult.AlreadySaved()
                    }
                }
                error.statusCode in 500..599 -> ShareSaveResult.ServerError
                else -> ShareSaveResult.SaveFailed
            }
        } catch (error: Exception) {
            if (isNetworkError(error)) {
                ShareSaveResult.NetworkError
            } else {
                ShareSaveResult.SaveFailed
            }
        }
    }

    private suspend fun routeSavedItem(itemId: Int, playlistId: Int?, current: AppSettings): PlaylistRouteResult {
        if (playlistId == null) {
            return PlaylistRouteResult(destinationLabel = SMART_QUEUE_LABEL)
        }

        val playlistName = runCatching {
            apiClient.getPlaylists(current.baseUrl, current.apiToken)
                .firstOrNull { it.id == playlistId }
                ?.name
        }.getOrNull()
        val destinationLabel = playlistName?.takeIf { it.isNotBlank() } ?: "playlist #$playlistId"

        return try {
            apiClient.addItemToPlaylist(current.baseUrl, current.apiToken, playlistId, itemId)
            PlaylistRouteResult(destinationLabel = destinationLabel)
        } catch (error: ApiException) {
            if (error.statusCode == 409) {
                PlaylistRouteResult(alreadySaved = true, destinationLabel = destinationLabel)
            } else {
                throw error
            }
        }
    }

    private fun isNetworkError(error: Exception): Boolean = error is IOException
}

private data class PlaylistRouteResult(
    val alreadySaved: Boolean = false,
    val destinationLabel: String = SMART_QUEUE_LABEL,
)

private const val SMART_QUEUE_LABEL = "Smart Queue"

internal object ShareRecentDuplicateDetector {
    internal const val WINDOW_MILLIS: Long = 120_000L
    private const val MAX_ENTRIES = 128
    private val recent = LinkedHashMap<String, Long>()

    @Synchronized
    fun wasSeenRecently(
        idempotencyKey: String,
        playlistId: Int?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        prune(nowMillis)
        val lastSeen = recent[keyFor(idempotencyKey, playlistId)] ?: return false
        return nowMillis - lastSeen <= WINDOW_MILLIS
    }

    @Synchronized
    fun remember(
        idempotencyKey: String,
        playlistId: Int?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        prune(nowMillis)
        recent[keyFor(idempotencyKey, playlistId)] = nowMillis
        trimToMaxSize()
    }

    @Synchronized
    internal fun clearForTest() {
        recent.clear()
    }

    private fun keyFor(idempotencyKey: String, playlistId: Int?): String {
        val destination = playlistId?.toString() ?: "smart"
        return "$destination|$idempotencyKey"
    }

    private fun prune(nowMillis: Long) {
        val cutoff = nowMillis - WINDOW_MILLIS
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value < cutoff) {
                iterator.remove()
            }
        }
    }

    private fun trimToMaxSize() {
        while (recent.size > MAX_ENTRIES) {
            val iterator = recent.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}
