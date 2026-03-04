package com.mimeo.android.share

import android.content.Context
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.io.IOException

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
        val playlistName: String? = null,
    ) : ShareSaveResult {
        override val notificationText: String =
            playlistName?.takeIf { it.isNotBlank() }?.let { "Saved to $it ✅" } ?: "Saved ✅"
        override val opensSettings: Boolean = false
    }

    data object AlreadySaved : ShareSaveResult {
        override val notificationText: String = "Already saved ✅"
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
        val current = settingsStore.settingsFlow.first()
        if (current.apiToken.isBlank()) {
            return ShareSaveResult.MissingToken
        }

        return try {
            val article = apiClient.createItem(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                url = url,
                idempotencyKey = buildShareIdempotencyKey(url),
                title = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
            )
            val routeResult = routeSavedItem(
                itemId = article.id,
                playlistId = current.defaultSavePlaylistId,
                current = current,
            )
            ShareSaveRefreshBus.events.tryEmit(ShareRefreshEvent(current.defaultSavePlaylistId))
            if (routeResult.alreadySaved) {
                ShareSaveResult.AlreadySaved
            } else {
                ShareSaveResult.Saved(routeResult.playlistName)
            }
        } catch (error: ApiException) {
            when {
                error.statusCode == 401 -> ShareSaveResult.Unauthorized
                error.statusCode == 409 -> ShareSaveResult.AlreadySaved
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
            return PlaylistRouteResult()
        }

        val playlistName = runCatching {
            apiClient.getPlaylists(current.baseUrl, current.apiToken)
                .firstOrNull { it.id == playlistId }
                ?.name
        }.getOrNull()

        return try {
            apiClient.addItemToPlaylist(current.baseUrl, current.apiToken, playlistId, itemId)
            PlaylistRouteResult(playlistName = playlistName)
        } catch (error: ApiException) {
            if (error.statusCode == 409) {
                PlaylistRouteResult(alreadySaved = true, playlistName = playlistName)
            } else {
                throw error
            }
        }
    }

    private fun isNetworkError(error: Exception): Boolean = error is IOException
}

private data class PlaylistRouteResult(
    val alreadySaved: Boolean = false,
    val playlistName: String? = null,
)
