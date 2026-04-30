package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
data class BlueskyAccountConnectionResponse(
    val connected: Boolean? = null,
    val handle: String? = null,
    val did: String? = null,
    @SerialName("credential_stored") val credentialStored: Boolean? = null,
    @SerialName("read_only") val readOnly: Boolean? = null,
    val mode: String? = null,
    @SerialName("last_validation_status") val lastValidationStatus: String? = null,
    @SerialName("last_validation_state") val lastValidationState: String? = null,
    @SerialName("validation_state") val validationState: String? = null,
    @SerialName("disconnect_available") val disconnectAvailable: Boolean? = null,
    val message: String? = null,
) {
    val resolvedValidationState: String?
        get() = lastValidationStatus ?: lastValidationState ?: validationState
}

@Serializable
data class BlueskyConnectRequest(
    val handle: String,
    @SerialName("app_password") val appPassword: String,
)

@Serializable
data class BlueskySourceDiagnostic(
    val label: String? = null,
    val handle: String? = null,
    val actor: String? = null,
    val identifier: String? = null,
    val enabled: Boolean? = null,
    @SerialName("type_label") val typeLabel: String? = null,
    @SerialName("poll_interval_minutes") val pollIntervalMinutes: Int? = null,
    @SerialName("next_harvest_at") val nextHarvestAt: String? = null,
    @SerialName("next_due_at") val nextDueAt: String? = null,
    @SerialName("last_attempted_at") val lastAttemptedAt: String? = null,
    @SerialName("last_harvested_at") val lastHarvestedAt: String? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_run_saved") val lastRunSaved: Int? = null,
    @SerialName("last_run_duplicate") val lastRunDuplicate: Int? = null,
    @SerialName("last_run_failed") val lastRunFailed: Int? = null,
    @SerialName("last_error") val lastErrorRaw: JsonElement? = null,
    @SerialName("reconnect_required") val reconnectRequired: Boolean? = null,
    val due: Boolean? = null,
    val hidden: Boolean? = null,
    val archived: Boolean? = null,
) {
    val resolvedName: String
        get() = label
            ?: handle
            ?: actor
            ?: identifier
            ?: "Unknown source"

    val resolvedNextDue: String?
        get() = nextHarvestAt ?: nextDueAt

    val resolvedLastErrorMessage: String?
        get() {
            val raw = lastErrorRaw ?: return null
            return runCatching { raw.jsonPrimitive.content }.getOrNull()
                ?: runCatching { raw.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                ?: runCatching { raw.toString() }.getOrNull()
        }
}

@Serializable
data class BlueskyOperatorStatusResponse(
    @SerialName("scheduler_enabled") val schedulerEnabled: Boolean? = null,
    val enabled: Boolean? = null,
    val state: String? = null,
    @SerialName("enabled_source_count") val enabledSourceCount: Int? = null,
    @SerialName("due_source_count") val dueSourceCount: Int? = null,
    @SerialName("next_due_at") val nextDueAt: String? = null,
    @SerialName("next_due_time") val nextDueTime: String? = null,
    @SerialName("last_run_status") val lastRunStatus: String? = null,
    @SerialName("last_error") val lastErrorRaw: JsonElement? = null,
    val sources: List<BlueskySourceDiagnostic> = emptyList(),
) {
    val resolvedSchedulerEnabled: Boolean?
        get() = schedulerEnabled ?: enabled

    val resolvedNextDue: String?
        get() = nextDueAt ?: nextDueTime

    val resolvedLastErrorMessage: String?
        get() {
            val raw = lastErrorRaw ?: return null
            return runCatching { raw.jsonPrimitive.content }.getOrNull()
                ?: runCatching { raw.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                ?: runCatching { raw.toString() }.getOrNull()
        }
}

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
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<PlaybackQueueItem>,
)

@Serializable
data class PlaylistSummary(
    val id: Int,
    val name: String,
    val kind: String = "manual",
    val entries: List<PlaylistEntrySummary> = emptyList(),
)

@Serializable
data class SmartPlaylistSummary(
    val id: Int,
    val name: String,
    val kind: String = "smart",
    @SerialName("filter_definition") val filterDefinition: JsonObject = JsonObject(emptyMap()),
    val sort: String = "saved_desc",
    @SerialName("pin_count") val pinCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

typealias SmartPlaylistDetail = SmartPlaylistSummary

@Serializable
data class SmartPlaylistFilterDefinition(
    val keyword: String? = null,
    @SerialName("source_labels") val sourceLabels: List<String>? = null,
    val domains: List<String>? = null,
    @SerialName("capture_kinds") val captureKinds: List<String>? = null,
    @SerialName("saved_after") val savedAfter: String? = null,
    @SerialName("saved_before") val savedBefore: String? = null,
    @SerialName("date_window") val dateWindow: String? = null,
    @SerialName("include_archived") val includeArchived: String = "false",
    @SerialName("favorites_only") val favoritesOnly: Boolean = false,
    @SerialName("read_status") val readStatus: String = "any",
)

@Serializable
data class SmartPlaylistWriteRequest(
    val name: String? = null,
    @SerialName("filter_definition") val filterDefinition: SmartPlaylistFilterDefinition? = null,
    val sort: String? = null,
)

@Serializable
data class SmartPlaylistPinRequest(
    @SerialName("article_id") val articleId: Int,
    val position: Int? = null,
)

@Serializable
data class SmartPlaylistPinReorderItem(
    @SerialName("article_id") val articleId: Int,
    val position: Int,
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
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("capture_kind") val captureKind: String? = null,
    @SerialName("source_app_package") val sourceAppPackage: String? = null,
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
    @SerialName("is_favorited") val isFavorited: Boolean = false,
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
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("capture_kind") val captureKind: String? = null,
    @SerialName("source_app_package") val sourceAppPackage: String? = null,
    val status: String? = null,
    @SerialName("active_content_version_id") val activeContentVersionId: Int? = null,
    @SerialName("strategy_used") val strategyUsed: String? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    val text: String,
    val paragraphs: List<String>? = null,
    @SerialName("total_chars") val totalChars: Int? = null,
    val chunks: List<ItemTextChunk>? = null,
    @SerialName("content_blocks") val contentBlocks: List<ItemTextContentBlock>? = null,
)

@Serializable
data class ItemTextChunk(
    val index: Int,
    @SerialName("start_char") val startChar: Int,
    @SerialName("end_char") val endChar: Int,
    val text: String,
)

@Serializable
data class ItemTextContentBlock(
    val type: String,
    val text: String? = null,
    val links: List<ItemTextContentLink>? = null,
)

@Serializable
data class ItemTextContentLink(
    val text: String? = null,
    val href: String? = null,
    val start: Int? = null,
    val end: Int? = null,
)

@Serializable
data class ProgressPayload(
    val percent: Int,
    val source: String? = null,
    @SerialName("client_timestamp") val clientTimestamp: String? = null,
    @SerialName("chunk_index") val chunkIndex: Int? = null,
    @SerialName("offset_in_chunk_chars") val offsetInChunkChars: Int? = null,
    @SerialName("reader_scroll_offset") val readerScrollOffset: Int? = null,
)

@Serializable
data class ArticleSummary(
    val id: Int,
    val url: String,
    @SerialName("canonical_url") val canonicalUrl: String? = null,
    val title: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("capture_kind") val captureKind: String? = null,
    @SerialName("source_app_package") val sourceAppPackage: String? = null,
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
enum class DrawerPanelSide {
    LEFT,
    RIGHT,
}

@Serializable
enum class ConnectionMode {
    LOCAL,
    LAN,
    REMOTE,
}

@Serializable
enum class LocusContentMode {
    FULL_TEXT,
    FULL_TEXT_WITH_PLAYER,
    PLAYBACK_FOCUSED,
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
    val baseUrl: String = DEFAULT_REMOTE_BASE_URL,
    val connectionMode: ConnectionMode = ConnectionMode.REMOTE,
    val localBaseUrl: String = DEFAULT_LOCAL_BASE_URL,
    val lanBaseUrl: String = DEFAULT_LAN_BASE_URL,
    val remoteBaseUrl: String = DEFAULT_REMOTE_BASE_URL,
    val apiToken: String = "",
    val autoAdvanceOnCompletion: Boolean = true,
    val autoArchiveAtArticleEnd: Boolean = false,
    val speakTitleBeforeArticle: Boolean = true,
    val skipDuplicateOpeningAfterTitleIntro: Boolean = true,
    val playCompletionCueAtArticleEnd: Boolean = true,
    val keepScreenOnDuringSession: Boolean = true,
    val persistentPlayerEnabled: Boolean = true,
    val autoScrollWhileListening: Boolean = true,
    val locusTabReturnsToPlaybackPosition: Boolean = false,
    val locusContentMode: LocusContentMode = LocusContentMode.FULL_TEXT_WITH_PLAYER,
    val continuousNowPlayingMarquee: Boolean = true,
    val forceSentenceHighlightFallback: Boolean = false,
    val showPlaybackDiagnostics: Boolean = false,
    val showAutoDownloadDiagnostics: Boolean = false,
    val showQueueCaptureMetadata: Boolean = false,
    val showPendingOutcomeSimulator: Boolean = false,
    val ttsVoiceName: String = "",
    val keepShareResultNotifications: Boolean = false,
    val autoDownloadSavedArticles: Boolean = true,
    val autoCacheFavoritedItems: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val selectedPlaylistId: Int? = null,
    val defaultSavePlaylistId: Int? = null,
    val readingFontSizeSp: Int = 16,
    val readingFontOption: ReaderFontOption = ReaderFontOption.SANS_SERIF,
    val readingLineHeightPercent: Int = 160,
    val readingMaxWidthDp: Int = 720,
    val readingParagraphSpacing: ParagraphSpacingOption = ParagraphSpacingOption.MEDIUM,
    val playerControlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerLastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerChevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.HOME,
    val playerChevronEdgeOffset: Float = 0.5f,
    val drawerPanelSide: DrawerPanelSide = DrawerPanelSide.LEFT,
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
enum class PendingItemActionType {
    SET_FAVORITE,
    ARCHIVE,
    UNARCHIVE,
    MOVE_TO_BIN,
    RESTORE_FROM_BIN,
    PURGE_FROM_BIN,
}

@Serializable
data class PendingItemAction(
    val id: Long,
    val itemId: Int,
    val actionType: PendingItemActionType,
    val favorited: Boolean? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
)

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
