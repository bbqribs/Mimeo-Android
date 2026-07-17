package com.mimeo.android.data

import android.util.Log
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.AuthTokenResponse
import com.mimeo.android.model.DebugVersionResponse
import com.mimeo.android.model.DebugPythonResponse
import com.mimeo.android.model.DeviceSession
import com.mimeo.android.model.RevokeDeviceResponse
import com.mimeo.android.model.RevokeOtherDevicesResponse
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.ItemTextContentBlock
import com.mimeo.android.model.ArticleSummary
import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyBrowseItem
import com.mimeo.android.model.BlueskyBrowsePinCreateRequest
import com.mimeo.android.model.BlueskyBrowsePinResponse
import com.mimeo.android.model.BlueskyScannerPreferences
import com.mimeo.android.model.BlueskyScannerPreferencesPatch
import com.mimeo.android.model.BlueskyBrowseResponse
import com.mimeo.android.model.BlueskyCandidatePinRequest
import com.mimeo.android.model.BlueskyCandidatePinResponse
import com.mimeo.android.model.BlueskyCandidateSaveRequest
import com.mimeo.android.model.BlueskyCandidateScanResponse
import com.mimeo.android.model.BlueskyConnectRequest
import com.mimeo.android.model.BlueskyOperatorStatusResponse
import com.mimeo.android.model.BlueskyPickerResponse
import com.mimeo.android.model.BlueskySourceInfo
import com.mimeo.android.model.ContentSummaryOut
import com.mimeo.android.model.ContentSummaryRequest
import com.mimeo.android.model.AiProviderConfigIn
import com.mimeo.android.model.AiProviderConfigStatusOut
import com.mimeo.android.model.AiProviderErrorCode
import com.mimeo.android.model.parseAiProviderErrorCode
import com.mimeo.android.model.SummaryCapabilitiesOut
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.SmartPlaylistDetail
import com.mimeo.android.model.SmartPlaylistPinRequest
import com.mimeo.android.model.SmartPlaylistPinReorderItem
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.model.SmartPlaylistWriteRequest
import com.mimeo.android.model.UpNextConflictResponse
import com.mimeo.android.model.UpNextSession
import com.mimeo.android.model.UpNextSessionClearRequest
import com.mimeo.android.model.UpNextSessionEnvelope
import com.mimeo.android.model.UpNextSessionWriteRequest
import com.mimeo.android.model.ProgressPayload
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.RawHttpResponse
import com.mimeo.android.model.ProblemReportRequest
import com.mimeo.android.model.ProblemReportResponse
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

class ApiException(val statusCode: Int, message: String) : Exception(message)

class UpNextVersionConflictException(
    val currentSession: UpNextSession?,
) : Exception("up_next_version_conflict")

/**
 * BYOAI-A5 — failure of an operator provider write/test/delete, carrying only a
 * coarse, display-safe [code] and the HTTP [statusCode]. The raw response body
 * (which may contain provider errors, ciphertext, or env var names) is parsed
 * into [code] and then discarded; it is deliberately NOT placed on this
 * exception, so nothing downstream — logs, crash breadcrumbs, UI copy — can leak
 * it. The message is the safe enum name only.
 */
class AiProviderConfigException(
    val code: AiProviderErrorCode,
    val statusCode: Int,
) : Exception("ai_provider_error:${code.name}")

data class QueueFetchResult(
    val payload: PlaybackQueueResponse,
    val debugSnapshot: QueueFetchDebugSnapshot,
)

@Serializable
data class QueueExplainResponse(
    val eligible: Boolean? = null,
    @kotlinx.serialization.SerialName("exclusion_reasons")
    val exclusionReasons: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("sort_note")
    val sortNote: String? = null,
)

@Serializable
private data class PlaylistNamePayload(val name: String)

@Serializable
private data class SmartPlaylistFreezePayload(val name: String)

@Serializable
private data class PlaylistItemPayload(
    @kotlinx.serialization.SerialName("item_id") val itemId: Int,
)

@Serializable
private data class CreateItemPayload(
    val url: String,
    val title: String? = null,
    @kotlinx.serialization.SerialName("canonical_url") val canonicalUrl: String? = null,
    @kotlinx.serialization.SerialName("site_name") val siteName: String? = null,
)

@Serializable
private data class ManualTextPayload(
    val url: String,
    val text: String,
    val title: String? = null,
    @kotlinx.serialization.SerialName("site_name") val siteName: String? = null,
    val source: ManualTextSourcePayload? = null,
)

@Serializable
data class ManualTextSourcePayload(
    @kotlinx.serialization.SerialName("source_type") val sourceType: String,
    @kotlinx.serialization.SerialName("source_label") val sourceLabel: String? = null,
    @kotlinx.serialization.SerialName("source_url") val sourceUrl: String? = null,
    @kotlinx.serialization.SerialName("capture_kind") val captureKind: String,
    @kotlinx.serialization.SerialName("source_app_package") val sourceAppPackage: String? = null,
)

@Serializable
private data class AuthTokenPayload(
    val username: String,
    val password: String,
    @kotlinx.serialization.SerialName("device_name") val deviceName: String,
)

@Serializable
private data class ChangePasswordPayload(
    @kotlinx.serialization.SerialName("old_password") val oldPassword: String,
    @kotlinx.serialization.SerialName("new_password") val newPassword: String,
)

@Serializable
private data class FavoriteUpdatePayload(
    val favorited: Boolean,
)

@Serializable
private data class ItemBatchRequest(
    val action: String,
    @kotlinx.serialization.SerialName("item_ids") val itemIds: List<Int>,
)

@Serializable
data class ItemBatchResult(
    @kotlinx.serialization.SerialName("item_id") val itemId: Int,
    val ok: Boolean,
    @kotlinx.serialization.SerialName("status_code") val statusCode: Int,
    val detail: String? = null,
)

@Serializable
data class ItemBatchResponse(
    val action: String,
    @kotlinx.serialization.SerialName("success_count") val successCount: Int,
    @kotlinx.serialization.SerialName("failure_count") val failureCount: Int,
    val results: List<ItemBatchResult>,
)

@Serializable
private data class PlaylistBatchAddRequest(
    @kotlinx.serialization.SerialName("item_ids") val itemIds: List<Int>,
    val insert: String = "end",
)

@Serializable
private data class PlaylistReorderItem(
    @kotlinx.serialization.SerialName("entry_id") val entryId: Int,
    val position: Float,
)

@Serializable
private data class SmartQueueReorderPayload(
    @kotlinx.serialization.SerialName("item_ids") val itemIds: List<Int>,
    val filtered: Boolean,
)

@Serializable
data class PlaylistBatchAddResult(
    @kotlinx.serialization.SerialName("item_id") val itemId: Int,
    val ok: Boolean,
    @kotlinx.serialization.SerialName("status_code") val statusCode: Int,
    val detail: String? = null,
)

@Serializable
data class PlaylistBatchAddResponse(
    @kotlinx.serialization.SerialName("playlist_id") val playlistId: Int,
    val insert: String,
    @kotlinx.serialization.SerialName("success_count") val successCount: Int,
    @kotlinx.serialization.SerialName("failure_count") val failureCount: Int,
    val results: List<PlaylistBatchAddResult>,
)

@Serializable
private data class ItemDetailLitePayload(
    @kotlinx.serialization.SerialName("extracted_content")
    val extractedContent: ExtractedContentLitePayload? = null,
)

@Serializable
private data class ExtractedContentLitePayload(
    @kotlinx.serialization.SerialName("content_blocks")
    val contentBlocks: List<ItemTextContentBlock>? = null,
)

class ApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    enum class ItemsView(val queryValue: String) {
        INBOX("inbox"),
        FAVORITES("favorites"),
        ARCHIVED("archived"),
        TRASH("trash"),
    }

    companion object {
        private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
        private const val DEBUG_TARGET_ITEM_ID = 409
        private const val QUEUE_FETCH_LIMIT = 100
        const val QUEUE_LOAD_MORE_LIMIT = 50
    }

    suspend fun getUpNextSession(baseUrl: String, token: String): UpNextSession? = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/up-next/session", token)
            .acceptJson()
            .get()
            .build()
        executeUpNextJson(request) { payload ->
            json.decodeFromString<UpNextSessionEnvelope>(payload).session
        }
    }

    suspend fun putUpNextSession(
        baseUrl: String,
        token: String,
        payload: UpNextSessionWriteRequest,
    ): UpNextSession = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/up-next/session", token)
            .acceptJson()
            .put(jsonBody(payload))
            .build()
        executeUpNextJson(request) { responseBody ->
            checkNotNull(json.decodeFromString<UpNextSessionEnvelope>(responseBody).session)
        }
    }

    suspend fun clearUpNextSession(
        baseUrl: String,
        token: String,
        expectedVersion: Long,
    ): UpNextSession = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/up-next/session", token)
            .acceptJson()
            .delete(jsonBody(UpNextSessionClearRequest(expectedVersion)))
            .build()
        executeUpNextJson(request) { responseBody ->
            checkNotNull(json.decodeFromString<UpNextSessionEnvelope>(responseBody).session)
        }
    }

    suspend fun getDebugVersion(baseUrl: String, token: String): DebugVersionResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/debug/version", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugVersionResponse>(payload) }
    }

    suspend fun getBlueskyAccountConnection(
        baseUrl: String,
        token: String,
    ): BlueskyAccountConnectionResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/account/connection", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyAccountConnectionResponse>(payload) }
    }

    suspend fun postBlueskyConnect(
        baseUrl: String,
        token: String,
        handle: String,
        appPassword: String,
    ): BlueskyAccountConnectionResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            BlueskyConnectRequest(handle = handle, appPassword = appPassword)
        ).toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/bluesky/account/connection/connect", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyAccountConnectionResponse>(payload) }
    }

    suspend fun postBlueskyDisconnect(
        baseUrl: String,
        token: String,
    ): BlueskyAccountConnectionResponse = withContext(Dispatchers.IO) {
        val body = jsonBody("{}")
        val request = authorizedRequest(baseUrl, "/bluesky/account/connection/disconnect", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyAccountConnectionResponse>(payload) }
    }

    suspend fun getBlueskyOperatorStatus(
        baseUrl: String,
        token: String,
    ): BlueskyOperatorStatusResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/operator/status", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyOperatorStatusResponse>(payload) }
    }

    suspend fun postAuthToken(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): AuthTokenResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            AuthTokenPayload(
                username = username,
                password = password,
                deviceName = deviceName,
            )
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/auth/token"))
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<AuthTokenResponse>(payload) }
    }

    suspend fun postChangePassword(
        baseUrl: String,
        token: String,
        oldPassword: String,
        newPassword: String,
    ) = withContext(Dispatchers.IO) {
        val jsonBody = json.encodeToString(
            ChangePasswordPayload(
                oldPassword = oldPassword,
                newPassword = newPassword,
            ),
        ).toRequestBody("application/json".toMediaType())
        val jsonEndpoints = listOf(
            "/auth/change-password",
            "/auth/change-password/",
            "/auth/change_password",
            "/auth/change_password/",
            "/api/auth/change-password",
            "/api/auth/change-password/",
            "/api/auth/change_password",
            "/api/auth/change_password/",
        )
        var lastError: ApiException? = null
        for (endpoint in jsonEndpoints) {
            val request = authorizedRequest(baseUrl, endpoint, token)
                .post(jsonBody)
                .build()
            try {
                executeNoBody(request)
                return@withContext
            } catch (error: ApiException) {
                lastError = error
                if (error.statusCode != 404) throw error
            }
        }
        val formEndpoints = listOf(
            "/account/change-password",
            "/account/change-password/",
            "/api/account/change-password",
            "/api/account/change-password/",
        )
        for (endpoint in formEndpoints) {
            val formBody = FormBody.Builder()
                .add("old_password", oldPassword)
                .add("new_password", newPassword)
                .add("confirm_new_password", newPassword)
                .build()
            val request = authorizedRequest(baseUrl, endpoint, token)
                .post(formBody)
                .build()
            try {
                executeNoBody(request)
                return@withContext
            } catch (error: ApiException) {
                lastError = error
                if (error.statusCode != 404) throw error
            }
        }
        throw lastError ?: ApiException(500, "Couldn't change password. Please try again.")
    }

    suspend fun getAccountDevices(baseUrl: String, token: String): List<DeviceSession> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/account/devices", token)
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(DeviceSession.serializer()), payload)
        }
    }

    suspend fun postRevokeDevice(baseUrl: String, token: String, deviceId: Int): RevokeDeviceResponse = withContext(Dispatchers.IO) {
        val body = jsonBody("{}")
        val request = authorizedRequest(baseUrl, "/account/devices/$deviceId/revoke", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<RevokeDeviceResponse>(payload) }
    }

    suspend fun postRevokeOtherDevices(baseUrl: String, token: String): RevokeOtherDevicesResponse = withContext(Dispatchers.IO) {
        val body = jsonBody("{}")
        val request = authorizedRequest(baseUrl, "/account/devices/revoke-others", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<RevokeOtherDevicesResponse>(payload) }
    }

    suspend fun getQueue(
        baseUrl: String,
        token: String,
        playlistId: Int? = null,
        offset: Int = 0,
        limit: Int = QUEUE_FETCH_LIMIT,
        sortField: String? = "created",
        sortDir: String? = "desc",
        includeDone: Boolean = true,
    ): QueueFetchResult = withContext(Dispatchers.IO) {
        val queryParts = buildList {
            if (includeDone) add("include_done=true")
            add("limit=$limit")
            add("offset=$offset")
            if (!sortField.isNullOrBlank()) add("sort=$sortField")
            if (!sortDir.isNullOrBlank()) add("dir=$sortDir")
            playlistId?.let { add("playlist_id=$it") }
        }
        val requestUrl = resolveUrl(
            baseUrl,
            "/playback/queue?${queryParts.joinToString("&")}",
        )
        val request = authorizedUrlRequest(requestUrl, token)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
            }
            val payload = json.decodeFromString<PlaybackQueueResponse>(body)
            val responseHash = if (BuildConfig.DEBUG) sha256Short(body) else ""
            val snapshot = QueueFetchDebugSnapshot(
                selectedPlaylistId = playlistId,
                requestUrl = requestUrl,
                statusCode = response.code,
                responseItemCount = payload.items.size,
                responseContains409 = payload.items.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                responseBytes = body.toByteArray().size,
                responseHash = responseHash,
            )
            if (BuildConfig.DEBUG) {
                Log.d(
                    QUEUE_DEBUG_TAG,
                    "requestUrl=$requestUrl playlistId=$playlistId status=${response.code} responseCount=${payload.items.size} contains409=${snapshot.responseContains409} bytes=${snapshot.responseBytes} hash=${snapshot.responseHash}",
                )
            }
            QueueFetchResult(payload = payload, debugSnapshot = snapshot)
        }
    }

    suspend fun getItemSummary(baseUrl: String, token: String, itemId: Int): ArticleSummary = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ArticleSummary>(payload) }
    }

    suspend fun getSummaryCapabilities(
        baseUrl: String,
        token: String,
    ): SummaryCapabilitiesOut = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/summary/capabilities", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<SummaryCapabilitiesOut>(payload) }
    }

    /**
     * BYOAI-A3 — read-only AI provider status enrichment. Calls the read-scope
     * `GET /config/ai-provider` endpoint. Callers must treat any failure
     * (unauthorized, missing endpoint, network) as "no enrichment" and fall back
     * to the capabilities display. This never sends or stores provider config.
     */
    suspend fun getAiProviderStatus(
        baseUrl: String,
        token: String,
    ): AiProviderConfigStatusOut = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/config/ai-provider", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<AiProviderConfigStatusOut>(payload) }
    }

    /**
     * BYOAI-A5 — operator upsert of the AI provider config
     * (`POST /config/ai-provider`). Reachable only with an operator-capable
     * device token; ordinary read/read_write tokens are rejected by the backend.
     *
     * No-secret discipline: the [config] (including its transient `api_key`) is
     * sent once and never retained; failures are mapped to a safe
     * [AiProviderConfigException] code and the raw body is discarded — it is never
     * logged or surfaced. Android never calls an LLM provider directly; this only
     * talks to the Mimeo backend.
     */
    suspend fun saveAiProviderConfig(
        baseUrl: String,
        token: String,
        config: AiProviderConfigIn,
    ): AiProviderConfigStatusOut = withContext(Dispatchers.IO) {
        val body = jsonBody(config)
        val request = authorizedRequest(baseUrl, "/config/ai-provider", token)
            .post(body)
            .build()
        executeProviderActionJson(request) { payload ->
            json.decodeFromString<AiProviderConfigStatusOut>(payload)
        }
    }

    /**
     * BYOAI-A5 — backend-side provider test (`POST /config/ai-provider/test`).
     * The backend performs the provider call; Android never contacts the provider
     * directly. A 409 (no saved config) maps to
     * [AiProviderErrorCode.TestBeforeSave]. Raw provider errors are never surfaced.
     */
    suspend fun testAiProviderConfig(
        baseUrl: String,
        token: String,
    ): AiProviderConfigStatusOut = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/config/ai-provider/test", token)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeProviderActionJson(request) { payload ->
            json.decodeFromString<AiProviderConfigStatusOut>(payload)
        }
    }

    /**
     * BYOAI-A5 — clear the stored provider config (`DELETE /config/ai-provider`).
     * Operator-only. Returns no body; callers re-fetch status afterwards.
     */
    suspend fun deleteAiProviderConfig(
        baseUrl: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/config/ai-provider", token)
            .delete()
            .build()
        executeProviderActionNoBody(request)
    }

    suspend fun getContentSummary(
        baseUrl: String,
        token: String,
        itemId: Int,
        kind: String? = null,
    ): ContentSummaryOut = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/summary${summaryKindQuery(kind)}", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ContentSummaryOut>(payload) }
    }

    suspend fun requestContentSummary(
        baseUrl: String,
        token: String,
        itemId: Int,
        force: Boolean = false,
        kind: String? = null,
    ): ContentSummaryOut = withContext(Dispatchers.IO) {
        // Kind is sent as a query param (backend accepts it on the POST); the body
        // keeps its existing shape so the no-kind path stays byte-for-byte compatible.
        val body = json.encodeToString(ContentSummaryRequest(force = force))
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/items/$itemId/summary${summaryKindQuery(kind)}", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<ContentSummaryOut>(payload) }
    }

    suspend fun getQueueExplain(baseUrl: String, token: String, itemId: Int): QueueExplainResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/playback/queue/explain?item_id=$itemId", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<QueueExplainResponse>(payload) }
    }

    suspend fun getPlaylists(baseUrl: String, token: String): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/playlists", token)
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(PlaylistSummary.serializer()), payload)
        }
    }

    suspend fun getSmartPlaylists(baseUrl: String, token: String): List<SmartPlaylistSummary> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/smart-playlists", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(SmartPlaylistSummary.serializer()), payload)
        }
    }

    suspend fun getSmartPlaylist(baseUrl: String, token: String, playlistId: Int): SmartPlaylistDetail = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<SmartPlaylistDetail>(payload) }
    }

    suspend fun getSmartPlaylistItems(baseUrl: String, token: String, playlistId: Int): List<PlaybackQueueItem> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId/items", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(PlaybackQueueItem.serializer()), payload)
        }
    }

    suspend fun createSmartPlaylist(
        baseUrl: String,
        token: String,
        payload: SmartPlaylistWriteRequest,
    ): SmartPlaylistSummary = withContext(Dispatchers.IO) {
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/smart-playlists", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { responsePayload -> json.decodeFromString<SmartPlaylistSummary>(responsePayload) }
    }

    suspend fun updateSmartPlaylist(
        baseUrl: String,
        token: String,
        playlistId: Int,
        payload: SmartPlaylistWriteRequest,
    ): SmartPlaylistSummary = withContext(Dispatchers.IO) {
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId", token)
            .acceptJson()
            .patch(body)
            .build()
        executeJson(request) { responsePayload -> json.decodeFromString<SmartPlaylistSummary>(responsePayload) }
    }

    suspend fun deleteSmartPlaylist(
        baseUrl: String,
        token: String,
        playlistId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId", token)
            .acceptJson()
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun pinSmartPlaylistItem(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemId: Int,
        position: Int? = null,
    ) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            SmartPlaylistPinRequest(articleId = itemId, position = position),
        ).toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId/pins", token)
            .acceptJson()
            .post(body)
            .build()
        executeNoBody(request)
    }

    suspend fun unpinSmartPlaylistItem(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId/pins/$itemId", token)
            .acceptJson()
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun reorderSmartPlaylistPins(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemIds: List<Int>,
    ) = withContext(Dispatchers.IO) {
        val payload = itemIds.mapIndexed { idx, itemId ->
            SmartPlaylistPinReorderItem(articleId = itemId, position = idx + 1)
        }
        val body = json.encodeToString(ListSerializer(SmartPlaylistPinReorderItem.serializer()), payload)
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId/pins/reorder", token)
            .acceptJson()
            .put(body)
            .build()
        executeNoBody(request)
    }

    suspend fun freezeSmartPlaylist(
        baseUrl: String,
        token: String,
        playlistId: Int,
        name: String? = null,
    ): PlaylistSummary = withContext(Dispatchers.IO) {
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val payload = trimmedName
            ?.let { json.encodeToString(SmartPlaylistFreezePayload(it)) }
            ?: "{}"
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/smart-playlists/$playlistId/freeze", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { responsePayload -> json.decodeFromString<PlaylistSummary>(responsePayload) }
    }

    suspend fun createPlaylist(baseUrl: String, token: String, name: String): PlaylistSummary = withContext(Dispatchers.IO) {
        val body = jsonBody(PlaylistNamePayload(name))
        val request = authorizedRequest(baseUrl, "/playlists", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaylistSummary>(payload) }
    }

    suspend fun createItem(
        baseUrl: String,
        token: String,
        url: String,
        idempotencyKey: String,
        title: String? = null,
        canonicalUrl: String? = null,
        siteName: String? = null,
        timeoutMs: Long? = null,
    ): ArticleSummary = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            CreateItemPayload(
                url = url,
                title = title,
                canonicalUrl = canonicalUrl,
                siteName = siteName,
            )
        ).toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/items", token)
            .header("Idempotency-Key", idempotencyKey)
            .post(body)
            .build()
        val client = if (timeoutMs != null) {
            okHttpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
        } else {
            okHttpClient
        }
        executeJson(request, client = client) { payload -> json.decodeFromString<ArticleSummary>(payload) }
    }

    suspend fun createManualTextItem(
        baseUrl: String,
        token: String,
        url: String,
        text: String,
        title: String? = null,
        siteName: String? = null,
        source: ManualTextSourcePayload? = null,
    ): ArticleSummary = withContext(Dispatchers.IO) {
        val payload = ManualTextPayload(
            url = url,
            text = text,
            title = title,
            siteName = siteName,
            source = source,
        )
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/items/manual-text", token)
            .post(body)
            .build()
        try {
            executeJson(request) { responsePayload -> json.decodeFromString<ArticleSummary>(responsePayload) }
        } catch (error: ApiException) {
            val shouldFallbackWithoutSource = source != null && (error.statusCode == 400 || error.statusCode == 422)
            if (!shouldFallbackWithoutSource) throw error
            val fallbackBody = json.encodeToString(
                ManualTextPayload(
                    url = url,
                    text = text,
                    title = title,
                    siteName = siteName,
                    source = null,
                ),
            ).toRequestBody("application/json".toMediaType())
            val fallbackRequest = authorizedRequest(baseUrl, "/items/manual-text", token)
                .post(fallbackBody)
                .build()
            executeJson(fallbackRequest) { responsePayload -> json.decodeFromString<ArticleSummary>(responsePayload) }
        }
    }

    suspend fun renamePlaylist(baseUrl: String, token: String, playlistId: Int, name: String): PlaylistSummary = withContext(Dispatchers.IO) {
        val body = jsonBody(PlaylistNamePayload(name))
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId", token)
            .patch(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaylistSummary>(payload) }
    }

    suspend fun deletePlaylist(baseUrl: String, token: String, playlistId: Int) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId", token)
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun addItemToPlaylist(baseUrl: String, token: String, playlistId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        val body = jsonBody(PlaylistItemPayload(itemId))
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId/items", token)
            .post(body)
            .build()
        executeNoBody(request)
    }

    suspend fun removeItemFromPlaylist(baseUrl: String, token: String, playlistId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId/items/$itemId", token)
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun batchAddItemsToPlaylist(
        baseUrl: String,
        token: String,
        playlistId: Int,
        itemIds: List<Int>,
    ): PlaylistBatchAddResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PlaylistBatchAddRequest(itemIds = itemIds))
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId/items/batch", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaylistBatchAddResponse>(payload) }
    }

    suspend fun reorderPlaylistEntries(
        baseUrl: String,
        token: String,
        playlistId: Int,
        entryIds: List<Int>,
    ) = withContext(Dispatchers.IO) {
        val payload = entryIds.mapIndexed { idx, entryId ->
            PlaylistReorderItem(entryId = entryId, position = idx.toFloat())
        }
        val body = json.encodeToString(ListSerializer(PlaylistReorderItem.serializer()), payload)
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/playlists/$playlistId/entries/reorder", token)
            .put(body)
            .build()
        executeNoBody(request)
    }

    suspend fun reorderSmartQueue(
        baseUrl: String,
        token: String,
        itemIds: List<Int>,
    ) = withContext(Dispatchers.IO) {
        val payload = SmartQueueReorderPayload(itemIds = itemIds, filtered = false)
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/playback/queue/reorder", token)
            .put(body)
            .build()
        executeNoBody(request)
    }

    suspend fun getDebugPython(baseUrl: String, token: String): DebugPythonResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/debug/python", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugPythonResponse>(payload) }
    }

    suspend fun getRawEndpoint(baseUrl: String, token: String, path: String, timeoutMs: Long = 4000): RawHttpResponse =
        withContext(Dispatchers.IO) {
            val request = authorizedRequest(baseUrl, path, token)
                .get()
                .build()
            val client = okHttpClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                RawHttpResponse(statusCode = response.code, body = response.body?.string().orEmpty())
            }
        }

    suspend fun getItemText(baseUrl: String, token: String, itemId: Int): ItemTextResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/text", token)
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ItemTextResponse>(payload) }
    }

    suspend fun getItemContentBlocks(baseUrl: String, token: String, itemId: Int): List<ItemTextContentBlock>? =
        withContext(Dispatchers.IO) {
            val request = authorizedRequest(baseUrl, "/items/$itemId", token)
                .get()
                .build()
            executeJson(request) { payload ->
                json.decodeFromString<ItemDetailLitePayload>(payload).extractedContent?.contentBlocks
            }
        }

    suspend fun postProgress(
        baseUrl: String,
        token: String,
        itemId: Int,
        percent: Int,
        source: String? = null,
        clientTimestamp: String? = null,
        chunkIndex: Int? = null,
        offsetInChunkChars: Int? = null,
        readerScrollOffset: Int? = null,
    ) = withContext(Dispatchers.IO) {
        val progressUrl = resolveUrl(baseUrl, "/items/$itemId/progress")
        val payload = ProgressPayload(
            percent = percent,
            source = source,
            clientTimestamp = clientTimestamp,
            chunkIndex = chunkIndex,
            offsetInChunkChars = offsetInChunkChars,
            readerScrollOffset = readerScrollOffset,
        )
        try {
            executeNoBody(buildProgressRequest(progressUrl, token, payload))
        } catch (error: ApiException) {
            val shouldRetryLegacy = (error.statusCode == 400 || error.statusCode == 422) &&
                (chunkIndex != null || offsetInChunkChars != null || readerScrollOffset != null)
            if (!shouldRetryLegacy) throw error
            val legacyPayload = ProgressPayload(
                percent = percent,
                source = source,
                clientTimestamp = clientTimestamp,
            )
            executeNoBody(buildProgressRequest(progressUrl, token, legacyPayload))
        }
    }

    private fun buildProgressRequest(
        url: String,
        token: String,
        payload: ProgressPayload,
    ): Request {
        val body = jsonBody(payload)
        return authorizedUrlRequest(url, token)
            .post(body)
            .build()
    }

    suspend fun markItemDone(
        baseUrl: String,
        token: String,
        itemId: Int,
        autoArchive: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val autoArchiveFlag = if (autoArchive) 1 else 0
        val request = authorizedRequest(baseUrl, "/items/$itemId/done?auto_archive=$autoArchiveFlag", token)
            .post(ByteArray(0).toRequestBody())
            .build()
        executeNoBody(request)
    }

    suspend fun resetItemDone(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/reset", token)
            .post(ByteArray(0).toRequestBody())
            .build()
        executeNoBody(request)
    }

    suspend fun unarchiveItem(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/unarchive", token)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeNoBody(request)
    }

    suspend fun getTrashedItems(baseUrl: String, token: String, limit: Int = 100): List<ArticleSummary> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items?trashed=true&limit=$limit", token)
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(ArticleSummary.serializer()), payload)
        }
    }

    suspend fun getArchivedItems(baseUrl: String, token: String, limit: Int = 100): List<ArticleSummary> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items?archived=true&limit=$limit", token)
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(ArticleSummary.serializer()), payload)
        }
    }

    suspend fun getItemsByView(
        baseUrl: String,
        token: String,
        view: ItemsView,
        limit: Int = 100,
        sort: String? = null,
        dir: String? = null,
        q: String? = null,
    ): List<ArticleSummary> = withContext(Dispatchers.IO) {
        val boundedLimit = limit.coerceIn(1, 100)
        val httpUrl = resolveUrl(baseUrl, "/items").toHttpUrl().newBuilder()
            .addQueryParameter("view", view.queryValue)
            .addQueryParameter("limit", boundedLimit.toString())
            .apply {
                if (sort != null) addQueryParameter("sort", sort)
                if (dir != null) addQueryParameter("dir", dir)
                if (!q.isNullOrBlank()) addQueryParameter("q", q)
            }
            .build()
        val request = authorizedUrlRequest(httpUrl, token)
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(ArticleSummary.serializer()), payload)
        }
    }

    suspend fun moveItemToBin(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId", token)
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun restoreItemFromBin(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/restore", token)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeNoBody(request)
    }

    suspend fun purgeItemFromBin(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/items/$itemId/purge", token)
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeNoBody(request)
    }

    suspend fun setFavoriteState(
        baseUrl: String,
        token: String,
        itemId: Int,
        favorited: Boolean,
    ) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            FavoriteUpdatePayload(
                favorited = favorited,
            ),
        ).toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/items/$itemId/favorite", token)
            .put(body)
            .build()
        executeNoBody(request)
    }

    suspend fun batchItemAction(
        baseUrl: String,
        token: String,
        action: String,
        itemIds: List<Int>,
    ): ItemBatchResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ItemBatchRequest(action = action, itemIds = itemIds))
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/items/batch", token)
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<ItemBatchResponse>(payload) }
    }

    suspend fun getBlueskyBrowse(
        baseUrl: String,
        token: String,
        sourceId: Int? = null,
        q: String? = null,
        cursor: String? = null,
        limit: Int = 50,
    ): BlueskyBrowseResponse = withContext(Dispatchers.IO) {
        val httpUrl = resolveUrl(baseUrl, "/bluesky/browse").toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .apply {
                if (sourceId != null) addQueryParameter("source_id", sourceId.toString())
                if (!q.isNullOrBlank()) addQueryParameter("q", q)
                if (cursor != null) addQueryParameter("cursor", cursor)
            }
            .build()
        val request = authorizedUrlRequest(httpUrl, token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyBrowseResponse>(payload) }
    }

    suspend fun getBlueskyPicker(baseUrl: String, token: String): BlueskyPickerResponse = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/picker", token)
            .acceptJson()
            .get()
            .build()
        val client = okHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        executeJson(request, client) { payload -> json.decodeFromString<BlueskyPickerResponse>(payload) }
    }

    suspend fun getBlueskyCandidates(
        baseUrl: String,
        token: String,
        sourceKind: String,
        sourceId: Int? = null,
        actor: String? = null,
        uri: String? = null,
        maxAgeHours: Int? = null,
        maxPosts: Int? = null,
        maxLinks: Int? = null,
    ): BlueskyCandidateScanResponse = withContext(Dispatchers.IO) {
        val httpUrl = resolveUrl(baseUrl, "/bluesky/candidates").toHttpUrl().newBuilder()
            .addQueryParameter("source_kind", sourceKind)
            .apply {
                if (sourceId != null) addQueryParameter("source_id", sourceId.toString())
                if (!actor.isNullOrBlank()) addQueryParameter("actor", actor)
                if (!uri.isNullOrBlank()) addQueryParameter("uri", uri)
                if (maxAgeHours != null) addQueryParameter("max_age_hours", maxAgeHours.toString())
                if (maxPosts != null) addQueryParameter("max_posts", maxPosts.toString())
                if (maxLinks != null) addQueryParameter("max_links", maxLinks.toString())
            }
            .build()
        val request = authorizedUrlRequest(httpUrl, token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyCandidateScanResponse>(payload) }
    }

    suspend fun saveBlueskyCandidate(
        baseUrl: String,
        token: String,
        payload: BlueskyCandidateSaveRequest,
    ): ArticleSummary = withContext(Dispatchers.IO) {
        val body = jsonBody(payload)
        val request = authorizedRequest(baseUrl, "/bluesky/candidates/save", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { responsePayload -> json.decodeFromString<ArticleSummary>(responsePayload) }
    }

    suspend fun pinBlueskyCandidateSource(
        baseUrl: String,
        token: String,
        actor: String? = null,
        uri: String? = null,
    ): BlueskyCandidatePinResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(BlueskyCandidatePinRequest(actor = actor, uri = uri))
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/bluesky/candidates/pin", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyCandidatePinResponse>(payload) }
    }

    suspend fun getBlueskySources(baseUrl: String, token: String): List<BlueskySourceInfo> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/sources", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(BlueskySourceInfo.serializer()), payload)
        }
    }

    suspend fun getBlueskyBrowsePins(baseUrl: String, token: String): List<BlueskyBrowsePinResponse> = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/browse/pins", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(BlueskyBrowsePinResponse.serializer()), payload)
        }
    }

    suspend fun addBlueskyBrowsePin(
        baseUrl: String,
        token: String,
        sourceId: Int,
    ): BlueskyBrowsePinResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(BlueskyBrowsePinCreateRequest(sourceId = sourceId))
            .toRequestBody("application/json".toMediaType())
        val request = authorizedRequest(baseUrl, "/bluesky/browse/pins", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyBrowsePinResponse>(payload) }
    }

    suspend fun removeBlueskyBrowsePinBySource(
        baseUrl: String,
        token: String,
        sourceId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/browse/pins/by-source/$sourceId", token)
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun getBlueskyPreferences(baseUrl: String, token: String): BlueskyScannerPreferences = withContext(Dispatchers.IO) {
        val request = authorizedRequest(baseUrl, "/bluesky/preferences", token)
            .acceptJson()
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyScannerPreferences>(payload) }
    }

    suspend fun patchBlueskyPreferences(
        baseUrl: String,
        token: String,
        patch: BlueskyScannerPreferencesPatch,
    ): BlueskyScannerPreferences = withContext(Dispatchers.IO) {
        val body = jsonBody(patch)
        val request = authorizedRequest(baseUrl, "/bluesky/preferences", token)
            .acceptJson()
            .patch(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<BlueskyScannerPreferences>(payload) }
    }

    suspend fun postProblemReport(
        baseUrl: String,
        token: String,
        requestPayload: ProblemReportRequest,
    ): ProblemReportResponse = withContext(Dispatchers.IO) {
        val body = jsonBody(requestPayload)
        val request = authorizedRequest(baseUrl, "/feedback/problem-report", token)
            .acceptJson()
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<ProblemReportResponse>(payload) }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        return "$cleanBase$cleanPath"
    }

    private fun authorizedRequest(baseUrl: String, path: String, token: String): Request.Builder =
        authorizedUrlRequest(resolveUrl(baseUrl, path), token)

    private fun authorizedUrlRequest(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")

    private fun authorizedUrlRequest(url: HttpUrl, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")

    private fun Request.Builder.acceptJson(): Request.Builder =
        header("Accept", "application/json")

    private fun jsonBody(payload: String) =
        payload.toRequestBody("application/json".toMediaType())

    private inline fun <reified T> jsonBody(payload: T) =
        json.encodeToString(payload).toRequestBody("application/json".toMediaType())

    /**
     * Build the optional `?kind=` query for summary requests. Blank/null kinds
     * yield an empty string so the URL is identical to the legacy no-kind path.
     */
    private fun summaryKindQuery(kind: String?): String {
        val trimmed = kind?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
        return "?kind=$encoded"
    }

    private inline fun <T> executeJson(
        request: Request,
        client: OkHttpClient = okHttpClient,
        parser: (String) -> T,
    ): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
            }
            return parser(body)
        }
    }

    private inline fun <T> executeUpNextJson(
        request: Request,
        parser: (String) -> T,
    ): T {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 409) {
                val conflict = runCatching {
                    json.decodeFromString<UpNextConflictResponse>(body)
                }.getOrNull()
                if (conflict?.error?.code == "up_next_version_conflict") {
                    throw UpNextVersionConflictException(conflict.currentSession)
                }
            }
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
            }
            return parser(body)
        }
    }

    private fun executeNoBody(request: Request) {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
            }
        }
    }

    /**
     * BYOAI-A5 — execute a provider write/test that returns JSON, mapping any
     * non-2xx into a body-free [AiProviderConfigException]. The error body is read
     * only to classify a safe [AiProviderErrorCode] and is then discarded, so the
     * raw provider/backend detail never escapes this method.
     */
    private inline fun <T> executeProviderActionJson(
        request: Request,
        parser: (String) -> T,
    ): T {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiProviderConfigException(
                    code = parseAiProviderErrorCode(response.code, body),
                    statusCode = response.code,
                )
            }
            return parser(body)
        }
    }

    /** BYOAI-A5 — body-free provider action (e.g. DELETE) with safe error mapping. */
    private fun executeProviderActionNoBody(request: Request) {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiProviderConfigException(
                    code = parseAiProviderErrorCode(response.code, body),
                    statusCode = response.code,
                )
            }
        }
    }

    private fun throwApiException(statusCode: Int, body: String): Nothing {
        val message = if (body.isNotBlank()) "HTTP $statusCode: $body" else "HTTP $statusCode"
        throw ApiException(statusCode, message)
    }

    private fun sha256Short(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
