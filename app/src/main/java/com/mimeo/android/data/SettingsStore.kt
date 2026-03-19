package com.mimeo.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PlaybackQueueResponse
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.model.decodeSelectedPlaylistId
import com.mimeo.android.model.encodeSelectedPlaylistId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "mimeo_settings")

class SettingsStore(private val context: Context) {
    private val baseUrlKey: Preferences.Key<String> = stringPreferencesKey("base_url")
    private val connectionModeKey: Preferences.Key<String> = stringPreferencesKey("connection_mode")
    private val localBaseUrlKey: Preferences.Key<String> = stringPreferencesKey("local_base_url")
    private val lanBaseUrlKey: Preferences.Key<String> = stringPreferencesKey("lan_base_url")
    private val remoteBaseUrlKey: Preferences.Key<String> = stringPreferencesKey("remote_base_url")
    private val tokenKey: Preferences.Key<String> = stringPreferencesKey("api_token")
    private val autoAdvanceOnCompletionKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_advance_on_completion")
    private val persistentPlayerEnabledKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("persistent_player_enabled")
    private val autoScrollWhileListeningKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_scroll_while_listening")
    private val continuousNowPlayingMarqueeKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("continuous_now_playing_marquee")
    private val forceSentenceHighlightFallbackKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("force_sentence_highlight_fallback")
    private val showPlaybackDiagnosticsKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("show_playback_diagnostics")
    private val showQueueCaptureMetadataKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("show_queue_capture_metadata")
    private val ttsVoiceNameKey: Preferences.Key<String> =
        stringPreferencesKey("tts_voice_name")
    private val keepShareResultNotificationsKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("keep_share_result_notifications")
    private val autoDownloadSavedArticlesKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_download_saved_articles")
    private val playbackSpeedKey: Preferences.Key<Float> =
        floatPreferencesKey("playback_speed")
    private val selectedPlaylistIdKey: Preferences.Key<Int> =
        intPreferencesKey("selected_playlist_id")
    private val defaultSavePlaylistIdKey: Preferences.Key<Int> =
        intPreferencesKey("default_save_playlist_id")
    private val readingFontSizeSpKey: Preferences.Key<Int> =
        intPreferencesKey("reading_font_size_sp")
    private val readingFontOptionKey: Preferences.Key<String> =
        stringPreferencesKey("reading_font_option")
    private val readingLineHeightPercentKey: Preferences.Key<Int> =
        intPreferencesKey("reading_line_height_percent")
    private val readingMaxWidthDpKey: Preferences.Key<Int> =
        intPreferencesKey("reading_max_width_dp")
    private val readingParagraphSpacingKey: Preferences.Key<String> =
        stringPreferencesKey("reading_paragraph_spacing")
    private val playerControlsModeKey: Preferences.Key<String> =
        stringPreferencesKey("player_controls_mode")
    private val playerLastNonNubModeKey: Preferences.Key<String> =
        stringPreferencesKey("player_last_non_nub_mode")
    private val playerChevronSnapEdgeKey: Preferences.Key<String> =
        stringPreferencesKey("player_chevron_snap_edge")
    private val playerChevronEdgeOffsetKey: Preferences.Key<Float> =
        floatPreferencesKey("player_chevron_edge_offset")
    private val queueSnapshotsJsonKey: Preferences.Key<String> =
        stringPreferencesKey("queue_snapshots_json")
    private val pendingManualSavesJsonKey: Preferences.Key<String> =
        stringPreferencesKey("pending_manual_saves_json")
    private val connectionTestSuccessJsonKey: Preferences.Key<String> =
        stringPreferencesKey("connection_test_success_json")
    private val json = Json { ignoreUnknownKeys = true }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val storedBaseUrl = prefs[baseUrlKey] ?: "http://10.0.2.2:8000"
        val parsedConnectionMode = prefs[connectionModeKey]
            ?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() }
            ?: if (storedBaseUrl.contains("10.0.2.2")) ConnectionMode.LOCAL else ConnectionMode.LAN
        val localBaseUrl = prefs[localBaseUrlKey] ?: if (storedBaseUrl.contains("10.0.2.2")) {
            storedBaseUrl
        } else {
            "http://10.0.2.2:8000"
        }
        val lanBaseUrl = prefs[lanBaseUrlKey] ?: storedBaseUrl
        val remoteBaseUrl = prefs[remoteBaseUrlKey] ?: storedBaseUrl
        AppSettings(
            baseUrl = storedBaseUrl,
            connectionMode = parsedConnectionMode,
            localBaseUrl = localBaseUrl,
            lanBaseUrl = lanBaseUrl,
            remoteBaseUrl = remoteBaseUrl,
            apiToken = prefs[tokenKey] ?: "",
            autoAdvanceOnCompletion = prefs[autoAdvanceOnCompletionKey] ?: false,
            persistentPlayerEnabled = prefs[persistentPlayerEnabledKey] ?: true,
            autoScrollWhileListening = prefs[autoScrollWhileListeningKey] ?: true,
            continuousNowPlayingMarquee = prefs[continuousNowPlayingMarqueeKey] ?: true,
            forceSentenceHighlightFallback = prefs[forceSentenceHighlightFallbackKey] ?: false,
            showPlaybackDiagnostics = prefs[showPlaybackDiagnosticsKey] ?: false,
            showQueueCaptureMetadata = prefs[showQueueCaptureMetadataKey] ?: false,
            ttsVoiceName = prefs[ttsVoiceNameKey] ?: "",
            keepShareResultNotifications = prefs[keepShareResultNotificationsKey] ?: false,
            autoDownloadSavedArticles = prefs[autoDownloadSavedArticlesKey] ?: true,
            playbackSpeed = prefs[playbackSpeedKey] ?: 1.0f,
            selectedPlaylistId = decodeSelectedPlaylistId(prefs[selectedPlaylistIdKey]),
            defaultSavePlaylistId = decodeSelectedPlaylistId(prefs[defaultSavePlaylistIdKey]),
            readingFontSizeSp = prefs[readingFontSizeSpKey] ?: 18,
            readingFontOption = prefs[readingFontOptionKey]
                ?.let { runCatching { ReaderFontOption.valueOf(it) }.getOrNull() }
                ?: ReaderFontOption.LITERATA,
            readingLineHeightPercent = prefs[readingLineHeightPercentKey] ?: 160,
            readingMaxWidthDp = prefs[readingMaxWidthDpKey] ?: 720,
            readingParagraphSpacing = prefs[readingParagraphSpacingKey]
                ?.let { runCatching { ParagraphSpacingOption.valueOf(it) }.getOrNull() }
                ?: ParagraphSpacingOption.MEDIUM,
            playerControlsMode = prefs[playerControlsModeKey]
                ?.let { runCatching { PlayerControlsMode.valueOf(it) }.getOrNull() }
                ?: PlayerControlsMode.FULL,
            playerLastNonNubMode = prefs[playerLastNonNubModeKey]
                ?.let { runCatching { PlayerControlsMode.valueOf(it) }.getOrNull() }
                ?.takeIf { it != PlayerControlsMode.NUB }
                ?: PlayerControlsMode.FULL,
            playerChevronSnapEdge = prefs[playerChevronSnapEdgeKey]
                ?.let { runCatching { PlayerChevronSnapEdge.valueOf(it) }.getOrNull() }
                ?: PlayerChevronSnapEdge.HOME,
            playerChevronEdgeOffset = (prefs[playerChevronEdgeOffsetKey] ?: 0.5f).coerceIn(0f, 1f),
        )
    }

    val pendingManualSavesFlow: Flow<List<PendingManualSaveItem>> = context.dataStore.data.map { prefs ->
        decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
    }

    val connectionTestSuccessFlow: Flow<Map<ConnectionMode, ConnectionTestSuccessSnapshot>> = context.dataStore.data.map { prefs ->
        decodeConnectionTestSuccesses(prefs[connectionTestSuccessJsonKey])
            .associateBy { it.mode }
    }

    suspend fun save(
        baseUrl: String,
        connectionMode: ConnectionMode,
        localBaseUrl: String,
        lanBaseUrl: String,
        remoteBaseUrl: String,
        apiToken: String,
        autoAdvanceOnCompletion: Boolean,
        persistentPlayerEnabled: Boolean,
        autoScrollWhileListening: Boolean,
        continuousNowPlayingMarquee: Boolean,
        forceSentenceHighlightFallback: Boolean,
        showPlaybackDiagnostics: Boolean,
        showQueueCaptureMetadata: Boolean,
        ttsVoiceName: String,
        keepShareResultNotifications: Boolean,
        autoDownloadSavedArticles: Boolean,
        playbackSpeed: Float,
        selectedPlaylistId: Int?,
        defaultSavePlaylistId: Int?,
        readingFontSizeSp: Int,
        readingFontOption: ReaderFontOption,
        readingLineHeightPercent: Int,
        readingMaxWidthDp: Int,
        readingParagraphSpacing: ParagraphSpacingOption,
        playerControlsMode: PlayerControlsMode,
        playerLastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
        playerChevronSnapEdge: PlayerChevronSnapEdge,
        playerChevronEdgeOffset: Float,
    ) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[connectionModeKey] = connectionMode.name
            prefs[localBaseUrlKey] = localBaseUrl.trim()
            prefs[lanBaseUrlKey] = lanBaseUrl.trim()
            prefs[remoteBaseUrlKey] = remoteBaseUrl.trim()
            prefs[tokenKey] = apiToken.trim()
            prefs[autoAdvanceOnCompletionKey] = autoAdvanceOnCompletion
            prefs[persistentPlayerEnabledKey] = persistentPlayerEnabled
            prefs[autoScrollWhileListeningKey] = autoScrollWhileListening
            prefs[continuousNowPlayingMarqueeKey] = continuousNowPlayingMarquee
            prefs[forceSentenceHighlightFallbackKey] = forceSentenceHighlightFallback
            prefs[showPlaybackDiagnosticsKey] = showPlaybackDiagnostics
            prefs[showQueueCaptureMetadataKey] = showQueueCaptureMetadata
            prefs[ttsVoiceNameKey] = ttsVoiceName.trim()
            prefs[keepShareResultNotificationsKey] = keepShareResultNotifications
            prefs[autoDownloadSavedArticlesKey] = autoDownloadSavedArticles
            prefs[playbackSpeedKey] = playbackSpeed
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
            prefs[defaultSavePlaylistIdKey] = encodeSelectedPlaylistId(defaultSavePlaylistId)
            prefs[readingFontSizeSpKey] = readingFontSizeSp
            prefs[readingFontOptionKey] = readingFontOption.name
            prefs[readingLineHeightPercentKey] = readingLineHeightPercent
            prefs[readingMaxWidthDpKey] = readingMaxWidthDp
            prefs[readingParagraphSpacingKey] = readingParagraphSpacing.name
            prefs[playerControlsModeKey] = playerControlsMode.name
            prefs[playerLastNonNubModeKey] = playerLastNonNubMode
                .takeIf { it != PlayerControlsMode.NUB }
                ?.name
                ?: PlayerControlsMode.FULL.name
            prefs[playerChevronSnapEdgeKey] = playerChevronSnapEdge.name
            prefs[playerChevronEdgeOffsetKey] = playerChevronEdgeOffset.coerceIn(0f, 1f)
        }
    }

    suspend fun saveSelectedPlaylistId(selectedPlaylistId: Int?) {
        context.dataStore.edit { prefs ->
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
        }
    }

    suspend fun saveDefaultSavePlaylistId(defaultSavePlaylistId: Int?) {
        context.dataStore.edit { prefs ->
            prefs[defaultSavePlaylistIdKey] = encodeSelectedPlaylistId(defaultSavePlaylistId)
        }
    }

    suspend fun saveReadingPreferences(
        readingFontSizeSp: Int,
        readingFontOption: ReaderFontOption,
        readingLineHeightPercent: Int,
        readingMaxWidthDp: Int,
        readingParagraphSpacing: ParagraphSpacingOption,
    ) {
        context.dataStore.edit { prefs ->
            prefs[readingFontSizeSpKey] = readingFontSizeSp
            prefs[readingFontOptionKey] = readingFontOption.name
            prefs[readingLineHeightPercentKey] = readingLineHeightPercent
            prefs[readingMaxWidthDpKey] = readingMaxWidthDp
            prefs[readingParagraphSpacingKey] = readingParagraphSpacing.name
        }
    }

    suspend fun savePlaybackSpeed(playbackSpeed: Float) {
        context.dataStore.edit { prefs ->
            prefs[playbackSpeedKey] = playbackSpeed
        }
    }

    suspend fun saveForceSentenceHighlightFallback(forceSentenceHighlightFallback: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[forceSentenceHighlightFallbackKey] = forceSentenceHighlightFallback
        }
    }

    suspend fun saveShowPlaybackDiagnostics(showPlaybackDiagnostics: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showPlaybackDiagnosticsKey] = showPlaybackDiagnostics
        }
    }

    suspend fun saveShowQueueCaptureMetadata(showQueueCaptureMetadata: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showQueueCaptureMetadataKey] = showQueueCaptureMetadata
        }
    }

    suspend fun saveTtsVoiceName(ttsVoiceName: String) {
        context.dataStore.edit { prefs ->
            prefs[ttsVoiceNameKey] = ttsVoiceName.trim()
        }
    }

    suspend fun savePersistentPlayerEnabled(persistentPlayerEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[persistentPlayerEnabledKey] = persistentPlayerEnabled
        }
    }

    suspend fun saveAutoAdvanceOnCompletion(autoAdvanceOnCompletion: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoAdvanceOnCompletionKey] = autoAdvanceOnCompletion
        }
    }

    suspend fun saveContinuousNowPlayingMarquee(continuousNowPlayingMarquee: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[continuousNowPlayingMarqueeKey] = continuousNowPlayingMarquee
        }
    }

    suspend fun saveAutoDownloadSavedArticles(autoDownloadSavedArticles: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoDownloadSavedArticlesKey] = autoDownloadSavedArticles
        }
    }

    suspend fun saveSignedInSession(
        baseUrl: String,
        connectionMode: ConnectionMode,
        apiToken: String,
    ) {
        val trimmedBaseUrl = baseUrl.trim()
        val trimmedToken = apiToken.trim()
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = trimmedBaseUrl
            prefs[connectionModeKey] = connectionMode.name
            prefs[tokenKey] = trimmedToken
            when (connectionMode) {
                ConnectionMode.LOCAL -> prefs[localBaseUrlKey] = trimmedBaseUrl
                ConnectionMode.LAN -> prefs[lanBaseUrlKey] = trimmedBaseUrl
                ConnectionMode.REMOTE -> prefs[remoteBaseUrlKey] = trimmedBaseUrl
            }
        }
    }

    suspend fun saveTokenOnly(apiToken: String) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = apiToken.trim()
        }
    }

    suspend fun savePlayerControlsMode(playerControlsMode: PlayerControlsMode) {
        context.dataStore.edit { prefs ->
            prefs[playerControlsModeKey] = playerControlsMode.name
            if (playerControlsMode != PlayerControlsMode.NUB) {
                prefs[playerLastNonNubModeKey] = playerControlsMode.name
            }
        }
    }

    suspend fun savePlayerControlsState(playerControlsMode: PlayerControlsMode, playerLastNonNubMode: PlayerControlsMode) {
        context.dataStore.edit { prefs ->
            prefs[playerControlsModeKey] = playerControlsMode.name
            prefs[playerLastNonNubModeKey] = playerLastNonNubMode
                .takeIf { it != PlayerControlsMode.NUB }
                ?.name
                ?: PlayerControlsMode.FULL.name
        }
    }

    suspend fun savePlayerChevronSnap(playerChevronSnapEdge: PlayerChevronSnapEdge, playerChevronEdgeOffset: Float) {
        context.dataStore.edit { prefs ->
            prefs[playerChevronSnapEdgeKey] = playerChevronSnapEdge.name
            prefs[playerChevronEdgeOffsetKey] = playerChevronEdgeOffset.coerceIn(0f, 1f)
        }
    }

    suspend fun saveQueueSnapshot(selectedPlaylistId: Int?, queue: PlaybackQueueResponse) {
        val key = queueSnapshotKey(selectedPlaylistId)
        val savedAt = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val existing = decodeQueueSnapshots(prefs[queueSnapshotsJsonKey])
            val updated = listOf(
                QueueSnapshotRecord(
                    key = key,
                    count = queue.count,
                    items = queue.items,
                    savedAt = savedAt,
                ),
            ) + existing.records.filterNot { it.key == key }
            prefs[queueSnapshotsJsonKey] = json.encodeToString(
                QueueSnapshotState(
                    records = updated.take(MAX_QUEUE_SNAPSHOT_RECORDS),
                ),
            )
        }
    }

    suspend fun loadQueueSnapshot(selectedPlaylistId: Int?): PlaybackQueueResponse? {
        val key = queueSnapshotKey(selectedPlaylistId)
        val prefs = context.dataStore.data.first()
        val stored = decodeQueueSnapshots(prefs[queueSnapshotsJsonKey])
        val record = stored.records.firstOrNull { it.key == key } ?: return null
        return PlaybackQueueResponse(
            count = maxOf(record.count, record.items.size),
            items = record.items,
        )
    }

    suspend fun clearQueueSnapshots() {
        context.dataStore.edit { prefs ->
            prefs[queueSnapshotsJsonKey] = json.encodeToString(QueueSnapshotState())
        }
    }

    suspend fun enqueuePendingManualSave(
        source: PendingSaveSource,
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
        lastFailureMessage: String,
        autoRetryEligible: Boolean,
        incrementRetryCount: Boolean = true,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val duplicateIndex = existing.indexOfFirst { pending ->
                pending.source == source &&
                    pending.type == type &&
                    pending.urlInput == urlInput &&
                    pending.titleInput == titleInput &&
                    pending.bodyInput == bodyInput &&
                    pending.destinationPlaylistId == destinationPlaylistId
            }
            val updated = if (duplicateIndex >= 0) {
                existing.mapIndexed { index, item ->
                    if (index == duplicateIndex) {
                        item.copy(
                            retryCount = if (incrementRetryCount) item.retryCount + 1 else item.retryCount,
                            lastFailureMessage = lastFailureMessage,
                            autoRetryEligible = autoRetryEligible,
                        )
                    } else {
                        item
                    }
                }
            } else {
                val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
                listOf(
                    PendingManualSaveItem(
                        id = nextId,
                        source = source,
                        type = type,
                        urlInput = urlInput,
                        titleInput = titleInput,
                        bodyInput = bodyInput,
                        destinationPlaylistId = destinationPlaylistId,
                        lastFailureMessage = lastFailureMessage,
                        autoRetryEligible = autoRetryEligible,
                    ),
                ) + existing
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun markPendingManualSaveResolved(
        itemId: Long,
        resolvedItemId: Int,
        statusMessage: String,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        lastFailureMessage = statusMessage,
                        autoRetryEligible = false,
                        resolvedItemId = resolvedItemId,
                    )
                } else {
                    item
                }
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun updatePendingManualSaveStatus(
        itemId: Long,
        statusMessage: String,
        autoRetryEligible: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        lastFailureMessage = statusMessage,
                        autoRetryEligible = autoRetryEligible,
                    )
                } else {
                    item
                }
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun markMatchingPendingManualSaveResolved(
        source: PendingSaveSource,
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
        resolvedItemId: Int,
        statusMessage: String,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.map { item ->
                if (
                    item.source == source &&
                    item.type == type &&
                    item.urlInput == urlInput &&
                    item.titleInput == titleInput &&
                    item.bodyInput == bodyInput &&
                    item.destinationPlaylistId == destinationPlaylistId
                ) {
                    item.copy(
                        lastFailureMessage = statusMessage,
                        autoRetryEligible = false,
                        resolvedItemId = resolvedItemId,
                    )
                } else {
                    item
                }
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun removePendingManualSave(itemId: Long) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.filterNot { it.id == itemId }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun clearPendingManualSaves() {
        context.dataStore.edit { prefs ->
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(emptyList())
        }
    }

    suspend fun markPendingManualSaveRetryFailure(
        itemId: Long,
        failureMessage: String,
        autoRetryEligible: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        retryCount = item.retryCount + 1,
                        lastFailureMessage = failureMessage,
                        autoRetryEligible = autoRetryEligible,
                    )
                } else {
                    item
                }
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun removeMatchingPendingManualSave(
        source: PendingSaveSource,
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodePendingManualSaves(prefs[pendingManualSavesJsonKey])
            val updated = existing.filterNot { pending ->
                pending.source == source &&
                    pending.type == type &&
                    pending.urlInput == urlInput &&
                    pending.titleInput == titleInput &&
                    pending.bodyInput == bodyInput &&
                    pending.destinationPlaylistId == destinationPlaylistId
            }
            prefs[pendingManualSavesJsonKey] = encodePendingManualSaves(updated)
        }
    }

    suspend fun saveConnectionTestSuccess(
        mode: ConnectionMode,
        baseUrl: String,
        gitSha: String?,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodeConnectionTestSuccesses(prefs[connectionTestSuccessJsonKey])
            val next = ConnectionTestSuccessSnapshot(
                mode = mode,
                baseUrl = baseUrl.trim(),
                gitSha = gitSha?.trim()?.takeIf { it.isNotEmpty() },
                succeededAtMs = System.currentTimeMillis(),
            )
            val updated = listOf(next) + existing.filterNot { it.mode == mode }
            prefs[connectionTestSuccessJsonKey] = json.encodeToString(
                ConnectionTestSuccessState(records = updated.take(3)),
            )
        }
    }

    private fun queueSnapshotKey(selectedPlaylistId: Int?): String {
        return selectedPlaylistId?.toString() ?: "smart"
    }

    private fun decodeQueueSnapshots(raw: String?): QueueSnapshotState {
        if (raw.isNullOrBlank()) return QueueSnapshotState()
        return runCatching {
            json.decodeFromString<QueueSnapshotState>(raw)
        }.getOrDefault(QueueSnapshotState())
    }

    private fun encodePendingManualSaves(items: List<PendingManualSaveItem>): String {
        return json.encodeToString(PendingManualSaveState(records = items))
    }

    private fun decodePendingManualSaves(raw: String?): List<PendingManualSaveItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<PendingManualSaveState>(raw).records
        }.getOrDefault(emptyList())
    }

    private fun decodeConnectionTestSuccesses(raw: String?): List<ConnectionTestSuccessSnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<ConnectionTestSuccessState>(raw).records
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_QUEUE_SNAPSHOT_RECORDS = 16
    }
}

@Serializable
private data class QueueSnapshotState(
    val records: List<QueueSnapshotRecord> = emptyList(),
)

@Serializable
private data class QueueSnapshotRecord(
    val key: String,
    val count: Int,
    val items: List<PlaybackQueueItem>,
    val savedAt: Long,
)

@Serializable
private data class PendingManualSaveState(
    val records: List<PendingManualSaveItem> = emptyList(),
)

@Serializable
private data class ConnectionTestSuccessState(
    val records: List<ConnectionTestSuccessSnapshot> = emptyList(),
)
