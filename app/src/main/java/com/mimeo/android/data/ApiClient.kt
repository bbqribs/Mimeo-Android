package com.mimeo.android.data

import android.util.Log
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.AuthTokenResponse
import com.mimeo.android.model.DebugVersionResponse
import com.mimeo.android.model.DebugPythonResponse
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.ArticleSummary
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaybackQueueResponse
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

class ApiException(val statusCode: Int, message: String) : Exception(message)

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

class ApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
        private const val DEBUG_TARGET_ITEM_ID = 409
        private const val QUEUE_FETCH_LIMIT = 100
    }

    suspend fun getDebugVersion(baseUrl: String, token: String): DebugVersionResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/debug/version"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugVersionResponse>(payload) }
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
            val request = Request.Builder()
                .url(resolveUrl(baseUrl, endpoint))
                .header("Authorization", "Bearer $token")
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
            val request = Request.Builder()
                .url(resolveUrl(baseUrl, endpoint))
                .header("Authorization", "Bearer $token")
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

    suspend fun getQueue(baseUrl: String, token: String, playlistId: Int? = null): QueueFetchResult = withContext(Dispatchers.IO) {
        val playlistParam = playlistId?.let { "&playlist_id=$it" } ?: ""
        val requestUrl = resolveUrl(baseUrl, "/playback/queue?include_done=true&limit=$QUEUE_FETCH_LIMIT$playlistParam")
        val request = Request.Builder()
            .url(requestUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
            }
            val payload = json.decodeFromString<PlaybackQueueResponse>(body)
            val snapshot = QueueFetchDebugSnapshot(
                selectedPlaylistId = playlistId,
                requestUrl = requestUrl,
                statusCode = response.code,
                responseItemCount = payload.items.size,
                responseContains409 = payload.items.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                responseBytes = body.toByteArray().size,
                responseHash = sha256Short(body),
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ArticleSummary>(payload) }
    }

    suspend fun getQueueExplain(baseUrl: String, token: String, itemId: Int): QueueExplainResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playback/queue/explain?item_id=$itemId"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<QueueExplainResponse>(payload) }
    }

    suspend fun getPlaylists(baseUrl: String, token: String): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(PlaylistSummary.serializer()), payload)
        }
    }

    suspend fun createPlaylist(baseUrl: String, token: String, name: String): PlaylistSummary = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PlaylistNamePayload(name)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists"))
            .header("Authorization", "Bearer $token")
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items"))
            .header("Authorization", "Bearer $token")
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
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/manual-text"))
            .header("Authorization", "Bearer $token")
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
            val fallbackRequest = Request.Builder()
                .url(resolveUrl(baseUrl, "/items/manual-text"))
                .header("Authorization", "Bearer $token")
                .post(fallbackBody)
                .build()
            executeJson(fallbackRequest) { responsePayload -> json.decodeFromString<ArticleSummary>(responsePayload) }
        }
    }

    suspend fun renamePlaylist(baseUrl: String, token: String, playlistId: Int, name: String): PlaylistSummary = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PlaylistNamePayload(name)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists/$playlistId"))
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaylistSummary>(payload) }
    }

    suspend fun deletePlaylist(baseUrl: String, token: String, playlistId: Int) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists/$playlistId"))
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun addItemToPlaylist(baseUrl: String, token: String, playlistId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(PlaylistItemPayload(itemId)).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists/$playlistId/items"))
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        executeNoBody(request)
    }

    suspend fun removeItemFromPlaylist(baseUrl: String, token: String, playlistId: Int, itemId: Int) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists/$playlistId/items/$itemId"))
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun getDebugPython(baseUrl: String, token: String): DebugPythonResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/debug/python"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugPythonResponse>(payload) }
    }

    suspend fun getRawEndpoint(baseUrl: String, token: String, path: String, timeoutMs: Long = 4000): RawHttpResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(resolveUrl(baseUrl, path))
                .header("Authorization", "Bearer $token")
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/text"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ItemTextResponse>(payload) }
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
        val body = json.encodeToString(payload).toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/done?auto_archive=$autoArchiveFlag"))
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody())
            .build()
        executeNoBody(request)
    }

    suspend fun resetItemDone(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/reset"))
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody())
            .build()
        executeNoBody(request)
    }

    suspend fun unarchiveItem(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/unarchive"))
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeNoBody(request)
    }

    suspend fun getTrashedItems(baseUrl: String, token: String, limit: Int = 100): List<ArticleSummary> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items?trashed=true&limit=$limit"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(ArticleSummary.serializer()), payload)
        }
    }

    suspend fun getArchivedItems(baseUrl: String, token: String, limit: Int = 100): List<ArticleSummary> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items?archived=true&limit=$limit"))
            .header("Authorization", "Bearer $token")
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId"))
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        executeNoBody(request)
    }

    suspend fun restoreItemFromBin(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/restore"))
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null, 0, 0))
            .build()
        executeNoBody(request)
    }

    suspend fun purgeItemFromBin(
        baseUrl: String,
        token: String,
        itemId: Int,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/purge"))
            .header("Authorization", "Bearer $token")
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
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/favorite"))
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()
        executeNoBody(request)
    }

    suspend fun postProblemReport(
        baseUrl: String,
        token: String,
        requestPayload: ProblemReportRequest,
    ): ProblemReportResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(requestPayload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/feedback/problem-report"))
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        executeJson(request) { payload -> json.decodeFromString<ProblemReportResponse>(payload) }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        return "$cleanBase$cleanPath"
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

    private fun executeNoBody(request: Request) {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwApiException(response.code, body)
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
