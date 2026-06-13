package com.mimeo.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

enum class ReaderFontOption {
    LITERATA,
    SERIF,
    SANS_SERIF,
    MONOSPACE,
}

enum class VisualThemePreference {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

enum class VisualDensityPreference {
    DEFAULT,
    COMPACT,
}

enum class AccentSchemePreference {
    EMBER,
    LILAC,
    FOREST,
    SLATE,
}

const val DEFAULT_VISUAL_DESIGN_V1_ENABLED = true

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
    @SerialName("active_scope_limit") val activeScopeLimit: Int? = null,
    @SerialName("reorder_allowed") val reorderAllowed: Boolean = false,
    @SerialName("reorder_unavailable_reason") val reorderUnavailableReason: String? = null,
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
    @SerialName("smart_queue_position") val smartQueuePosition: Double? = null,
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
data class ContentSummaryOut(
    @SerialName("item_id") val itemId: Int,
    @SerialName("active_content_version_id") val activeContentVersionId: Int? = null,
    @SerialName("summary_content_version_id") val summaryContentVersionId: Int? = null,
    val state: String,
    @SerialName("summary_kind") val summaryKind: String = "abstract",
    @SerialName("summary_text") val summaryText: String? = null,
    val provider: String? = null,
    val model: String? = null,
    @SerialName("prompt_version") val promptVersion: String? = null,
    @SerialName("current_prompt_version") val currentPromptVersion: String? = null,
    @SerialName("is_current_prompt_version") val isCurrentPromptVersion: Boolean? = null,
    @SerialName("can_refresh") val canRefresh: Boolean = false,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("can_request") val canRequest: Boolean = false,
    val disclaimer: String? = null,
)

@Serializable
data class ContentSummaryRequest(
    val force: Boolean = false,
)

enum class ContentSummaryState {
    MISSING,
    PENDING,
    READY,
    FAILED,
    STALE,
    UNKNOWN,
}

enum class ContentSummaryFailureReason {
    SUMMARIES_DISABLED,
    PROVIDER_NOT_CONFIGURED,
    NO_ACTIVE_CONTENT,
    CONTENT_TOO_SHORT,
    DAILY_LIMIT_REACHED,
    UNAUTHORIZED,
    NOT_FOUND,
    NETWORK,
    UNKNOWN,
}

sealed class ReaderSummaryState {
    object Idle : ReaderSummaryState()
    data class Loading(val itemId: Int, val previous: ContentSummaryOut? = null) : ReaderSummaryState()
    data class Ready(val itemId: Int, val summary: ContentSummaryOut) : ReaderSummaryState()
    data class Error(
        val itemId: Int,
        val reason: ContentSummaryFailureReason,
        val message: String,
    ) : ReaderSummaryState()
}

fun ContentSummaryOut.normalizedState(): ContentSummaryState = when (state.lowercase()) {
    "missing" -> ContentSummaryState.MISSING
    "pending" -> ContentSummaryState.PENDING
    "ready" -> ContentSummaryState.READY
    "failed" -> ContentSummaryState.FAILED
    "stale" -> ContentSummaryState.STALE
    else -> ContentSummaryState.UNKNOWN
}

fun ContentSummaryOut.canRequestGeneration(): Boolean {
    if (!canRequest) return false
    return normalizedState() in setOf(
        ContentSummaryState.MISSING,
        ContentSummaryState.FAILED,
        ContentSummaryState.STALE,
    )
}

/**
 * True when a ready summary was generated with an older prompt version and the
 * backend permits a forced refresh. Drives the soft "Refresh summary" affordance
 * shown alongside the existing summary text — refresh is never automatic.
 */
fun ContentSummaryOut.canRefreshOutdatedSummary(): Boolean {
    if (!canRefresh) return false
    if (normalizedState() != ContentSummaryState.READY) return false
    return isCurrentPromptVersion == false
}

fun contentSummaryFailureReasonFromCode(code: String?): ContentSummaryFailureReason? = when (code?.trim()?.lowercase()) {
    "summaries_disabled" -> ContentSummaryFailureReason.SUMMARIES_DISABLED
    "provider_not_configured" -> ContentSummaryFailureReason.PROVIDER_NOT_CONFIGURED
    "no_active_content" -> ContentSummaryFailureReason.NO_ACTIVE_CONTENT
    "content_too_short" -> ContentSummaryFailureReason.CONTENT_TOO_SHORT
    "daily_limit_reached" -> ContentSummaryFailureReason.DAILY_LIMIT_REACHED
    else -> null
}

fun contentSummaryFailureReasonFromApiMessage(message: String?): ContentSummaryFailureReason? {
    val raw = message.orEmpty()
    val bodyStart = raw.indexOf('{')
    if (bodyStart < 0) return null
    return runCatching {
        val root = Json.parseToJsonElement(raw.substring(bodyStart)).jsonObject
        val detail = root["detail"]
        val reason = when (detail) {
            is JsonObject -> detail["reason"]?.jsonPrimitive?.contentOrNull
            else -> root["reason"]?.jsonPrimitive?.contentOrNull
        }
        contentSummaryFailureReasonFromCode(reason)
    }.getOrNull()
}

fun contentSummaryFailureMessage(reason: ContentSummaryFailureReason): String = when (reason) {
    ContentSummaryFailureReason.SUMMARIES_DISABLED -> "Summaries are not available right now."
    ContentSummaryFailureReason.PROVIDER_NOT_CONFIGURED -> "Summaries are temporarily unavailable."
    ContentSummaryFailureReason.NO_ACTIVE_CONTENT -> "This item does not have readable text to summarize."
    ContentSummaryFailureReason.CONTENT_TOO_SHORT -> "This item is too short to summarize."
    ContentSummaryFailureReason.DAILY_LIMIT_REACHED -> "Summary requests are paused for today. Try again later."
    ContentSummaryFailureReason.UNAUTHORIZED -> "Sign in with a device token that can request summaries."
    ContentSummaryFailureReason.NOT_FOUND -> "This item could not be found."
    ContentSummaryFailureReason.NETWORK -> "Couldn't reach the server. Check connection and try again."
    ContentSummaryFailureReason.UNKNOWN -> "Couldn't load the summary. Try again."
}

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

/**
 * Which library/queue screen the app opens to from a cold start. Mapped to a navigation
 * route in the app shell; [UP_NEXT] preserves the historical default destination.
 */
@Serializable
enum class StartupDestination {
    INBOX,
    UP_NEXT,
    SMART_QUEUE,
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
    val playbackSpeedPresets: List<Float> = DEFAULT_PLAYBACK_SPEED_PRESETS,
    val selectedPlaylistId: Int? = null,
    val defaultSavePlaylistId: Int? = null,
    val readingFontSizeSp: Int = 16,
    val readingFontOption: ReaderFontOption = ReaderFontOption.SANS_SERIF,
    val readingLineHeightPercent: Int = 160,
    val readingMaxWidthDp: Int = 720,
    val readingParagraphSpacing: Float = DEFAULT_PARAGRAPH_SPACING,
    val paragraphSpacingPresets: List<Float> = DEFAULT_PARAGRAPH_SPACING_PRESETS,
    val readingTextAlign: ReaderTextAlignOption = ReaderAppearanceDefaults.TEXT_ALIGN,
    val visualThemePreference: VisualThemePreference = VisualThemePreference.FOLLOW_SYSTEM,
    val visualDensityPreference: VisualDensityPreference = VisualDensityPreference.DEFAULT,
    val accentSchemePreference: AccentSchemePreference = AccentSchemePreference.LILAC,
    val visualDesignV1Enabled: Boolean = DEFAULT_VISUAL_DESIGN_V1_ENABLED,
    val playerControlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerLastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    val playerChevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.HOME,
    val playerChevronEdgeOffset: Float = 0.5f,
    val drawerPanelSide: DrawerPanelSide = DrawerPanelSide.LEFT,
    val startupDestination: StartupDestination = StartupDestination.UP_NEXT,
)

/** Default playback speed quick-tap presets shown in the player speed panel. */
val DEFAULT_PLAYBACK_SPEED_PRESETS: List<Float> = listOf(1.0f, 1.25f, 1.4f, 1.75f, 2.0f)

/** Inclusive bounds for a single playback speed preset. */
const val PLAYBACK_SPEED_PRESET_MIN: Float = 0.5f
const val PLAYBACK_SPEED_PRESET_MAX: Float = 4.0f
private val PLAYBACK_SPEED_PRESET_MIN_HUNDREDTHS = (PLAYBACK_SPEED_PRESET_MIN * 100f).roundToInt()
private val PLAYBACK_SPEED_PRESET_MAX_HUNDREDTHS = (PLAYBACK_SPEED_PRESET_MAX * 100f).roundToInt()

/** Number of playback speed preset slots — one editable box per preset in Settings. */
const val MAX_PLAYBACK_SPEED_PRESETS = 5

/**
 * Normalize candidate speeds into a valid preset list: drop non-finite and
 * out-of-range values, round to 0.01x, de-duplicate, sort ascending, and cap the
 * count. May return an empty list when no input value is valid.
 */
fun sanitizePlaybackSpeedPresets(values: List<Float>): List<Float> =
    values
        .filter { it.isFinite() }
        .map { (it * 100f).roundToInt() }
        .filter { it in PLAYBACK_SPEED_PRESET_MIN_HUNDREDTHS..PLAYBACK_SPEED_PRESET_MAX_HUNDREDTHS }
        .distinct()
        .sorted()
        .take(MAX_PLAYBACK_SPEED_PRESETS)
        .map { it / 100f }

/**
 * Parse a stored or user-entered comma-separated speed list. Falls back to
 * [DEFAULT_PLAYBACK_SPEED_PRESETS] when the input is blank or yields no valid
 * value, so a corrupt stored value can never produce an empty player panel.
 */
fun parsePlaybackSpeedPresets(raw: String?): List<Float> {
    if (raw.isNullOrBlank()) return DEFAULT_PLAYBACK_SPEED_PRESETS
    val parsed = raw.split(',').mapNotNull { it.trim().toFloatOrNull() }
    return sanitizePlaybackSpeedPresets(parsed).ifEmpty { DEFAULT_PLAYBACK_SPEED_PRESETS }
}

/**
 * True when [text] is a blank slot or a finite number within the valid preset
 * bounds. Used for per-box validation in the Settings speed-preset editor.
 */
fun isPlaybackSpeedPresetEntryValid(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    val value = trimmed.toFloatOrNull() ?: return false
    return value.isFinite() && value in PLAYBACK_SPEED_PRESET_MIN..PLAYBACK_SPEED_PRESET_MAX
}

/** Format a preset list as a compact comma-separated string, e.g. "1, 1.25, 1.4". */
fun formatPlaybackSpeedPresets(values: List<Float>): String =
    values.joinToString(", ") { formatPlaybackSpeedPresetValue(it) }

/** Format a single preset speed, trimming trailing zeros (1.0 -> "1", 1.40 -> "1.4"). */
fun formatPlaybackSpeedPresetValue(value: Float): String {
    val hundredths = (value * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}

/**
 * Default paragraph-spacing quick-pick presets, expressed as multiples of the
 * reader body line height. Reproduce the historic Small/Medium/Large gaps.
 */
val DEFAULT_PARAGRAPH_SPACING_PRESETS: List<Float> = listOf(0.35f, 1.0f, 2.0f)

/** Default selected paragraph spacing (a multiple of the body line height). */
const val DEFAULT_PARAGRAPH_SPACING: Float = 1.0f

/** Inclusive bounds for a single paragraph-spacing preset. */
const val PARAGRAPH_SPACING_PRESET_MIN: Float = 0.0f
const val PARAGRAPH_SPACING_PRESET_MAX: Float = 4.0f
private val PARAGRAPH_SPACING_PRESET_MIN_HUNDREDTHS = (PARAGRAPH_SPACING_PRESET_MIN * 100f).roundToInt()
private val PARAGRAPH_SPACING_PRESET_MAX_HUNDREDTHS = (PARAGRAPH_SPACING_PRESET_MAX * 100f).roundToInt()

/** Number of paragraph-spacing preset slots — one editable box per preset in Settings. */
const val MAX_PARAGRAPH_SPACING_PRESETS = 5

/** Clamp a paragraph-spacing multiplier into its valid range. */
fun coerceParagraphSpacing(value: Float): Float =
    if (value.isFinite()) {
        value.coerceIn(PARAGRAPH_SPACING_PRESET_MIN, PARAGRAPH_SPACING_PRESET_MAX)
    } else {
        DEFAULT_PARAGRAPH_SPACING
    }

/**
 * Normalize candidate spacings into a valid preset list: drop non-finite and
 * out-of-range values, round to 0.01, de-duplicate, sort ascending, and cap the
 * count. May return an empty list when no input value is valid.
 */
fun sanitizeParagraphSpacingPresets(values: List<Float>): List<Float> =
    values
        .filter { it.isFinite() }
        .map { (it * 100f).roundToInt() }
        .filter { it in PARAGRAPH_SPACING_PRESET_MIN_HUNDREDTHS..PARAGRAPH_SPACING_PRESET_MAX_HUNDREDTHS }
        .distinct()
        .sorted()
        .take(MAX_PARAGRAPH_SPACING_PRESETS)
        .map { it / 100f }

/**
 * Parse a stored or user-entered comma-separated paragraph-spacing list. Falls
 * back to [DEFAULT_PARAGRAPH_SPACING_PRESETS] when the input is blank or yields
 * no valid value, so a corrupt stored value can never empty the reader panel.
 */
fun parseParagraphSpacingPresets(raw: String?): List<Float> {
    if (raw.isNullOrBlank()) return DEFAULT_PARAGRAPH_SPACING_PRESETS
    val parsed = raw.split(',').mapNotNull { it.trim().toFloatOrNull() }
    return sanitizeParagraphSpacingPresets(parsed).ifEmpty { DEFAULT_PARAGRAPH_SPACING_PRESETS }
}

/**
 * True when [text] is a blank slot or a finite number within the valid preset
 * bounds. Used for per-box validation in the Settings paragraph-spacing editor.
 */
fun isParagraphSpacingPresetEntryValid(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return true
    val value = trimmed.toFloatOrNull() ?: return false
    return value.isFinite() && value in PARAGRAPH_SPACING_PRESET_MIN..PARAGRAPH_SPACING_PRESET_MAX
}

/** Format a preset list as a compact comma-separated string, e.g. "0.35, 1, 2". */
fun formatParagraphSpacingPresets(values: List<Float>): String =
    values.joinToString(", ") { formatParagraphSpacingPresetValue(it) }

/** Format a single spacing multiplier, trimming trailing zeros (1.0 -> "1", 0.35 -> "0.35"). */
fun formatParagraphSpacingPresetValue(value: Float): String {
    val hundredths = (value * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}

/**
 * Parse the stored selected paragraph spacing. Accepts the current numeric
 * format and migrates the legacy SMALL/MEDIUM/LARGE enum names to multiples of
 * the body line height.
 */
fun parseStoredParagraphSpacing(raw: String?): Float {
    if (raw.isNullOrBlank()) return DEFAULT_PARAGRAPH_SPACING
    raw.trim().toFloatOrNull()?.let { return coerceParagraphSpacing(it) }
    return when (raw.trim().uppercase()) {
        "SMALL" -> 0.35f
        "MEDIUM" -> 1.0f
        "LARGE" -> 2.0f
        else -> DEFAULT_PARAGRAPH_SPACING
    }
}

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
data class BlueskySourceInfo(
    val id: Int,
    @SerialName("source_type") val sourceType: String,
    val actor: String,
    @SerialName("display_name") val displayName: String? = null,
    val enabled: Boolean = false,
    @SerialName("poll_interval_minutes") val pollIntervalMinutes: Int = 60,
    @SerialName("next_harvest_at") val nextHarvestAt: String? = null,
    @SerialName("last_harvested_at") val lastHarvestedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val resolvedName: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: actor
}

@Serializable
data class BlueskyBrowsePinResponse(
    val id: Int,
    @SerialName("source_id") val sourceId: Int,
    val position: Int,
    @SerialName("created_at") val createdAt: String? = null,
    val source: BlueskySourceInfo? = null,
)

@Serializable
data class BlueskyBrowsePinCreateRequest(
    @SerialName("source_id") val sourceId: Int,
    val position: Int? = null,
)

@Serializable
data class BlueskyProvenance(
    @SerialName("source_id") val sourceId: Int,
    @SerialName("source_label") val sourceLabel: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("post_url") val postUrl: String? = null,
    @SerialName("author_handle") val authorHandle: String? = null,
    @SerialName("post_indexed_at") val postIndexedAt: String? = null,
)

@Serializable
data class BlueskyBrowseItem(
    val id: Int,
    val url: String,
    val title: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String? = null,
    @SerialName("word_count") val wordCount: Int? = null,
    val bluesky: BlueskyProvenance,
)

@Serializable
data class BlueskyBrowseResponse(
    val items: List<BlueskyBrowseItem>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("total_known") val totalKnown: Int? = null,
)

@Serializable
data class BlueskyPickerListItem(
    val uri: String,
    val name: String,
    val description: String? = null,
    @SerialName("item_count") val itemCount: Int? = null,
)

@Serializable
data class BlueskyPickerTimeline(
    val available: Boolean = false,
)

@Serializable
data class BlueskyPickerCaps(
    @SerialName("max_age_hours") val maxAgeHours: Int = 24,
    @SerialName("max_posts") val maxPosts: Int = 30,
    @SerialName("max_links") val maxLinks: Int = 15,
    @SerialName("max_age_hours_ceiling") val maxAgeHoursCeiling: Int = 24,
    @SerialName("max_posts_ceiling") val maxPostsCeiling: Int = 30,
    @SerialName("max_links_ceiling") val maxLinksCeiling: Int = 15,
)

@Serializable
data class BlueskyPickerPinItem(
    @SerialName("source_id") val sourceId: Int,
    val kind: String,
    val handle: String? = null,
    val uri: String? = null,
    @SerialName("display_name") val displayName: String? = null,
) {
    val resolvedLabel: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: handle?.takeIf { it.isNotBlank() }?.let { "@${it.trimStart('@')}" }
            ?: uri?.takeIf { it.isNotBlank() }
            ?: "Pinned source"
}

@Serializable
data class BlueskyPickerFeedItem(
    val uri: String,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    @SerialName("like_count") val likeCount: Int? = null,
    val pinned: Boolean = false,
)

@Serializable
data class BlueskyPickerResponse(
    val connection: BlueskyAccountConnectionResponse,
    val timeline: BlueskyPickerTimeline = BlueskyPickerTimeline(),
    val lists: List<BlueskyPickerListItem> = emptyList(),
    val feeds: List<BlueskyPickerFeedItem> = emptyList(),
    val accounts: List<JsonElement> = emptyList(),
    val caps: BlueskyPickerCaps = BlueskyPickerCaps(),
    val pins: List<BlueskyPickerPinItem> = emptyList(),
)

data class BlueskyCandidateSourceSelection(
    val sourceKind: String,
    val displayLabel: String,
    val actor: String? = null,
    val uri: String? = null,
    val sourceId: Int? = null,
)

@Serializable
data class BlueskyCandidateSource(
    @SerialName("source_type") val sourceType: String,
    val identifier: String? = null,
    @SerialName("display_label") val displayLabel: String,
    @SerialName("source_id") val sourceId: Int? = null,
)

@Serializable
data class BlueskyCandidateScan(
    @SerialName("max_age_hours") val maxAgeHours: Int,
    @SerialName("max_posts") val maxPosts: Int,
    @SerialName("max_links") val maxLinks: Int,
    @SerialName("posts_scanned") val postsScanned: Int,
    @SerialName("posts_skipped_old") val postsSkippedOld: Int,
    @SerialName("stopped_reason") val stoppedReason: String,
)

@Serializable
data class BlueskyCandidatePostContext(
    @SerialName("post_uri") val postUri: String,
    @SerialName("post_url") val postUrl: String? = null,
    @SerialName("author_handle") val authorHandle: String? = null,
    @SerialName("author_display_name") val authorDisplayName: String? = null,
    @SerialName("text_snippet") val textSnippet: String? = null,
    @SerialName("indexed_at") val indexedAt: String? = null,
)

@Serializable
data class BlueskyCandidate(
    @SerialName("article_url") val articleUrl: String,
    @SerialName("normalized_url") val normalizedUrl: String,
    val title: String? = null,
    val domain: String? = null,
    val bluesky: BlueskyCandidatePostContext,
    @SerialName("source_label") val sourceLabel: String,
    @SerialName("source_type") val sourceType: String,
    val saved: Boolean = false,
    @SerialName("saved_state") val savedState: String = "unsaved",
    @SerialName("item_id") val itemId: Int? = null,
    @SerialName("read_link") val readLink: String? = null,
)

@Serializable
data class BlueskyCandidateScanResponse(
    val source: BlueskyCandidateSource,
    val scan: BlueskyCandidateScan,
    val candidates: List<BlueskyCandidate>,
    @SerialName("fetched_at") val fetchedAt: String? = null,
    val live: Boolean = true,
)

@Serializable
data class BlueskyCandidateSaveRequest(
    @SerialName("article_url") val articleUrl: String,
    val title: String? = null,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("post_url") val postUrl: String? = null,
)

@Serializable
data class BlueskyCandidatePinRequest(
    val actor: String? = null,
    val uri: String? = null,
)

@Serializable
data class BlueskyCandidatePinResponse(
    @SerialName("source_id") val sourceId: Int,
    @SerialName("pin_id") val pinId: Int,
)

@Serializable
data class BlueskyScannerPreferences(
    @SerialName("max_age_hours") val maxAgeHours: Int,
    @SerialName("max_posts") val maxPosts: Int,
    @SerialName("max_links") val maxLinks: Int,
    @SerialName("max_age_hours_ceiling") val maxAgeHoursCeiling: Int = 168,
    @SerialName("max_posts_ceiling") val maxPostsCeiling: Int = 100,
    @SerialName("max_links_ceiling") val maxLinksCeiling: Int = 50,
)

@Serializable
data class BlueskyScannerPreferencesPatch(
    @SerialName("max_age_hours") val maxAgeHours: Int? = null,
    @SerialName("max_posts") val maxPosts: Int? = null,
    @SerialName("max_links") val maxLinks: Int? = null,
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
