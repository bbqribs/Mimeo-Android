package com.mimeo.android.share

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.mimeo.android.BuildConfig
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

private const val SHARE_CREATE_TIMEOUT_MS = 45_000L
private const val SHARE_ONLINE_DEADLINE_MS = 8_000L
private const val MANUAL_SAVE_ONLINE_DEADLINE_MS = 8_000L
private const val SHARE_POST_FAILURE_RESOLUTION_ATTEMPTS = 2
private const val SHARE_POST_FAILURE_RESOLUTION_DELAY_MS = 1_200L
private const val AUTO_DOWNLOAD_MAX_ATTEMPTS = 4
private const val AUTO_DOWNLOAD_RETRY_DELAY_MS = 1200L

data class ShareRefreshEvent(
    val playlistId: Int?,
    val itemId: Int? = null,
)

object ShareSaveRefreshBus {
    val events = MutableSharedFlow<ShareRefreshEvent>(extraBufferCapacity = 8)
}

internal fun shouldAttemptLateShareSaveResolution(result: ShareSaveResult): Boolean = when (result) {
    ShareSaveResult.NetworkError,
    ShareSaveResult.TimedOut,
    ShareSaveResult.SaveFailed,
    -> true
    else -> false
}

sealed interface ShareSaveResult {
    val notificationText: String
    val opensSettings: Boolean
    val notificationTitle: String
        get() = "Mimeo"

    data class Saved(
        val destinationName: String,
        val itemTitle: String? = null,
    ) : ShareSaveResult {
        override val notificationText: String = "Saved to $destinationName ✅"
        override val opensSettings: Boolean = false
        override val notificationTitle: String = itemTitle?.trim().orEmpty().takeIf { it.isNotEmpty() }?.let {
            "Saved: $it"
        } ?: "Mimeo"
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

    data object TimedOut : ShareSaveResult {
        override val notificationText: String = "Save timed out. Try again."
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

    data object PendingQueued : ShareSaveResult {
        override val notificationText: String = "Added to Pending Saves"
        override val opensSettings: Boolean = false
    }
}

class ShareSaveCoordinator(
    context: Context,
    private val apiClient: ApiClient = ApiClient(),
    private val settingsStore: SettingsStore = SettingsStore(context.applicationContext),
    private val playbackRepository: PlaybackRepository = PlaybackRepository(
        apiClient = apiClient,
        database = AppDatabase.getInstance(context.applicationContext),
        appContext = context.applicationContext,
    ),
) {
    private val appContext = context.applicationContext
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun saveSharedText(
        sharedText: String?,
        sharedTitle: String?,
        destinationPlaylistIdOverride: Int? = null,
    ): ShareSaveResult {
        val attemptId = ShareSaveDebugState.nextAttemptId()
        val url = extractFirstHttpUrl(sharedText)
        if (url == null) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = null,
                tokenPresent = false,
                destination = "unknown",
                requestUrls = emptyList(),
                result = ShareSaveResult.NoValidUrl,
            )
            return ShareSaveResult.NoValidUrl
        }

        val current = settingsStore.settingsFlow.first()
        val destinationPlaylistId = destinationPlaylistIdOverride ?: current.defaultSavePlaylistId
        val destination = destinationLabel(destinationPlaylistId)
        val requestUrls = buildRequestUrls(current.baseUrl, destinationPlaylistId)
        recordSnapshot(
            attemptId = attemptId,
            baseUrl = current.baseUrl,
            tokenPresent = current.apiToken.isNotBlank(),
            destination = destination,
            requestUrls = requestUrls,
            result = ShareSaveResult.SaveFailed,
            phase = "started",
        )

        if (current.apiToken.isBlank()) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = false,
                destination = destination,
                requestUrls = requestUrls,
                result = ShareSaveResult.MissingToken,
            )
            return ShareSaveResult.MissingToken
        }
        if (isClearlyOffline()) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = ShareSaveResult.NetworkError,
                phase = "offline_short_circuit",
            )
            return ShareSaveResult.NetworkError
        }

        return try {
            val article = withTimeoutOrNull(SHARE_ONLINE_DEADLINE_MS) {
                apiClient.createItem(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    url = url,
                    idempotencyKey = buildShareIdempotencyKey(url),
                    title = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                    timeoutMs = SHARE_CREATE_TIMEOUT_MS,
                )
            }
            if (article == null) {
                recordSnapshot(
                    attemptId = attemptId,
                    baseUrl = current.baseUrl,
                    tokenPresent = true,
                    destination = destination,
                    requestUrls = requestUrls,
                    result = ShareSaveResult.TimedOut,
                    phase = "deadline_timeout",
                )
                return ShareSaveResult.TimedOut
            }
            val result = completeSavedItem(
                itemId = article.id,
                itemTitle = article.title,
                current = current,
                destinationPlaylistId = destinationPlaylistId,
                attemptId = attemptId,
            )
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                autoDownloadEnabled = current.autoDownloadSavedArticles,
            )
            result
        } catch (error: ApiException) {
            val result = when {
                error.statusCode == 401 -> ShareSaveResult.Unauthorized
                error.statusCode == 403 -> ShareSaveResult.Unauthorized
                isAuthLikeApiFailure(error) -> ShareSaveResult.Unauthorized
                error.statusCode == 409 -> resolveDuplicateSaveResult(
                    current = current,
                    url = url,
                    destinationPlaylistId = destinationPlaylistId,
                    preferredTitle = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                    attemptId = attemptId,
                )
                error.statusCode in 500..599 -> ShareSaveResult.ServerError
                else -> ShareSaveResult.SaveFailed
            }
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                error = error,
                phase = "api_exception",
            )
            result
        } catch (error: Exception) {
            val baseResult = when {
                isTimeoutError(error) -> ShareSaveResult.TimedOut
                isNetworkError(error) -> ShareSaveResult.NetworkError
                else -> ShareSaveResult.SaveFailed
            }
            val recoveredResult = resolveLateShareSaveSuccess(
                baseResult = baseResult,
                current = current,
                url = url,
                destinationPlaylistId = destinationPlaylistId,
                preferredTitle = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                attemptId = attemptId,
            )
            val result = recoveredResult ?: baseResult
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                error = error,
                phase = if (recoveredResult != null) "exception_recovered" else "exception",
            )
            result
        }
    }

    suspend fun saveManualText(
        urlInput: String,
        titleInput: String?,
        bodyInput: String,
        destinationPlaylistIdOverride: Int? = null,
    ): ShareSaveResult {
        val attemptId = ShareSaveDebugState.nextAttemptId()
        val normalizedUrl = extractFirstHttpUrl(urlInput)
        if (normalizedUrl == null) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = null,
                tokenPresent = false,
                destination = "unknown",
                requestUrls = emptyList(),
                result = ShareSaveResult.NoValidUrl,
            )
            return ShareSaveResult.NoValidUrl
        }

        val normalizedBody = bodyInput.trim()
        if (normalizedBody.isBlank()) {
            return ShareSaveResult.SaveFailed
        }

        val current = settingsStore.settingsFlow.first()
        val destinationPlaylistId = destinationPlaylistIdOverride ?: current.defaultSavePlaylistId
        val destination = destinationLabel(destinationPlaylistId)
        val requestUrls = buildRequestUrls(current.baseUrl, destinationPlaylistId) +
            resolveApiUrl(current.baseUrl, "/items/manual-text")
        recordSnapshot(
            attemptId = attemptId,
            baseUrl = current.baseUrl,
            tokenPresent = current.apiToken.isNotBlank(),
            destination = destination,
            requestUrls = requestUrls,
            result = ShareSaveResult.SaveFailed,
            phase = "started",
        )

        if (current.apiToken.isBlank()) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = false,
                destination = destination,
                requestUrls = requestUrls,
                result = ShareSaveResult.MissingToken,
            )
            return ShareSaveResult.MissingToken
        }
        if (isClearlyOffline()) {
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = ShareSaveResult.NetworkError,
                phase = "offline_short_circuit",
            )
            return ShareSaveResult.NetworkError
        }

        return try {
            val article = withTimeoutOrNull(MANUAL_SAVE_ONLINE_DEADLINE_MS) {
                apiClient.createManualTextItem(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    url = normalizedUrl,
                    text = normalizedBody,
                    title = titleInput?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            if (article == null) {
                recordSnapshot(
                    attemptId = attemptId,
                    baseUrl = current.baseUrl,
                    tokenPresent = true,
                    destination = destination,
                    requestUrls = requestUrls,
                    result = ShareSaveResult.TimedOut,
                    phase = "deadline_timeout",
                )
                return ShareSaveResult.TimedOut
            }
            val result = completeSavedItem(
                itemId = article.id,
                itemTitle = article.title,
                current = current,
                destinationPlaylistId = destinationPlaylistId,
                attemptId = attemptId,
            )
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                autoDownloadEnabled = current.autoDownloadSavedArticles,
            )
            result
        } catch (error: ApiException) {
            val result = when {
                error.statusCode == 401 -> ShareSaveResult.Unauthorized
                error.statusCode == 403 -> ShareSaveResult.Unauthorized
                isAuthLikeApiFailure(error) -> ShareSaveResult.Unauthorized
                error.statusCode == 409 -> resolveDuplicateSaveResult(
                    current = current,
                    url = normalizedUrl,
                    destinationPlaylistId = destinationPlaylistId,
                    preferredTitle = titleInput?.trim()?.takeIf { it.isNotEmpty() },
                    attemptId = attemptId,
                )
                error.statusCode in 500..599 -> ShareSaveResult.ServerError
                else -> ShareSaveResult.SaveFailed
            }
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                error = error,
                phase = "api_exception",
            )
            result
        } catch (error: Exception) {
            val result = when {
                isTimeoutError(error) -> ShareSaveResult.TimedOut
                isNetworkError(error) -> ShareSaveResult.NetworkError
                else -> ShareSaveResult.SaveFailed
            }
            recordSnapshot(
                attemptId = attemptId,
                baseUrl = current.baseUrl,
                tokenPresent = true,
                destination = destination,
                requestUrls = requestUrls,
                result = result,
                error = error,
                phase = "exception",
            )
            result
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
                PlaylistRouteResult(playlistName = playlistName)
            } else {
                throw error
            }
        }
    }

    private suspend fun resolveDestinationName(
        current: AppSettings,
        destinationPlaylistId: Int?,
        knownPlaylistName: String? = null,
    ): String {
        val playlistId = destinationPlaylistId ?: return "Smart Queue"
        val fromRoute = knownPlaylistName?.trim().orEmpty()
        if (fromRoute.isNotEmpty()) {
            return fromRoute
        }
        val resolved = runCatching {
            apiClient.getPlaylists(current.baseUrl, current.apiToken)
                .firstOrNull { it.id == playlistId }
                ?.name
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
        if (resolved.isNotEmpty()) {
            return resolved
        }
        return "Playlist $playlistId"
    }

    private suspend fun resolveDuplicateSaveResult(
        current: AppSettings,
        url: String,
        destinationPlaylistId: Int?,
        preferredTitle: String?,
        attemptId: Int,
    ): ShareSaveResult {
        val existingItemId = resolveExistingItemIdForUrl(
            current = current,
            url = url,
            destinationPlaylistId = destinationPlaylistId,
        )
        if (existingItemId != null) {
            val resolved = runCatching {
                completeSavedItem(
                    itemId = existingItemId,
                    itemTitle = preferredTitle,
                    current = current,
                    destinationPlaylistId = destinationPlaylistId,
                    attemptId = attemptId,
                )
            }.getOrNull()
            if (resolved != null) {
                return resolved
            }
        }

        return ShareSaveResult.Saved(
            destinationName = resolveDestinationName(
                current = current,
                destinationPlaylistId = destinationPlaylistId,
            ),
            itemTitle = preferredTitle,
        )
    }

    private suspend fun resolveLateShareSaveSuccess(
        baseResult: ShareSaveResult,
        current: AppSettings,
        url: String,
        destinationPlaylistId: Int?,
        preferredTitle: String?,
        attemptId: Int,
    ): ShareSaveResult.Saved? {
        if (!shouldAttemptLateShareSaveResolution(baseResult)) {
            return null
        }
        repeat(SHARE_POST_FAILURE_RESOLUTION_ATTEMPTS) { attempt ->
            val existingItemId = resolveExistingItemIdForUrl(
                current = current,
                url = url,
                destinationPlaylistId = destinationPlaylistId,
            )
            if (existingItemId != null) {
                return runCatching {
                    completeSavedItem(
                        itemId = existingItemId,
                        itemTitle = preferredTitle,
                        current = current,
                        destinationPlaylistId = destinationPlaylistId,
                        attemptId = attemptId,
                    )
                }.getOrNull()
            }
            if (attempt < SHARE_POST_FAILURE_RESOLUTION_ATTEMPTS - 1) {
                delay(SHARE_POST_FAILURE_RESOLUTION_DELAY_MS)
            }
        }
        return null
    }

    private suspend fun resolveExistingItemIdForUrl(
        current: AppSettings,
        url: String,
        destinationPlaylistId: Int?,
    ): Int? {
        val targetUrl = url.trim()
        if (targetUrl.isBlank()) return null
        val playlistCandidates = listOf(destinationPlaylistId, null).distinct()
        playlistCandidates.forEach { playlistId ->
            val items = runCatching {
                apiClient.getQueue(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    playlistId = playlistId,
                ).payload.items
            }.getOrNull().orEmpty()
            val match = items.firstOrNull { item ->
                matchesSavedUrl(candidateUrl = item.url, targetUrl = targetUrl)
            }
            if (match != null) {
                return match.itemId
            }
        }
        return null
    }

    private fun matchesSavedUrl(candidateUrl: String, targetUrl: String): Boolean {
        return runCatching {
            buildShareIdempotencyKey(candidateUrl) == buildShareIdempotencyKey(targetUrl)
        }.getOrDefault(candidateUrl.trim() == targetUrl.trim())
    }

    private suspend fun completeSavedItem(
        itemId: Int,
        itemTitle: String?,
        current: AppSettings,
        destinationPlaylistId: Int?,
        attemptId: Int,
    ): ShareSaveResult.Saved {
        val routeResult = routeSavedItem(
            itemId = itemId,
            playlistId = destinationPlaylistId,
            current = current,
        )
        autoDownloadIfEnabledAsync(
            itemId = itemId,
            current = current,
            attemptId = attemptId,
        )
        ShareSaveRefreshBus.events.tryEmit(
            ShareRefreshEvent(
                playlistId = destinationPlaylistId,
                itemId = itemId,
            ),
        )
        return ShareSaveResult.Saved(
            destinationName = resolveDestinationName(
                current = current,
                destinationPlaylistId = destinationPlaylistId,
                knownPlaylistName = routeResult.playlistName,
            ),
            itemTitle = itemTitle,
        )
    }

    private fun buildRequestUrls(baseUrl: String, playlistId: Int?): List<String> {
        val urls = mutableListOf(resolveApiUrl(baseUrl, "/items"))
        if (playlistId != null) {
            urls += resolveApiUrl(baseUrl, "/playlists")
            urls += resolveApiUrl(baseUrl, "/playlists/$playlistId/items")
        }
        return urls
    }

    private fun destinationLabel(playlistId: Int?): String {
        return if (playlistId == null) {
            "Smart Queue"
        } else {
            "Playlist($playlistId)"
        }
    }

    private fun resolveApiUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        return "$cleanBase$cleanPath"
    }

    private fun isTimeoutError(error: Exception): Boolean {
        return rootCause(error) is SocketTimeoutException
    }

    private fun isNetworkError(error: Exception): Boolean {
        return when (val root = rootCause(error)) {
            is ConnectException,
            is UnknownHostException,
            is SocketException,
            is SSLException,
            -> true
            else -> false
        }
    }

    private fun isAuthLikeApiFailure(error: ApiException): Boolean {
        if (error.statusCode == 401 || error.statusCode == 403) return true
        val message = error.message?.lowercase().orEmpty()
        if (message.isBlank()) return false
        return message.contains("unauthorized") ||
            message.contains("forbidden") ||
            message.contains("token") ||
            message.contains("auth")
    }

    private fun rootCause(error: Throwable): Throwable {
        var current: Throwable = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private suspend fun autoDownloadIfEnabled(
        itemId: Int,
        current: AppSettings,
        attemptId: Int,
    ): Boolean? {
        if (!current.autoDownloadSavedArticles) {
            return null
        }
        var lastError: Throwable? = null
        for (attempt in 1..AUTO_DOWNLOAD_MAX_ATTEMPTS) {
            try {
                playbackRepository.getItemText(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    itemId = itemId,
                    expectedActiveVersionId = null,
                )
                return true
            } catch (error: Exception) {
                lastError = error
                if (!shouldRetryAutoDownload(error) || attempt == AUTO_DOWNLOAD_MAX_ATTEMPTS) {
                    break
                }
                delay(AUTO_DOWNLOAD_RETRY_DELAY_MS)
            }
        }

        val root = lastError?.let { rootCause(it) }
        if (BuildConfig.DEBUG) {
            Log.w(
                "MimeoShareSave",
                "attempt=$attemptId autoDownloadFailed itemId=$itemId exceptionClass=${root?.javaClass?.name ?: "none"} exceptionMessage=${root?.message ?: "none"}",
            )
        }
        return false
    }

    private fun shouldRetryAutoDownload(error: Exception): Boolean {
        val root = rootCause(error)
        if (root is SocketTimeoutException || root is ConnectException || root is UnknownHostException) {
            return true
        }
        if (error is ApiException) {
            return error.statusCode in setOf(404, 409, 425, 429, 500, 502, 503, 504)
        }
        return false
    }

    private fun autoDownloadIfEnabledAsync(
        itemId: Int,
        current: AppSettings,
        attemptId: Int,
    ) {
        if (!current.autoDownloadSavedArticles) return
        backgroundScope.launch {
            autoDownloadIfEnabled(
                itemId = itemId,
                current = current,
                attemptId = attemptId,
            )
        }
    }

    private fun isClearlyOffline(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return !(hasInternet && validated)
    }

    private fun recordSnapshot(
        attemptId: Int,
        baseUrl: String?,
        tokenPresent: Boolean,
        destination: String,
        requestUrls: List<String>,
        result: ShareSaveResult,
        error: Throwable? = null,
        phase: String = "done",
        autoDownloadEnabled: Boolean? = null,
        autoDownloadSucceeded: Boolean? = null,
    ) {
        val root = error?.let { rootCause(it) }
        ShareSaveDebugState.record(
            ShareSaveDebugSnapshot(
                attemptId = attemptId,
                phase = phase,
                baseUrl = baseUrl,
                tokenPresent = tokenPresent,
                destination = destination,
                requestUrls = requestUrls,
                apiClientReady = true,
                repositoryReady = null,
                settingsStoreReady = true,
                result = result::class.java.simpleName,
                exceptionClass = root?.javaClass?.name,
                exceptionMessage = root?.message,
                autoDownloadEnabled = autoDownloadEnabled,
                autoDownloadSucceeded = autoDownloadSucceeded,
            )
        )
    }
}

private data class PlaylistRouteResult(
    val playlistName: String? = null,
)

internal data class ShareSaveDebugSnapshot(
    val attemptId: Int,
    val phase: String,
    val baseUrl: String?,
    val tokenPresent: Boolean,
    val destination: String,
    val requestUrls: List<String>,
    val apiClientReady: Boolean,
    val repositoryReady: Boolean?,
    val settingsStoreReady: Boolean,
    val result: String,
    val exceptionClass: String?,
    val exceptionMessage: String?,
    val autoDownloadEnabled: Boolean?,
    val autoDownloadSucceeded: Boolean?,
)

internal object ShareSaveDebugState {
    private const val TAG = "MimeoShareSave"
    private val attemptIds = AtomicInteger(0)

    @Volatile
    var lastSnapshot: ShareSaveDebugSnapshot? = null
        private set

    fun nextAttemptId(): Int = attemptIds.incrementAndGet()

    fun record(snapshot: ShareSaveDebugSnapshot) {
        if (!BuildConfig.DEBUG) return
        lastSnapshot = snapshot
        val requests = if (snapshot.requestUrls.isEmpty()) "none" else snapshot.requestUrls.joinToString(",")
        Log.d(
            TAG,
            "attempt=${snapshot.attemptId} phase=${snapshot.phase} result=${snapshot.result} baseUrl=${snapshot.baseUrl ?: "unset"} tokenPresent=${snapshot.tokenPresent} destination=${snapshot.destination} apiClientReady=${snapshot.apiClientReady} repositoryReady=${snapshot.repositoryReady ?: "n/a"} settingsStoreReady=${snapshot.settingsStoreReady} autoDownloadEnabled=${snapshot.autoDownloadEnabled ?: "n/a"} autoDownloadSucceeded=${snapshot.autoDownloadSucceeded ?: "n/a"} requestUrls=$requests exceptionClass=${snapshot.exceptionClass ?: "none"} exceptionMessage=${snapshot.exceptionMessage ?: "none"}",
        )
    }
}

