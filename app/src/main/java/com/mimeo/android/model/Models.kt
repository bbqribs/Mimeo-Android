package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ParagraphSpacingOption {
    SMALL,
    MEDIUM,
    LARGE,
}

enum class ReaderFontOption {
    LITERATA,
    SERIF,
    SANS_SERIF,
    MONOSPACE,
}

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
data class ConnectionTestSuccessSnapshot(
    val mode: ConnectionMode,
    val baseUrl: String,
    val gitSha: String? = null,
    val succeededAtMs: Long = 0L,
)

@Serializable
data class DebugVersionResponse(
    @SerialName("git_sha") val gitSha: String? = null,
    @SerialName("alembic_head") val alembicHead: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class AuthTokenResponse(
    val token: String,
    val id: Int,
    val name: String,
    val scope: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class PlaybackQueueResponse(
    val count: Int,
    val items: List<PlaybackQueueItem>,
)

@Serializable
data class PlaylistSummary(
    val id: Int,
    val name: String,
    val entries: List<PlaylistEntrySummary> = emptyList(),
)

data class FolderSummary(
    val id: Int,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class PlaylistEntrySummary(
    val id: Int,
    @SerialName("article_id") val articleId: Int,
    val position: Double? = null,
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
    @SerialName("resume_read_percent") val resumeReadPercent: Int? = null,
    @SerialName("last_read_percent") val lastReadPercent: Int? = null,
    @SerialName("progress_percent") val apiProgressPercent: Int? = null,
    @SerialName("furthest_percent") val apiFurthestPercent: Int? = null,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val progressPercent: Int
        get() {
            val parsedProgress = apiProgressPercent ?: resumeReadPercent ?: lastReadPercent ?: 0
            val clampedProgress = parsedProgress.coerceAtLeast(0)
            return minOf(clampedProgress, furthestPercent)
        }

    val furthestPercent: Int
        get() {
            val parsedFurthest = apiFurthestPercent ?: lastReadPercent ?: (apiProgressPercent ?: resumeReadPercent ?: 0)
            return maxOf(parsedFurthest, 0)
        }
}

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
data class ArticleSummary(
    val id: Int,
    val url: String,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    val title: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_opened_at") val lastOpenedAt: String? = null,
    @SerialName("open_count") val openCount: Int = 0,
    @SerialName("last_read_percent") val lastReadPercent: Int? = null,
    @SerialName("resume_read_percent") val resumeReadPercent: Int? = null,
    @SerialName("progress_percent") val progressPercent: Int? = null,
    @SerialName("furthest_percent") val furthestPercent: Int? = null,
    @SerialName("last_read_at") val lastReadAt: String? = null,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("is_favorited") val isFavorited: Boolean = false,
    val status: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("strategy_used") val strategyUsed: String? = null,
    @SerialName("fetch_http_status") val fetchHttpStatus: Int? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    @SerialName("estimated_listen_minutes") val estimatedListenMinutes: Int? = null,
)

@Serializable
enum class PlayerControlsMode {
    FULL,
    MINIMAL,
    NUB,
}

@Serializable
enum class PlayerChevronSnapEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    HOME,
}

@Serializable
enum class ConnectionMode {
    LOCAL,
    LAN,
    REMOTE,
}

enum class AutoDownloadWorkerState {
    IDLE,
    QUEUED,
    RUNNING,
    RETRY_PENDING,
    SUCCEEDED,
    COMPLETED_WITH_FAILURES,
    SKIPPED_ALREADY_CACHED,
    SKIPPED_DISABLED,
    SKIPPED_NO_TOKEN,
}

data class AutoDownloadDiagnostics(
    val autoDownloadEnabled: Boolean = false,
    val queueItemCount: Int = 0,
    val offlineReadyCount: Int = 0,
    val knownNoActiveCount: Int = 0,
    val lastScheduledAtMs: Long? = null,
    val candidateCount: Int = 0,
    val queuedCount: Int = 0,
    val skippedCachedCount: Int = 0,
    val skippedNoActiveCount: Int = 0,
    val includeAllVisibleUncached: Boolean = false,
    val workerState: AutoDownloadWorkerState = AutoDownloadWorkerState.IDLE,
    val workerUpdatedAtMs: Long? = null,
    val attemptedCount: Int = 0,
    val successCount: Int = 0,
    val retryableFailureCount: Int = 0,
    val terminalFailureCount: Int = 0,
    val noActiveContentCount: Int = 0,
)

@Serializable
data class AppSettings(
    val baseUrl: String = "http://10.0.2.2:8000",
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL,
    val localBaseUrl: String = "http://10.0.2.2:8000",
    val lanBaseUrl: String = "",
    val remoteBaseUrl: String = "",
    val apiToken: String = "",
    val autoAdvanceOnCompletion: Boolean = false,
    val speakTitleBeforeArticle: Boolean = false,
    val speakTitleBeforeArticleOnAutoplay: Boolean = false,
    val skipDuplicateOpeningAfterTitleIntro: Boolean = true,
    val playCompletionCueAtArticleEnd: Boolean = false,
    val playCompletionCueOnAutoplay: Boolean = false,
    val persistentPlayerEnabled: Boolean = true,
    val autoScrollWhileListening: Boolean = true,
    val continuousNowPlayingMarquee: Boolean = true,
    val forceSentenceHighlightFallback: Boolean = false,
    val showPlaybackDiagnostics: Boolean = false,
    val showAutoDownloadDiagnostics: Boolean = false,
    val showQueueCaptureMetadata: Boolean = false,
    val ttsVoiceName: String = "",
    val keepShareResultNotifications: Boolean = false,
    val autoDownloadSavedArticles: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val selectedPlaylistId: Int? = null,
    val defaultSavePlaylistId: Int? = null,
    val readingFontSizeSp: Int = 18,
    val readingFontOption: ReaderFontOption = ReaderFontOption.LITERATA,
    val readingLineHeightPercent: Int = 160,
    val readingMaxWidthDp: Int = 720,
    val readingParagraphSpacing: ParagraphSpacingOption = ParagraphSpacingOption.MEDIUM,
    val playerControlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerLastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerChevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.HOME,
    val playerChevronEdgeOffset: Float = 0.5f,
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

data class QueueFetchDebugSnapshot(
    val selectedPlaylistId: Int? = null,
    val requestUrl: String = "",
    val statusCode: Int? = null,
    val responseItemCount: Int = 0,
    val responseContains409: Boolean = false,
    val responseBytes: Int = 0,
    val responseHash: String = "",
    val appliedItemCount: Int = 0,
    val appliedContains409: Boolean = false,
    val lastFetchAt: String = "",
)

@Serializable
enum class PendingManualSaveType {
    URL,
    TEXT,
}

@Serializable
enum class PendingSaveSource {
    MANUAL,
    SHARE,
}

@Serializable
data class PendingManualSaveItem(
    val id: Long,
    val source: PendingSaveSource = PendingSaveSource.MANUAL,
    val type: PendingManualSaveType,
    val urlInput: String,
    val titleInput: String? = null,
    val bodyInput: String? = null,
    val destinationPlaylistId: Int? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastFailureMessage: String,
    val autoRetryEligible: Boolean,
    val resolvedItemId: Int? = null,
)
