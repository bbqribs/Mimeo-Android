package com.mimeo.android.bluesky

import android.util.Log
import com.mimeo.android.UiSnackbarMessage
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ArticleSummary
import com.mimeo.android.model.BlueskyCandidate
import com.mimeo.android.model.BlueskyCandidateSaveRequest
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.state.BlueskyStateHolder
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class BlueskyServiceCoordinator(
    private val apiClient: ApiClient,
    private val state: BlueskyStateHolder,
    private val settings: StateFlow<AppSettings>,
    private val scope: CoroutineScope,
    private val snackbarMessages: Channel<UiSnackbarMessage>,
    private val onCandidateSaved: () -> Unit,
) {
    // ── Status / account ─────────────────────────────────────────────────────

    fun refreshBlueskyStatus() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyAccountConnection.value = null
            state._blueskyOperatorStatus.value = null
            state._blueskyStatusError.value = "Token required to load Bluesky status."
            return
        }
        scope.launch {
            state._blueskyStatusLoading.value = true
            state._blueskyStatusError.value = null
            try {
                supervisorScope {
                    val accountDeferred = async {
                        apiClient.getBlueskyAccountConnection(current.baseUrl, current.apiToken)
                    }
                    val operatorDeferred = async {
                        apiClient.getBlueskyOperatorStatus(current.baseUrl, current.apiToken)
                    }
                    state._blueskyAccountConnection.value = accountDeferred.await()
                    state._blueskyOperatorStatus.value = operatorDeferred.await()
                }
            } catch (error: Throwable) {
                state._blueskyStatusError.value = when (error) {
                    is ApiException -> when (error.statusCode) {
                        401 -> "Unauthorized. Check token and try again."
                        403 -> "Forbidden for this account."
                        404 -> "Bluesky status endpoints unavailable on this backend."
                        in 500..599 -> "Backend error while loading Bluesky status."
                        else -> userFacingRequestErrorMessage(error, "Couldn't load Bluesky status.")
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't load Bluesky status.")
                }
            } finally {
                state._blueskyStatusLoading.value = false
            }
        }
    }

    fun connectBluesky(handle: String, appPassword: String) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyConnectError.value = "Sign in first to connect a Bluesky account."
            return
        }
        scope.launch {
            state._blueskyConnecting.value = true
            state._blueskyConnectError.value = null
            state._blueskyConnectIsReadOnlyScope.value = false
            try {
                val result = apiClient.postBlueskyConnect(current.baseUrl, current.apiToken, handle, appPassword)
                state._blueskyAccountConnection.value = result
                refreshBlueskyStatus()
                snackbarMessages.trySend(UiSnackbarMessage(message = "Bluesky account connected.", actionLabel = null))
            } catch (error: Throwable) {
                val errorMsg = when (error) {
                    is ApiException -> when (error.statusCode) {
                        400, 502 -> apiExceptionDetail(error)
                            ?: "Invalid handle or app password."
                        401 -> "Unauthorized. Check your Mimeo token and try again."
                        403 -> "Your Mimeo token is read-only. Sign out and sign in again, or use a read-write device token."
                        500 -> apiExceptionDetail(error)
                            ?: "Backend error — BLUESKY_SECRET_ENCRYPTION_KEY may not be configured."
                        else -> userFacingRequestErrorMessage(error, "Couldn't connect Bluesky account.")
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't connect Bluesky account.")
                }
                state._blueskyConnectError.value = errorMsg
                if (error is ApiException && error.statusCode == 403) {
                    state._blueskyConnectIsReadOnlyScope.value = true
                }
            } finally {
                state._blueskyConnecting.value = false
            }
        }
    }

    fun disconnectBluesky() {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            state._blueskyDisconnecting.value = true
            try {
                val result = apiClient.postBlueskyDisconnect(current.baseUrl, current.apiToken)
                state._blueskyAccountConnection.value = result
                refreshBlueskyStatus()
                snackbarMessages.trySend(UiSnackbarMessage(message = "Bluesky account disconnected.", actionLabel = null))
            } catch (error: Throwable) {
                snackbarMessages.trySend(
                    UiSnackbarMessage(
                        message = when (error) {
                            is ApiException -> userFacingRequestErrorMessage(error, "Couldn't disconnect Bluesky account.")
                            is IOException -> "Couldn't reach server."
                            else -> userFacingRequestErrorMessage(error, "Couldn't disconnect Bluesky account.")
                        },
                        actionLabel = null,
                    )
                )
            } finally {
                state._blueskyDisconnecting.value = false
            }
        }
    }

    fun resetOnSignOut() {
        state._blueskyAccountConnection.value = null
        state._blueskyOperatorStatus.value = null
        state._blueskyStatusError.value = null
        state._blueskyConnectError.value = null
        state._blueskyConnectIsReadOnlyScope.value = false
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    fun loadBlueskyBrowse(refresh: Boolean = true) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyBrowseError.value = "Token required."
            return
        }
        if (refresh) {
            state._blueskyBrowseNextCursor.value = null
            state._blueskyBrowseItems.value = emptyList()
        }
        scope.launch {
            state._blueskyBrowseLoading.value = true
            state._blueskyBrowseError.value = null
            try {
                supervisorScope {
                    val sourcesDeferred = async { apiClient.getBlueskySources(current.baseUrl, current.apiToken) }
                    val pinsDeferred = async {
                        if (!state._blueskyBrowsePinsAvailable.value) return@async emptyList()
                        try {
                            apiClient.getBlueskyBrowsePins(current.baseUrl, current.apiToken)
                        } catch (e: ApiException) {
                            if (e.statusCode == 404) state._blueskyBrowsePinsAvailable.value = false
                            emptyList()
                        }
                    }
                    val browseDeferred = async {
                        apiClient.getBlueskyBrowse(
                            current.baseUrl,
                            current.apiToken,
                            sourceId = state._blueskyBrowseSourceFilter.value,
                            q = state._blueskyBrowseQuery.value.trim().takeIf { it.isNotEmpty() },
                        )
                    }
                    state._blueskyBrowseSources.value = sourcesDeferred.await()
                    state._blueskyBrowsePins.value = pinsDeferred.await()
                    val browse = browseDeferred.await()
                    state._blueskyBrowseItems.value = browse.items
                    state._blueskyBrowseNextCursor.value = browse.nextCursor
                }
            } catch (error: Throwable) {
                state._blueskyBrowseError.value = when (error) {
                    is ApiException -> when (error.statusCode) {
                        401 -> "Unauthorized."
                        404 -> "Browse endpoint not available on this backend."
                        in 500..599 -> "Backend error while loading Bluesky browse."
                        else -> userFacingRequestErrorMessage(error, "Couldn't load Bluesky browse.")
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't load Bluesky browse.")
                }
            } finally {
                state._blueskyBrowseLoading.value = false
            }
        }
    }

    fun loadMoreBlueskyBrowse() {
        val cursor = state._blueskyBrowseNextCursor.value ?: return
        val current = settings.value
        if (current.apiToken.isBlank() || state._blueskyBrowseLoadingMore.value) return
        scope.launch {
            state._blueskyBrowseLoadingMore.value = true
            try {
                val browse = apiClient.getBlueskyBrowse(
                    current.baseUrl,
                    current.apiToken,
                    sourceId = state._blueskyBrowseSourceFilter.value,
                    q = state._blueskyBrowseQuery.value.trim().takeIf { it.isNotEmpty() },
                    cursor = cursor,
                )
                state._blueskyBrowseItems.value = state._blueskyBrowseItems.value + browse.items
                state._blueskyBrowseNextCursor.value = browse.nextCursor
            } catch (error: Throwable) {
                snackbarMessages.trySend(UiSnackbarMessage(message = "Couldn't load more items.", actionLabel = null))
            } finally {
                state._blueskyBrowseLoadingMore.value = false
            }
        }
    }

    fun setBlueskyBrowseSourceFilter(sourceId: Int?) {
        state._blueskyBrowseSourceFilter.value = sourceId
    }

    fun setBlueskyBrowseQuery(q: String) {
        state._blueskyBrowseQuery.value = q
    }

    fun addBlueskyBrowsePin(sourceId: Int) {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            try {
                val pin = apiClient.addBlueskyBrowsePin(current.baseUrl, current.apiToken, sourceId)
                state._blueskyBrowsePins.value = (state._blueskyBrowsePins.value + pin).sortedBy { it.position }
            } catch (error: Throwable) {
                val msg = if (error is ApiException && error.statusCode == 409) {
                    "Source is already pinned."
                } else {
                    "Couldn't pin source."
                }
                snackbarMessages.trySend(UiSnackbarMessage(message = msg, actionLabel = null))
            }
        }
    }

    fun removeBlueskyBrowsePin(sourceId: Int) {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            try {
                apiClient.removeBlueskyBrowsePinBySource(current.baseUrl, current.apiToken, sourceId)
                state._blueskyBrowsePins.value = state._blueskyBrowsePins.value.filter { it.sourceId != sourceId }
            } catch (error: Throwable) {
                snackbarMessages.trySend(UiSnackbarMessage(message = "Couldn't unpin source.", actionLabel = null))
            }
        }
    }

    // ── Candidate picker ─────────────────────────────────────────────────────

    fun loadBlueskyCandidatePicker() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyCandidatePickerError.value = "Token required to load Bluesky sources."
            return
        }
        scope.launch {
            state._blueskyCandidatePickerLoading.value = true
            state._blueskyCandidatePickerError.value = null
            try {
                state._blueskyCandidatePicker.value = apiClient.getBlueskyPicker(current.baseUrl, current.apiToken)
            } catch (error: Throwable) {
                Log.e("BlueskyServiceCoordinator", "loadBlueskyCandidatePicker: ${error.javaClass.name}: ${error.message}")
                state._blueskyCandidatePickerError.value = when (error) {
                    is ApiException -> when (error.statusCode) {
                        401 -> "Unauthorized. Sign in again."
                        404 -> "Bluesky picker endpoint not available on this backend."
                        in 500..599 -> "Backend error while loading Bluesky sources."
                        else -> blueskyCandidateRequestErrorMessage(error, "Couldn't load Bluesky sources.")
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't load Bluesky sources.")
                }
            } finally {
                state._blueskyCandidatePickerLoading.value = false
            }
        }
    }

    // ── Candidate scan / save / pin ──────────────────────────────────────────

    fun scanBlueskyCandidateSource(selection: BlueskyCandidateSourceSelection) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyCandidateError.value = "Token required to scan Bluesky candidates."
            return
        }
        state._blueskyCandidateSelection.value = selection
        scope.launch {
            state._blueskyCandidateLoading.value = true
            state._blueskyCandidateError.value = null
            state._blueskyCandidateSaveErrors.value = emptyMap()
            try {
                state._blueskyCandidateScan.value = apiClient.getBlueskyCandidates(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    sourceKind = selection.sourceKind,
                    sourceId = selection.sourceId,
                    actor = selection.actor,
                    uri = selection.uri,
                )
            } catch (error: Throwable) {
                state._blueskyCandidateScan.value = null
                state._blueskyCandidateError.value = when (error) {
                    is ApiException -> when (error.statusCode) {
                        401 -> "Unauthorized. Sign in again."
                        404 -> "Bluesky candidate endpoint not available on this backend."
                        409 -> blueskyCandidateRequestErrorMessage(error, "Connect or reconnect Bluesky before scanning this source.")
                        429 -> "Bluesky rate limited the candidate scan. Try again later."
                        502 -> blueskyCandidateRequestErrorMessage(error, "Live Bluesky candidate scan failed.")
                        in 500..599 -> "Backend error while scanning Bluesky candidates."
                        else -> blueskyCandidateRequestErrorMessage(error, "Couldn't scan Bluesky candidates.")
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't scan Bluesky candidates.")
                }
            } finally {
                state._blueskyCandidateLoading.value = false
            }
        }
    }

    fun saveBlueskyCandidate(candidate: BlueskyCandidate) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            state._blueskyCandidateSaveErrors.update { it + (candidate.articleUrl to "Token required.") }
            return
        }
        if (candidate.saved && candidate.itemId != null) return
        scope.launch {
            state._blueskyCandidateSavingUrls.update { it + candidate.articleUrl }
            state._blueskyCandidateSaveErrors.update { it - candidate.articleUrl }
            try {
                val article = apiClient.saveBlueskyCandidate(
                    current.baseUrl,
                    current.apiToken,
                    BlueskyCandidateSaveRequest(
                        articleUrl = candidate.articleUrl,
                        title = candidate.title,
                        sourceType = candidate.sourceType,
                        sourceLabel = candidate.sourceLabel,
                        postUrl = candidate.bluesky.postUrl,
                    ),
                )
                updateSavedBlueskyCandidate(candidate.articleUrl, article)
                snackbarMessages.trySend(UiSnackbarMessage(message = "Saved from Bluesky.", actionLabel = null))
                onCandidateSaved()
            } catch (error: Throwable) {
                val message = when (error) {
                    is ApiException -> blueskyCandidateRequestErrorMessage(error, "Couldn't save candidate.")
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't save candidate.")
                }
                state._blueskyCandidateSaveErrors.update { it + (candidate.articleUrl to message) }
            } finally {
                state._blueskyCandidateSavingUrls.update { it - candidate.articleUrl }
            }
        }
    }

    fun pinCurrentBlueskyCandidateSource() {
        val current = settings.value
        val selection = state._blueskyCandidateSelection.value
        val scanSource = state._blueskyCandidateScan.value?.source
        if (current.apiToken.isBlank() || selection == null) return
        val sourceType = scanSource?.sourceType ?: selection.sourceKind
        val actor = when (sourceType) {
            "author_feed", "account" -> scanSource?.identifier ?: selection.actor
            else -> null
        }
        val uri = if (sourceType == "list_feed") scanSource?.identifier ?: selection.uri else null
        if (actor.isNullOrBlank() && uri.isNullOrBlank()) return
        scope.launch {
            state._blueskyCandidatePinning.value = true
            try {
                val pin = apiClient.pinBlueskyCandidateSource(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    actor = actor,
                    uri = uri,
                )
                state._blueskyCandidateScan.update { response ->
                    response?.copy(source = response.source.copy(sourceId = pin.sourceId))
                }
                loadBlueskyCandidatePicker()
                snackbarMessages.trySend(UiSnackbarMessage(message = "Bluesky source pinned.", actionLabel = null))
            } catch (error: Throwable) {
                val message = when (error) {
                    is ApiException -> {
                        if (error.statusCode == 403 && error.message.orEmpty().contains("user-linked token", ignoreCase = true)) {
                            "Pinning requires a user-linked token. Sign in with a per-device account token; legacy/operator tokens can scan but cannot store Bluesky pins."
                        } else {
                            blueskyCandidateRequestErrorMessage(error, "Couldn't pin Bluesky source.")
                        }
                    }
                    is IOException -> "Couldn't reach server."
                    else -> userFacingRequestErrorMessage(error, "Couldn't pin Bluesky source.")
                }
                snackbarMessages.trySend(UiSnackbarMessage(message = message, actionLabel = null))
            } finally {
                state._blueskyCandidatePinning.value = false
            }
        }
    }

    fun unpinBlueskyCandidateSource(sourceId: Int) {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        scope.launch {
            state._blueskyCandidatePinning.value = true
            try {
                apiClient.removeBlueskyBrowsePinBySource(current.baseUrl, current.apiToken, sourceId)
                state._blueskyCandidatePicker.update { picker ->
                    picker?.copy(pins = picker.pins.filter { it.sourceId != sourceId })
                }
                snackbarMessages.trySend(UiSnackbarMessage(message = "Bluesky source unpinned.", actionLabel = null))
            } catch (error: Throwable) {
                snackbarMessages.trySend(UiSnackbarMessage(message = "Couldn't unpin Bluesky source.", actionLabel = null))
            } finally {
                state._blueskyCandidatePinning.value = false
            }
        }
    }

    private fun updateSavedBlueskyCandidate(articleUrl: String, article: ArticleSummary) {
        state._blueskyCandidateScan.update { response ->
            response?.copy(
                candidates = response.candidates.map { candidate ->
                    if (candidate.articleUrl != articleUrl) {
                        candidate
                    } else {
                        candidate.copy(
                            saved = true,
                            savedState = if (article.status == "failed" || article.status == "blocked") "failed_saved" else "saved",
                            itemId = article.id,
                            readLink = "/items/${article.id}/read",
                            title = article.title ?: candidate.title,
                        )
                    }
                },
            )
        }
    }
}

// ── Error message helpers (private to this file) ──────────────────────────────

private fun userFacingRequestErrorMessage(error: Throwable, fallback: String): String {
    if (error is ApiException) {
        return when {
            error.statusCode == 401 -> "Check your API token"
            error.statusCode >= 500 -> "Server error. Try again."
            !error.message.isNullOrBlank() -> error.message!!
            else -> fallback
        }
    }
    if (error is IOException) {
        return "Couldn't reach server"
    }
    val message = error.message?.trim()
    if (message.isNullOrEmpty()) return fallback
    if (message.contains("java.", ignoreCase = true) || message.length > 180) {
        return fallback
    }
    return message
}

private fun apiExceptionDetail(error: ApiException): String? {
    val body = error.message?.replaceFirst(Regex("^HTTP \\d+:\\s*"), "") ?: return null
    return runCatching {
        Json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun blueskyCandidateRequestErrorMessage(error: ApiException, fallback: String): String {
    val body = error.message?.replaceFirst(Regex("^HTTP \\d+:\\s*"), "") ?: return fallback
    val detailMessage = runCatching {
        val detail = Json.parseToJsonElement(body).jsonObject["detail"]
        detail?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: detail?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }
    return detailMessage ?: userFacingRequestErrorMessage(error, fallback)
}
