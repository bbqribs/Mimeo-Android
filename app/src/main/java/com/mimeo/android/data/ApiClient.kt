package com.mimeo.android.data

import com.mimeo.android.model.DebugVersionResponse
import com.mimeo.android.model.DebugPythonResponse
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.ProgressPayload
import com.mimeo.android.model.RawHttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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

@Serializable
private data class PlaylistNamePayload(val name: String)

@Serializable
private data class PlaylistItemPayload(@SerialName("item_id") val itemId: Int)

class ApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun getDebugVersion(baseUrl: String, token: String): DebugVersionResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/debug/version"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<DebugVersionResponse>(payload) }
    }

    suspend fun getQueue(baseUrl: String, token: String, playlistId: Int? = null): PlaybackQueueResponse = withContext(Dispatchers.IO) {
        val playlistParam = playlistId?.let { "&playlist_id=$it" } ?: ""
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playback/queue?include_done=true&limit=50$playlistParam"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaybackQueueResponse>(payload) }
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

    suspend fun getPlaylistItems(baseUrl: String, token: String, playlistId: Int): List<PlaybackQueueItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playlists/$playlistId/items"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload ->
            json.decodeFromString(ListSerializer(PlaybackQueueItem.serializer()), payload)
        }
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

    private fun resolveUrl(baseUrl: String, path: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanPath = if (path.startsWith('/')) path else "/$path"
        return "$cleanBase$cleanPath"
    }

    private inline fun <T> executeJson(request: Request, parser: (String) -> T): T {
        okHttpClient.newCall(request).execute().use { response ->
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
}
