package com.mimeo.android.data

import android.util.Log
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.DebugVersionResponse
import com.mimeo.android.model.DebugPythonResponse
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.ArticleSummary
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.ProgressPayload
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.RawHttpResponse
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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

class ApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
        private const val DEBUG_TARGET_ITEM_ID = 409
    }

    suspend fun getDebugVersion(baseUrl: String, token: String): DebugVersionResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/debug/version"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugVersionResponse>(payload) }
    }

    suspend fun getQueue(baseUrl: String, token: String, playlistId: Int? = null): QueueFetchResult = withContext(Dispatchers.IO) {
        val playlistParam = playlistId?.let { "&playlist_id=$it" } ?: ""
        val requestUrl = resolveUrl(baseUrl, "/playback/queue?include_done=true&limit=50$playlistParam")
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
    ) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            ProgressPayload(
                percent = percent,
                source = source,
                clientTimestamp = clientTimestamp,
            )
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/progress"))
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        executeNoBody(request)
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
        val message = when (statusCode) {
            401 -> "Unauthorized-check token"
            403 -> "Forbidden"
            else -> if (body.isNotBlank()) "HTTP $statusCode: $body" else "HTTP $statusCode"
        }
        throw ApiException(statusCode, message)
    }

    private fun sha256Short(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
}
