package com.mimeo.android

import android.app.Application
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ConnectivityDiagnosticOutcome
import com.mimeo.android.model.ConnectivityDiagnosticRow
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaylistEntrySummary
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.repository.ItemTextResult
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.PlaylistMembershipToggleResult
import com.mimeo.android.repository.PlaybackRepository
import com.mimeo.android.repository.ProgressPostResult
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.settings.ConnectivityDiagnosticsScreen
import com.mimeo.android.ui.settings.SettingsScreen
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.playlists.PlaylistsScreen
import com.mimeo.android.ui.queue.QueueScreen
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DONE_PERCENT_THRESHOLD = 98

data class UiSnackbarMessage(
    val message: String,
    val actionLabel: String? = null,
    val actionKey: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
)

private fun isLikelyPhysicalDevice(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val brand = Build.BRAND.lowercase()
    return !(fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        model.contains("sdk") ||
        model.contains("emulator") ||
        brand.startsWith("generic"))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[AppViewModel::class.java]
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MimeoApp(vm)
            }
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application.applicationContext)
    private val apiClient = ApiClient()
    private val repository = PlaybackRepository(
        apiClient = apiClient,
        database = AppDatabase.getInstance(application.applicationContext),
        appContext = application.applicationContext,
    )

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val queueItems: StateFlow<List<PlaybackQueueItem>> = _queueItems.asStateFlow()
    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()

    private val _queueLoading = MutableStateFlow(false)
    val queueLoading: StateFlow<Boolean> = _queueLoading.asStateFlow()

    private val _queueOffline = MutableStateFlow(false)
    val queueOffline: StateFlow<Boolean> = _queueOffline.asStateFlow()

    private val _pendingProgressCount = MutableStateFlow(0)
    val pendingProgressCount: StateFlow<Int> = _pendingProgressCount.asStateFlow()

    private val _cachedItemIds = MutableStateFlow<Set<Int>>(emptySet())
    val cachedItemIds: StateFlow<Set<Int>> = _cachedItemIds.asStateFlow()

    private val _progressSyncBadgeState = MutableStateFlow(ProgressSyncBadgeState.SYNCED)
    val progressSyncBadgeState: StateFlow<ProgressSyncBadgeState> = _progressSyncBadgeState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    private val _snackbarMessages = MutableSharedFlow<UiSnackbarMessage>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<UiSnackbarMessage> = _snackbarMessages.asSharedFlow()
    private val _testingConnection = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = _testingConnection.asStateFlow()

    private val _diagnosticsRows = MutableStateFlow<List<ConnectivityDiagnosticRow>>(emptyList())
    val diagnosticsRows: StateFlow<List<ConnectivityDiagnosticRow>> = _diagnosticsRows.asStateFlow()

    private val _diagnosticsRunning = MutableStateFlow(false)
    val diagnosticsRunning: StateFlow<Boolean> = _diagnosticsRunning.asStateFlow()

    private val _diagnosticsLastError = MutableStateFlow<String?>(null)
    val diagnosticsLastError: StateFlow<String?> = _diagnosticsLastError.asStateFlow()

    private val _playbackPositionByItem = MutableStateFlow<Map<Int, PlaybackPosition>>(emptyMap())
    val playbackPositionByItem: StateFlow<Map<Int, PlaybackPosition>> = _playbackPositionByItem.asStateFlow()

    private val _nowPlayingSession = MutableStateFlow<NowPlayingSession?>(null)
    val nowPlayingSession: StateFlow<NowPlayingSession?> = _nowPlayingSession.asStateFlow()
    private val _sessionIssueMessage = MutableStateFlow<String?>(null)
    val sessionIssueMessage: StateFlow<String?> = _sessionIssueMessage.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { _settings.value = it }
        }
        WorkScheduler.enqueueProgressSync(application.applicationContext)
        viewModelScope.launch {
            refreshPendingCount()
            flushPendingProgress()
        }
        viewModelScope.launch {
            val loadResult = repository.getSessionLoadResult()
            val session = loadResult.session
            _nowPlayingSession.value = session
            _sessionIssueMessage.value = if (loadResult.wasCorrupt) {
                "Saved Now Playing session was invalid. Clear session metadata if this repeats."
            } else {
                null
            }
            if (session != null) {
                _playbackPositionByItem.value = session.items.associate { item ->
                    item.itemId to PlaybackPosition(
                        chunkIndex = item.chunkIndex.coerceAtLeast(0),
                        offsetInChunkChars = item.offsetInChunkChars.coerceAtLeast(0),
                    )
                }
                _cachedItemIds.value = repository.getCachedItemIds(session.items.map { it.itemId })
            }
        }
    }

    fun saveSettings(
        baseUrl: String,
        token: String,
        autoAdvanceOnCompletion: Boolean,
        autoScrollWhileListening: Boolean,
    ) {
        viewModelScope.launch {
            settingsStore.save(
                baseUrl = baseUrl,
                apiToken = token,
                autoAdvanceOnCompletion = autoAdvanceOnCompletion,
                autoScrollWhileListening = autoScrollWhileListening,
                selectedPlaylistId = settings.value.selectedPlaylistId,
            )
            _statusMessage.value = "Settings saved"
        }
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        actionKey: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        _snackbarMessages.tryEmit(
            UiSnackbarMessage(
                message = message,
                actionLabel = actionLabel,
                actionKey = actionKey,
                duration = duration,
            ),
        )
    }

    fun testConnection() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            _testingConnection.value = true
            try {
                val version = apiClient.getDebugVersion(current.baseUrl, current.apiToken)
                _statusMessage.value = "Connected git_sha=${version.gitSha ?: "unknown"}"
                _queueOffline.value = false
                updateSyncBadgeState()
            } catch (e: ApiException) {
                _statusMessage.value = if (e.statusCode == 401) "Unauthorized-check token" else e.message
            } catch (e: Exception) {
                _statusMessage.value = e.message
            } finally {
                _testingConnection.value = false
            }
        }
    }

    fun loadQueue() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            _queueLoading.value = true
            try {
                runCatching {
                    repository.listPlaylists(current.baseUrl, current.apiToken)
                }.getOrNull()?.let { loaded ->
                    _playlists.value = loaded
                }
                val queue = repository.loadQueueAndPrefetch(
                    current.baseUrl,
                    current.apiToken,
                    playlistId = current.selectedPlaylistId,
                )
                _queueItems.value = queue.items
                _cachedItemIds.value = repository.getCachedItemIds(queue.items.map { it.itemId })
                _queueOffline.value = false
                _statusMessage.value = "Queue loaded (${queue.count})"
                flushPendingProgress()
            } catch (e: ApiException) {
                _queueOffline.value = false
                _statusMessage.value = if (e.statusCode == 401) "Unauthorized-check token" else e.message
                updateSyncBadgeState()
            } catch (e: Exception) {
                _queueOffline.value = isNetworkError(e)
                _statusMessage.value = e.message ?: "Failed to load queue"
                updateSyncBadgeState()
            } finally {
                _queueLoading.value = false
                refreshPendingCount()
            }
        }
    }

    fun refreshPlaylists() {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        viewModelScope.launch {
            try {
                val loaded = repository.listPlaylists(current.baseUrl, current.apiToken)
                _playlists.value = loaded
                val selected = current.selectedPlaylistId
                if (selected != null && loaded.none { it.id == selected }) {
                    settingsStore.saveSelectedPlaylistId(null)
                    _statusMessage.value = "Selected playlist removed; switched to Smart queue"
                }
            } catch (_: Exception) {
                // Keep prior value; queue still works with Smart mode fallback.
            }
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(selectedPlaylistId = playlistId) }
            settingsStore.saveSelectedPlaylistId(playlistId)
            loadQueue()
        }
    }

    fun createPlaylist(name: String) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            try {
                val created = repository.createPlaylist(current.baseUrl, current.apiToken, name.trim())
                _playlists.update { listOf(created) + it.filterNot { existing -> existing.id == created.id } }
                _statusMessage.value = "Playlist created"
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Create playlist failed"
            }
        }
    }

    fun renamePlaylist(playlistId: Int, name: String) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            try {
                val updated = repository.renamePlaylist(current.baseUrl, current.apiToken, playlistId, name.trim())
                _playlists.update { rows -> rows.map { if (it.id == playlistId) updated else it } }
                _statusMessage.value = "Playlist renamed"
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Rename playlist failed"
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = "Token required"
            return
        }
        viewModelScope.launch {
            try {
                repository.deletePlaylist(current.baseUrl, current.apiToken, playlistId)
                _playlists.update { rows -> rows.filterNot { it.id == playlistId } }
                if (settings.value.selectedPlaylistId == playlistId) {
                    settingsStore.saveSelectedPlaylistId(null)
                }
                _statusMessage.value = "Playlist deleted"
                loadQueue()
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Delete playlist failed"
            }
        }
    }

    fun isItemInPlaylist(itemId: Int, playlistId: Int): Boolean {
        return playlists.value
            .firstOrNull { it.id == playlistId }
            ?.entries
            ?.any { it.articleId == itemId } == true
    }

    suspend fun togglePlaylistMembership(itemId: Int, playlistId: Int): Result<PlaylistMembershipToggleResult> {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            return Result.failure(IllegalStateException("Token required"))
        }

        val currentMembership = isItemInPlaylist(itemId, playlistId)
        return try {
            val result = repository.togglePlaylistMembership(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                playlistId = playlistId,
                itemId = itemId,
                isCurrentlyMember = currentMembership,
            )
            updatePlaylistEntriesLocally(
                playlistId = playlistId,
                itemId = itemId,
                added = result.added,
            )
            if (settings.value.selectedPlaylistId == playlistId) {
                loadQueue()
            }
            Result.success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun runConnectivityDiagnostics(isPhysicalDevice: Boolean) {
        val current = settings.value
        val baseUrl = current.baseUrl.trim().trimEnd('/')
        val token = current.apiToken.trim()
        val rows = mutableListOf<ConnectivityDiagnosticRow>()
        _diagnosticsRunning.value = true
        _diagnosticsLastError.value = null

        val baseHint = baseUrlHint(baseUrl, isPhysicalDevice)
        if (baseHint != null) {
            rows += diagnosticRow(
                name = "base_url",
                url = baseUrl.ifBlank { "(unset)" },
                outcome = ConnectivityDiagnosticOutcome.INFO,
                detail = "base URL check",
                hint = baseHint,
            )
        }

        if (token.isBlank()) {
            rows += diagnosticRow(
                name = "auth",
                url = baseUrl.ifBlank { "(unset)" },
                outcome = ConnectivityDiagnosticOutcome.FAIL,
                detail = "token missing",
                hint = "add API token in Settings",
            )
            _diagnosticsRows.value = rows
            _diagnosticsLastError.value = "Token missing"
            _diagnosticsRunning.value = false
            return
        }

        viewModelScope.launch {
            var lastError: String? = null
            try {
                val health = runRawCheck(baseUrl, token, "/health", "health")
                rows += health
                if (health.outcome != ConnectivityDiagnosticOutcome.PASS && health.hint != null) {
                    lastError = health.hint
                }

                val debugVersion = runRawCheck(baseUrl, token, "/debug/version", "debug/version")
                rows += if (debugVersion.outcome == ConnectivityDiagnosticOutcome.PASS) {
                    val gitSha = extractJsonField(debugVersion.detail, "git_sha") ?: "unknown"
                    debugVersion.copy(detail = "git_sha=$gitSha")
                } else {
                    debugVersion
                }
                if (debugVersion.outcome == ConnectivityDiagnosticOutcome.FAIL && debugVersion.hint != null) {
                    lastError = debugVersion.hint
                }

                val debugPython = runRawCheck(baseUrl, token, "/debug/python", "debug/python")
                rows += if (debugPython.outcome == ConnectivityDiagnosticOutcome.PASS) {
                    val sysPrefix = extractJsonField(debugPython.detail, "sys_prefix") ?: "unknown"
                    debugPython.copy(detail = "sys_prefix=$sysPrefix")
                } else {
                    debugPython
                }
                if (debugPython.outcome == ConnectivityDiagnosticOutcome.FAIL && debugPython.hint != null) {
                    lastError = debugPython.hint
                }
            } catch (e: Exception) {
                val message = e.message ?: "diagnostics failed"
                rows += diagnosticRow(
                    name = "diagnostics",
                    url = baseUrl,
                    outcome = ConnectivityDiagnosticOutcome.FAIL,
                    detail = "error=$message",
                    hint = classifyNetworkHint(message),
                )
                lastError = message
            } finally {
                _diagnosticsRows.value = rows
                _diagnosticsLastError.value = lastError
                _diagnosticsRunning.value = false
            }
        }
    }

    fun flushPendingProgress() {
        val current = settings.value
        if (current.apiToken.isBlank()) return
        viewModelScope.launch {
            try {
                val syncResult = repository.flushPendingProgress(current.baseUrl, current.apiToken)
                if (syncResult.flushedCount > 0) {
                    _statusMessage.value = "Synced ${syncResult.flushedCount} queued updates"
                }
                _queueOffline.value = false
                if (syncResult.retryableFailures > 0 && syncResult.pendingCount > 0) {
                    WorkScheduler.enqueueProgressSync(getApplication<Application>().applicationContext)
                }
                updateSyncBadgeState(syncResult.pendingCount)
            } catch (e: Exception) {
                if (isNetworkError(e)) {
                    _queueOffline.value = true
                }
                updateSyncBadgeState()
            } finally {
                refreshPendingCount()
            }
        }
    }

    suspend fun fetchItemText(itemId: Int): Result<ItemTextResult> {
        val current = settings.value
        val expectedVersion = expectedActiveVersionFor(itemId)
        return try {
            val loaded = repository.getItemText(current.baseUrl, current.apiToken, itemId, expectedVersion)
            if (loaded.usingCache) {
                _queueOffline.value = true
            } else {
                _queueOffline.value = false
                _cachedItemIds.update { previous -> previous + itemId }
            }
            updateSyncBadgeState()
            Result.success(loaded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isNetworkError(error)) {
                _queueOffline.value = true
                updateSyncBadgeState()
            }
            Result.failure(error)
        }
    }

    suspend fun postProgress(itemId: Int, percent: Int): Result<ProgressPostResult> {
        val current = settings.value
        val clamped = percent.coerceIn(0, 100)
        return try {
            val result = repository.postProgress(current.baseUrl, current.apiToken, itemId, clamped)
            applyLocalProgress(itemId = itemId, percent = clamped)
            if (result.queued) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.QUEUED
            } else {
                _queueOffline.value = false
            }
            val pending = refreshPendingCount()
            updateSyncBadgeState(pending)
            Result.success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun resolveInitialPlayerItemId(fallbackItemId: Int): Int {
        val existing = nowPlayingSession.value
        val existingItem = existing?.currentItem
        if (existingItem != null) {
            return existingItem.itemId
        }
        val queue = queueItems.value
        if (queue.isNotEmpty()) {
            val session = repository.startSession(queue, fallbackItemId)
            applySessionSnapshot(session)
            return session.currentItem?.itemId ?: fallbackItemId
        }
        return fallbackItemId
    }

    fun startNowPlayingSession(startItemId: Int) {
        val queue = queueItems.value
        if (queue.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val session = repository.startSession(queue, startItemId)
            applySessionSnapshot(session)
        }
    }

    fun restartNowPlayingSession() {
        viewModelScope.launch {
            val restarted = repository.restartSession()
            if (restarted == null) {
                _sessionIssueMessage.value = "No active session to restart."
                return@launch
            }
            applySessionSnapshot(restarted)
            _statusMessage.value = "Now Playing session restarted."
        }
    }

    fun clearNowPlayingSession() {
        viewModelScope.launch {
            clearNowPlayingSessionNow()
        }
    }

    suspend fun clearNowPlayingSessionNow() {
        repository.clearSession()
        _nowPlayingSession.value = null
        _playbackPositionByItem.value = emptyMap()
        _sessionIssueMessage.value = null
        _statusMessage.value = "Now Playing session cleared."
    }

    fun currentNowPlayingItemId(): Int? = nowPlayingSession.value?.currentItem?.itemId

    fun isItemCached(itemId: Int): Boolean = cachedItemIds.value.contains(itemId)

    fun shouldAutoAdvanceAfterCompletion(): Boolean = settings.value.autoAdvanceOnCompletion
    fun shouldAutoScrollWhileListening(): Boolean = settings.value.autoScrollWhileListening

    fun baseUrlHintForDevice(isPhysicalDevice: Boolean): String? =
        baseUrlHint(settings.value.baseUrl.trim().trimEnd('/'), isPhysicalDevice)

    fun nowPlayingSummaryText(): String? {
        val session = nowPlayingSession.value ?: return null
        val current = session.currentItem ?: session.items.firstOrNull() ?: return null
        val title = current.title?.takeIf { it.isNotBlank() }
            ?: current.url.takeIf { it.isNotBlank() }
            ?: "Item ${current.itemId}"
        val progress = knownProgressForItem(current.itemId)
        val doneSuffix = if (progress >= DONE_PERCENT_THRESHOLD) " (Done)" else ""
        return "$title$doneSuffix"
    }

    fun knownProgressForItem(itemId: Int): Int {
        val queueProgress = queueItems.value.firstOrNull { it.itemId == itemId }?.lastReadPercent ?: 0
        val sessionProgress = nowPlayingSession.value
            ?.items
            ?.firstOrNull { it.itemId == itemId }
            ?.lastReadPercent ?: 0
        return maxOf(queueProgress, sessionProgress)
    }

    suspend fun setNowPlayingCurrentItem(itemId: Int) {
        val session = nowPlayingSession.value ?: return
        val idx = session.items.indexOfFirst { it.itemId == itemId }
        if (idx < 0) return
        _nowPlayingSession.value = repository.setCurrentIndex(idx) ?: session.copy(currentIndex = idx)
    }

    suspend fun nextSessionItemId(currentId: Int): Int? {
        val session = getOrCreateNowPlayingSession(currentId) ?: return null
        val idx = session.items.indexOfFirst { it.itemId == currentId }.let { if (it >= 0) it else session.currentIndex }
        if (idx >= session.items.lastIndex) return null
        val nextIndex = idx + 1
        val updated = repository.setCurrentIndex(nextIndex) ?: session.copy(currentIndex = nextIndex)
        _nowPlayingSession.value = updated
        return updated.currentItem?.itemId
    }

    suspend fun prevSessionItemId(currentId: Int): Int? {
        val session = getOrCreateNowPlayingSession(currentId) ?: return null
        val idx = session.items.indexOfFirst { it.itemId == currentId }.let { if (it >= 0) it else session.currentIndex }
        if (idx <= 0) return null
        val prevIndex = idx - 1
        val updated = repository.setCurrentIndex(prevIndex) ?: session.copy(currentIndex = prevIndex)
        _nowPlayingSession.value = updated
        return updated.currentItem?.itemId
    }

    private suspend fun getOrCreateNowPlayingSession(currentId: Int): NowPlayingSession? {
        nowPlayingSession.value?.let { return it }
        val queue = queueItems.value
        if (queue.isEmpty()) return null
        val session = repository.startSession(queue, currentId)
        applySessionSnapshot(session)
        return session
    }

    fun getPlaybackPosition(itemId: Int): PlaybackPosition {
        return playbackPositionByItem.value[itemId] ?: PlaybackPosition()
    }

    fun setPlaybackPosition(itemId: Int, chunkIndex: Int, offsetInChunkChars: Int) {
        val normalized = PlaybackPosition(
            chunkIndex = chunkIndex.coerceAtLeast(0),
            offsetInChunkChars = offsetInChunkChars.coerceAtLeast(0),
        )
        _playbackPositionByItem.update { previous -> previous + (itemId to normalized) }
        viewModelScope.launch {
            val updated = repository.setCurrentPlaybackPosition(
                itemId = itemId,
                chunkIndex = normalized.chunkIndex,
                offsetInChunkChars = normalized.offsetInChunkChars,
            )
            if (updated != null) {
                _nowPlayingSession.value = updated
            }
        }
    }

    private suspend fun refreshPendingCount(): Int {
        val pending = repository.countPendingProgress()
        _pendingProgressCount.value = pending
        return pending
    }

    private suspend fun applyLocalProgress(itemId: Int, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _queueItems.update { existing ->
            existing.map { item ->
                if (item.itemId != itemId) {
                    item
                } else {
                    val merged = maxOf(item.lastReadPercent ?: 0, clamped)
                    item.copy(lastReadPercent = merged)
                }
            }
        }
        repository.setNowPlayingItemProgress(itemId, clamped)?.let { updated ->
            _nowPlayingSession.value = updated
        }
    }

    private fun applySessionSnapshot(session: NowPlayingSession) {
        _nowPlayingSession.value = session
        _playbackPositionByItem.value = session.items.associate { item ->
            item.itemId to PlaybackPosition(
                chunkIndex = item.chunkIndex.coerceAtLeast(0),
                offsetInChunkChars = item.offsetInChunkChars.coerceAtLeast(0),
            )
        }
        _sessionIssueMessage.value = null
    }

    private fun expectedActiveVersionFor(itemId: Int): Int? {
        val fromSession = nowPlayingSession.value?.items?.firstOrNull { it.itemId == itemId }?.activeContentVersionId
        if (fromSession != null) return fromSession
        return queueItems.value.firstOrNull { it.itemId == itemId }?.activeContentVersionId
    }

    private fun updateSyncBadgeState(pendingCount: Int = _pendingProgressCount.value) {
        _progressSyncBadgeState.value = when {
            _queueOffline.value && pendingCount > 0 -> ProgressSyncBadgeState.OFFLINE
            _queueOffline.value -> ProgressSyncBadgeState.OFFLINE
            pendingCount > 0 -> ProgressSyncBadgeState.QUEUED
            else -> ProgressSyncBadgeState.SYNCED
        }
    }

    private fun isNetworkError(error: Exception): Boolean = error is IOException

    private fun updatePlaylistEntriesLocally(playlistId: Int, itemId: Int, added: Boolean) {
        _playlists.update { rows ->
            rows.map { playlist ->
                if (playlist.id != playlistId) {
                    playlist
                } else {
                    val hasEntry = playlist.entries.any { it.articleId == itemId }
                    val nextEntries = when {
                        added && !hasEntry -> playlist.entries + PlaylistEntrySummary(id = 0, articleId = itemId)
                        !added && hasEntry -> playlist.entries.filterNot { it.articleId == itemId }
                        else -> playlist.entries
                    }
                    playlist.copy(entries = nextEntries)
                }
            }
        }
    }

    private suspend fun runRawCheck(baseUrl: String, token: String, path: String, name: String): ConnectivityDiagnosticRow {
        val url = "$baseUrl$path"
        return try {
            val response = apiClient.getRawEndpoint(baseUrl, token, path)
            if (response.statusCode in 200..299) {
                diagnosticRow(
                    name = name,
                    url = url,
                    outcome = ConnectivityDiagnosticOutcome.PASS,
                    detail = response.body.take(200).ifBlank { "status=${response.statusCode}" },
                )
            } else {
                diagnosticRow(
                    name = name,
                    url = url,
                    outcome = ConnectivityDiagnosticOutcome.FAIL,
                    detail = "status=${response.statusCode}",
                    hint = classifyHttpHint(path, response.statusCode),
                )
            }
        } catch (e: Exception) {
            val message = e.message ?: "request failed"
            diagnosticRow(
                name = name,
                url = url,
                outcome = ConnectivityDiagnosticOutcome.FAIL,
                detail = "error=$message",
                hint = classifyNetworkHint(message),
            )
        }
    }

    private fun diagnosticRow(
        name: String,
        url: String,
        outcome: ConnectivityDiagnosticOutcome,
        detail: String,
        hint: String? = null,
    ): ConnectivityDiagnosticRow = ConnectivityDiagnosticRow(
        name = name,
        url = url,
        outcome = outcome,
        detail = detail,
        hint = hint,
        checkedAt = timestampNow(),
    )

    private fun timestampNow(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun extractJsonField(body: String, field: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject[field]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun classifyHttpHint(path: String, status: Int): String {
        if (status == 401 || status == 403) return "check API token/auth"
        if (status >= 500) return "backend error; check logs"
        if (status == 404 && (path == "/debug/version" || path == "/debug/python")) {
            return "backend stale; run verify-mimeo.ps1 then hard refresh"
        }
        return "check backend endpoint and settings"
    }

    private fun classifyNetworkHint(message: String): String {
        val lower = message.lowercase(Locale.US)
        return if (lower.contains("timeout") || lower.contains("failed to connect") || lower.contains("connection")) {
            "start backend (verify-mimeo.ps1) and check firewall rule"
        } else {
            "check backend reachability and logs"
        }
    }

    private fun baseUrlHint(baseUrl: String, isPhysicalDevice: Boolean): String? {
        if (!isPhysicalDevice) return null
        val lower = baseUrl.lowercase(Locale.US)
        return when {
            lower.contains("127.0.0.1") || lower.contains("localhost") ->
                "127.0.0.1/localhost points to phone; use http://<PC_LAN_IP>:8000"
            lower.contains("10.0.2.2") ->
                "10.0.2.2 is emulator-only; use http://<PC_LAN_IP>:8000 on phone"
            else -> null
        }
    }
}

@Composable
private fun MimeoApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val navBackStack by nav.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentRoute = navBackStack?.destination?.route.orEmpty()
    val settings by vm.settings.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val selectedTab = when {
        currentRoute.startsWith("player") -> "player"
        currentRoute.startsWith("playlists") -> "playlists"
        currentRoute.startsWith("settings") -> "settings"
        else -> "queue"
    }
    val baseUrlHint = vm.baseUrlHintForDevice(isLikelyPhysicalDevice())
    val baseAddress = settings.baseUrl.trim().removePrefix("http://").removePrefix("https://")
    val statusLooksError = statusMessage?.let { message ->
        val lower = message.lowercase()
        lower.contains("failed") ||
            lower.contains("error") ||
            lower.contains("unauthorized") ||
            lower.contains("forbidden") ||
            lower.contains("timeout")
    } ?: false
    val bannerStateLabel = when {
        queueOffline -> "Offline"
        baseUrlHint != null -> "LAN mismatch"
        else -> "Status"
    }
    val bannerSummary = when {
        queueOffline -> "Cannot reach server at $baseAddress"
        baseUrlHint != null -> baseUrlHint
        statusLooksError -> statusMessage.orEmpty()
        else -> ""
    }
    val showGlobalBanner = queueOffline || baseUrlHint != null || statusLooksError

    LaunchedEffect(vm, snackbarHostState) {
        vm.snackbarMessages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.message,
                actionLabel = message.actionLabel,
                duration = message.duration,
            )
            if (
                result == SnackbarResult.ActionPerformed &&
                message.actionKey == "open_diagnostics"
            ) {
                nav.navigate("settings/diagnostics") { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.ime),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == "queue",
                    onClick = { nav.navigate("queue") { launchSingleTop = true } },
                    label = { Text("Queue") },
                    icon = {},
                )
                NavigationBarItem(
                    selected = selectedTab == "player",
                    onClick = { nav.navigate("player") { launchSingleTop = true } },
                    label = { Text("Player") },
                    icon = {},
                )
                NavigationBarItem(
                    selected = selectedTab == "playlists",
                    onClick = { nav.navigate("playlists") { launchSingleTop = true } },
                    label = { Text("Playlists") },
                    icon = {},
                )
                NavigationBarItem(
                    selected = selectedTab == "settings",
                    onClick = { nav.navigate("settings") { launchSingleTop = true } },
                    label = { Text("Settings") },
                    icon = {},
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showGlobalBanner) {
                StatusBanner(
                    stateLabel = bannerStateLabel,
                    summary = bannerSummary,
                    detail = if (statusLooksError) statusMessage else baseUrlHint,
                    onRetry = { vm.loadQueue() },
                    onDiagnostics = { nav.navigate("settings/diagnostics") },
                )
            }
            NavHost(
                navController = nav,
                startDestination = "queue",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("playlists") {
                    PlaylistsScreen(vm = vm)
                }
                composable("settings") {
                    SettingsScreen(
                        vm = vm,
                        onOpenDiagnostics = { nav.navigate("settings/diagnostics") },
                    )
                }
                composable("settings/diagnostics") {
                    ConnectivityDiagnosticsScreen(vm = vm)
                }
                composable("player") {
                    val nowPlayingId = vm.currentNowPlayingItemId()
                    if (nowPlayingId == null) {
                        NoNowPlayingScreen(onGoQueue = { nav.navigate("queue") })
                    } else {
                        PlayerScreen(
                            vm = vm,
                            onShowSnackbar = { message, actionLabel, actionKey ->
                                vm.showSnackbar(message, actionLabel, actionKey)
                            },
                            initialItemId = nowPlayingId,
                            onOpenItem = { nextId -> nav.navigate("player/$nextId") },
                            onBackToQueue = { focusId ->
                                if (focusId == null) {
                                    nav.navigate("queue")
                                } else {
                                    nav.navigate("queue?focusItemId=$focusId")
                                }
                            },
                            onOpenDiagnostics = { nav.navigate("settings/diagnostics") },
                        )
                    }
                }
                composable(
                    route = "queue?focusItemId={focusItemId}",
                    arguments = listOf(
                        navArgument("focusItemId") {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                ) { backStack ->
                    val focusItemId = backStack.arguments?.getInt("focusItemId")?.takeIf { it > 0 }
                    QueueScreen(
                        vm = vm,
                        onShowSnackbar = { message, actionLabel, actionKey ->
                            vm.showSnackbar(message, actionLabel, actionKey)
                        },
                        focusItemId = focusItemId,
                        onOpenPlayer = { itemId -> nav.navigate("player/$itemId") },
                        onOpenDiagnostics = { nav.navigate("settings/diagnostics") },
                    )
                }
                composable(
                    "player/{itemId}",
                    arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
                ) { backStack ->
                    val itemId = backStack.arguments?.getInt("itemId") ?: return@composable
                    PlayerScreen(
                        vm = vm,
                        onShowSnackbar = { message, actionLabel, actionKey ->
                            vm.showSnackbar(message, actionLabel, actionKey)
                        },
                        initialItemId = itemId,
                        onOpenItem = { nextId -> nav.navigate("player/$nextId") },
                        onBackToQueue = { focusId ->
                            if (focusId == null) {
                                nav.navigate("queue")
                            } else {
                                nav.navigate("queue?focusItemId=$focusId")
                            }
                        },
                        onOpenDiagnostics = { nav.navigate("settings/diagnostics") },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoNowPlayingScreen(onGoQueue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No Now Playing session")
        Button(onClick = onGoQueue) {
            Text("Go to Queue")
        }
    }
}
