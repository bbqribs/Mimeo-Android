package com.mimeo.android.data

import com.mimeo.android.model.DebugVersionResponse
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.ProgressPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val statusCode: Int, message: String) : Exception(message)

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

    suspend fun getQueue(baseUrl: String, token: String): PlaybackQueueResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/playback/queue"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<PlaybackQueueResponse>(payload) }
    }

    suspend fun getItemText(baseUrl: String, token: String, itemId: Int): ItemTextResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolveUrl(baseUrl, "/items/$itemId/text"))
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        executeJson(request) { payload -> json.decodeFromString<ItemTextResponse>(payload) }
    }

    suspend fun postProgress(baseUrl: String, token: String, itemId: Int, percent: Int) = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ProgressPayload(percent)).toRequestBody("application/json".toMediaType())
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
