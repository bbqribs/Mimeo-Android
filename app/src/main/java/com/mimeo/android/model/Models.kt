package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ConnectivityDiagnosticOutcome {
    PASS,
    FAIL,
    INFO,
}

enum class ProgressSyncBadgeState {
    SYNCED,
    QUEUED,
    OFFLINE,
}

data class ConnectivityDiagnosticRow(
    val name: String,
    val url: String,
    val outcome: ConnectivityDiagnosticOutcome,
    val detail: String,
    val hint: String? = null,
    val checkedAt: String,
)

@Serializable
data class DebugVersionResponse(
    @SerialName("git_sha") val gitSha: String? = null,
    @SerialName("alembic_head") val alembicHead: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class PlaybackQueueResponse(
    val count: Int,
    val items: List<PlaybackQueueItem>,
)

@Serializable
data class PlaybackQueueItem(
    @SerialName("item_id") val itemId: Int,
    val title: String? = null,
    val url: String,
    val host: String? = null,
    val status: String? = null,
    @SerialName("active_content_version_id") val activeContentVersionId: Int? = null,
    @SerialName("strategy_used") val strategyUsed: String? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    @SerialName("last_read_percent") val lastReadPercent: Int? = null,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ItemTextResponse(
    @SerialName("item_id") val itemId: Int,
    val title: String? = null,
    val url: String,
    val host: String? = null,
    val status: String? = null,
    @SerialName("active_content_version_id") val activeContentVersionId: Int? = null,
    @SerialName("strategy_used") val strategyUsed: String? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    val text: String,
    val paragraphs: List<String>? = null,
    @SerialName("total_chars") val totalChars: Int? = null,
    val chunks: List<ItemTextChunk>? = null,
)

@Serializable
data class ItemTextChunk(
    val index: Int,
    @SerialName("start_char") val startChar: Int,
    @SerialName("end_char") val endChar: Int,
    val text: String,
)

@Serializable
data class ProgressPayload(
    val percent: Int,
    val source: String? = null,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
)

@Serializable
data class AppSettings(
    val baseUrl: String = "http://10.0.2.2:8000",
    val apiToken: String = "",
    val autoAdvanceOnCompletion: Boolean = false,
    val autoScrollWhileListening: Boolean = true,
)

@Serializable
data class DebugPythonResponse(
    @SerialName("sys_prefix") val sysPrefix: String? = null,
    @SerialName("sys_executable") val sysExecutable: String? = null,
    @SerialName("sys_base_prefix") val sysBasePrefix: String? = null,
)

data class RawHttpResponse(
    val statusCode: Int,
    val body: String,
)
