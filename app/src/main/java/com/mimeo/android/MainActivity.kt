package com.mimeo.android

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.model.FolderSummary
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaylistEntrySummary
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.repository.ItemTextResult
import com.mimeo.android.repository.FoldersRepository
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.repository.OfflineReadyCandidate
import com.mimeo.android.repository.PlaylistMembershipToggleResult
import com.mimeo.android.repository.PlaybackRepository
import com.mimeo.android.repository.resolveOfflineReadyItemIds
import com.mimeo.android.share.ShareSaveCoordinator
import com.mimeo.android.share.ShareSaveRefreshBus
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.isAutoRetryEligiblePendingSaveResult
import com.mimeo.android.share.isRetryablePendingSaveResult
import com.mimeo.android.ui.collections.CollectionsScreen
import com.mimeo.android.ui.collections.FolderDetailScreen
import com.mimeo.android.repository.ProgressPostResult
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.settings.ConnectivityDiagnosticsScreen
import com.mimeo.android.ui.settings.ConnectionTestMessageResolver
import com.mimeo.android.ui.settings.SettingsScreen
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.playlists.PlaylistsScreen
import com.mimeo.android.ui.queue.QueueScreen
import com.mimeo.android.ui.signin.SignInScreen
import com.mimeo.android.ui.signin.SignInState
import com.mimeo.android.ui.signin.buildAuthDeviceName
import com.mimeo.android.ui.signin.inferConnectionModeForBaseUrl
import com.mimeo.android.ui.signin.resolveSignInErrorMessage
import com.mimeo.android.ui.theme.MimeoTheme
import com.mimeo.android.work.WorkScheduler
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DONE_PERCENT_THRESHOLD = 98
private const val ROUTE_UP_NEXT = "upNext"
private const val ROUTE_SIGN_IN = "signIn"
private const val ROUTE_LOCUS = "locus"
private const val ROUTE_LOCUS_ITEM = "locus/{itemId}"
private const val ROUTE_COLLECTIONS = "collections"
private const val ROUTE_COLLECTIONS_PLAYLISTS = "collections/playlists"
private const val ROUTE_COLLECTIONS_FOLDER = "collections/folder/{folderId}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SETTINGS_DIAGNOSTICS = "settings/diagnostics"
private const val ACTION_KEY_OPEN_DIAGNOSTICS = "open_diagnostics"
private const val ACTION_KEY_OPEN_SETTINGS = "open_settings"
private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
private const val DEBUG_TARGET_ITEM_ID = 409
private const val INITIAL_SIGN_IN_AUTO_DOWNLOAD_LIMIT = 8

private data class BottomNavDestination(
    val route: String,
    val label: String,
)

data class UiSnackbarMessage(
    val message: String,
    val actionLabel: String? = null,
    val actionKey: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
)

data class PendingRetryBatchResult(
    val successCount: Int,
    val firstFailureResult: ShareSaveResult? = null,
)

internal fun selectInitialQueueHydrationTargets(
    queueItems: List<PlaybackQueueItem>,
    cachedItemIds: Set<Int>,
    limit: Int = INITIAL_SIGN_IN_AUTO_DOWNLOAD_LIMIT,
): List<Int> {
    if (limit <= 0) return emptyList()
    return queueItems
        .asSequence()
        .filterNot { isTerminalPendingProcessingStatus(it.status) }
        .map { it.itemId }
        .distinct()
        .filterNot { cachedItemIds.contains(it) }
        .take(limit)
        .toList()
}

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
    private lateinit var vm: AppViewModel
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[AppViewModel::class.java]
        consumeLaunchIntent(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            MimeoTheme {
                MimeoApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != ShareReceiverActivity.ACTION_OPEN_SETTINGS) return
        vm.requestNavigation(ROUTE_SETTINGS)
        setIntent(Intent(incomingIntent).apply { action = null })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application.applicationContext)
    private val apiClient = ApiClient()
    private val database = AppDatabase.getInstance(application.applicationContext)
    private val repository = PlaybackRepository(
        apiClient = apiClient,
        database = database,
        appContext = application.applicationContext,
    )
    private val shareSaveCoordinator = ShareSaveCoordinator(
        context = application.applicationContext,
        apiClient = apiClient,
        settingsStore = settingsStore,
        playbackRepository = repository,
    )
    private val foldersRepository = FoldersRepository(database)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val queueItems: StateFlow<List<PlaybackQueueItem>> = _queueItems.asStateFlow()
    private val _pendingManualSaves = MutableStateFlow<List<PendingManualSaveItem>>(emptyList())
    val pendingManualSaves: StateFlow<List<PendingManualSaveItem>> = _pendingManualSaves.asStateFlow()
    private val pendingManualRetryMutex = Mutex()
    private val _pendingManualRetryInProgress = MutableStateFlow(false)
    val pendingManualRetryInProgress: StateFlow<Boolean> = _pendingManualRetryInProgress.asStateFlow()
    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()
    private val _folders = MutableStateFlow<List<FolderSummary>>(emptyList())
    val folders: StateFlow<List<FolderSummary>> = _folders.asStateFlow()
    private val _playlistFolderAssignments = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val playlistFolderAssignments: StateFlow<Map<Int, Int>> = _playlistFolderAssignments.asStateFlow()

    private val _queueLoading = MutableStateFlow(false)
    val queueLoading: StateFlow<Boolean> = _queueLoading.asStateFlow()
    private val queueLoadMutex = Mutex()
    private val _lastQueueFetchDebug = MutableStateFlow(QueueFetchDebugSnapshot())
    val lastQueueFetchDebug: StateFlow<QueueFetchDebugSnapshot> = _lastQueueFetchDebug.asStateFlow()

    private val _queueOffline = MutableStateFlow(false)
    val queueOffline: StateFlow<Boolean> = _queueOffline.asStateFlow()

    private val _pendingProgressCount = MutableStateFlow(0)
    val pendingProgressCount: StateFlow<Int> = _pendingProgressCount.asStateFlow()

    private val _cachedItemIds = MutableStateFlow<Set<Int>>(emptySet())
    val cachedItemIds: StateFlow<Set<Int>> = _cachedItemIds.asStateFlow()
    private val _pendingQueueFocusItemId = MutableStateFlow<Int?>(null)
    val pendingQueueFocusItemId: StateFlow<Int?> = _pendingQueueFocusItemId.asStateFlow()

    private val _progressSyncBadgeState = MutableStateFlow(ProgressSyncBadgeState.SYNCED)
    val progressSyncBadgeState: StateFlow<ProgressSyncBadgeState> = _progressSyncBadgeState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    private val _snackbarMessages = Channel<UiSnackbarMessage>(capacity = Channel.BUFFERED)
    val snackbarMessages: Flow<UiSnackbarMessage> = _snackbarMessages.receiveAsFlow()
    private val _testingConnection = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = _testingConnection.asStateFlow()
    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    private val _diagnosticsRows = MutableStateFlow<List<ConnectivityDiagnosticRow>>(emptyList())
    val diagnosticsRows: StateFlow<List<ConnectivityDiagnosticRow>> = _diagnosticsRows.asStateFlow()

    private val _diagnosticsRunning = MutableStateFlow(false)
    val diagnosticsRunning: StateFlow<Boolean> = _diagnosticsRunning.asStateFlow()

    private val _diagnosticsLastError = MutableStateFlow<String?>(null)
    val diagnosticsLastError: StateFlow<String?> = _diagnosticsLastError.asStateFlow()
    private val _connectionTestSuccessByMode = MutableStateFlow<Map<ConnectionMode, ConnectionTestSuccessSnapshot>>(emptyMap())
    val connectionTestSuccessByMode: StateFlow<Map<ConnectionMode, ConnectionTestSuccessSnapshot>> =
        _connectionTestSuccessByMode.asStateFlow()

    private val _playbackPositionByItem = MutableStateFlow<Map<Int, PlaybackPosition>>(emptyMap())
    val playbackPositionByItem: StateFlow<Map<Int, PlaybackPosition>> = _playbackPositionByItem.asStateFlow()

    private val _nowPlayingSession = MutableStateFlow<NowPlayingSession?>(null)
    val nowPlayingSession: StateFlow<NowPlayingSession?> = _nowPlayingSession.asStateFlow()
    private val _sessionIssueMessage = MutableStateFlow<String?>(null)
    val sessionIssueMessage: StateFlow<String?> = _sessionIssueMessage.asStateFlow()
    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute.asStateFlow()
    private val _settingsScrollOffset = MutableStateFlow(0)
    val settingsScrollOffset: StateFlow<Int> = _settingsScrollOffset.asStateFlow()
    private var lastPendingAutoRetryAtMs: Long = 0L
    private var pendingInitialPostSignInHydration = false
    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var lastConnectivityRefreshAtMs: Long = 0L

    init {
        viewModelScope.launch {
            var previous = _settings.value
            settingsStore.settingsFlow.collect { next ->
                _settings.value = next
                if (next.apiToken.isBlank()) {
                    _queueItems.value = emptyList()
                    _cachedItemIds.value = emptySet()
                    _queueOffline.value = false
                    _playlists.value = emptyList()
                }
                if (previous.apiToken.isBlank() && next.apiToken.isNotBlank()) {
                    pendingInitialPostSignInHydration = true
                    loadQueue()
                } else if (previous.apiToken.isNotBlank() && next.apiToken.isBlank()) {
                    pendingInitialPostSignInHydration = false
                    settingsStore.clearQueueSnapshots()
                }
                previous = next
            }
        }
        viewModelScope.launch {
            settingsStore.pendingManualSavesFlow.collect { pending ->
                _pendingManualSaves.value = pending
            }
        }
        viewModelScope.launch {
            settingsStore.connectionTestSuccessFlow.collect { snapshots ->
                _connectionTestSuccessByMode.value = snapshots
            }
        }
        WorkScheduler.enqueueProgressSync(application.applicationContext)
        viewModelScope.launch {
            refreshPendingCount()
            flushPendingProgress()
        }
        viewModelScope.launch {
            ShareSaveRefreshBus.events.collect { event ->
                refreshPlaylists()
                if (settings.value.selectedPlaylistId == event.playlistId) {
                    _pendingQueueFocusItemId.value = event.itemId
                    loadQueue()
                }
            }
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
                _cachedItemIds.value = resolveOfflineReadyIdsForSession(session.items)
            }
        }
        viewModelScope.launch {
            refreshFolders()
        }
        startConnectivityMonitoring()
    }

    fun saveSettings(
        baseUrl: String,
        connectionMode: ConnectionMode,
        localBaseUrl: String,
        lanBaseUrl: String,
        remoteBaseUrl: String,
        token: String,
        autoAdvanceOnCompletion: Boolean,
        persistentPlayerEnabled: Boolean,
        autoScrollWhileListening: Boolean,
        continuousNowPlayingMarquee: Boolean,
        forceSentenceHighlightFallback: Boolean,
        keepShareResultNotifications: Boolean,
        autoDownloadSavedArticles: Boolean,
    ) {
        viewModelScope.launch {
            settingsStore.save(
                baseUrl = baseUrl,
                connectionMode = connectionMode,
                localBaseUrl = localBaseUrl,
                lanBaseUrl = lanBaseUrl,
                remoteBaseUrl = remoteBaseUrl,
                apiToken = token,
                autoAdvanceOnCompletion = autoAdvanceOnCompletion,
                persistentPlayerEnabled = persistentPlayerEnabled,
                autoScrollWhileListening = autoScrollWhileListening,
                continuousNowPlayingMarquee = continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = forceSentenceHighlightFallback,
                keepShareResultNotifications = keepShareResultNotifications,
                autoDownloadSavedArticles = autoDownloadSavedArticles,
                playbackSpeed = settings.value.playbackSpeed,
                selectedPlaylistId = settings.value.selectedPlaylistId,
                defaultSavePlaylistId = settings.value.defaultSavePlaylistId,
                readingFontSizeSp = settings.value.readingFontSizeSp,
                readingFontOption = settings.value.readingFontOption,
                readingLineHeightPercent = settings.value.readingLineHeightPercent,
                readingMaxWidthDp = settings.value.readingMaxWidthDp,
                readingParagraphSpacing = settings.value.readingParagraphSpacing,
                playerControlsMode = settings.value.playerControlsMode,
                playerLastNonNubMode = settings.value.playerLastNonNubMode,
                playerChevronSnapEdge = settings.value.playerChevronSnapEdge,
                playerChevronEdgeOffset = settings.value.playerChevronEdgeOffset,
            )
            _statusMessage.value = "Settings saved"
        }
    }

    fun saveReadingPreferences(
        readingFontSizeSp: Int,
        readingFontOption: ReaderFontOption,
        readingLineHeightPercent: Int,
        readingMaxWidthDp: Int,
        readingParagraphSpacing: ParagraphSpacingOption,
    ) {
        viewModelScope.launch {
            settingsStore.save(
                baseUrl = settings.value.baseUrl,
                connectionMode = settings.value.connectionMode,
                localBaseUrl = settings.value.localBaseUrl,
                lanBaseUrl = settings.value.lanBaseUrl,
                remoteBaseUrl = settings.value.remoteBaseUrl,
                apiToken = settings.value.apiToken,
                autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                persistentPlayerEnabled = settings.value.persistentPlayerEnabled,
                autoScrollWhileListening = settings.value.autoScrollWhileListening,
                continuousNowPlayingMarquee = settings.value.continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = settings.value.forceSentenceHighlightFallback,
                keepShareResultNotifications = settings.value.keepShareResultNotifications,
                autoDownloadSavedArticles = settings.value.autoDownloadSavedArticles,
                playbackSpeed = settings.value.playbackSpeed,
                selectedPlaylistId = settings.value.selectedPlaylistId,
                defaultSavePlaylistId = settings.value.defaultSavePlaylistId,
                readingFontSizeSp = readingFontSizeSp,
                readingFontOption = readingFontOption,
                readingLineHeightPercent = readingLineHeightPercent,
                readingMaxWidthDp = readingMaxWidthDp,
                readingParagraphSpacing = readingParagraphSpacing,
                playerControlsMode = settings.value.playerControlsMode,
                playerLastNonNubMode = settings.value.playerLastNonNubMode,
                playerChevronSnapEdge = settings.value.playerChevronSnapEdge,
                playerChevronEdgeOffset = settings.value.playerChevronEdgeOffset,
            )
        }
    }

    fun savePlaybackSpeed(playbackSpeed: Float) {
        viewModelScope.launch {
            settingsStore.save(
                baseUrl = settings.value.baseUrl,
                connectionMode = settings.value.connectionMode,
                localBaseUrl = settings.value.localBaseUrl,
                lanBaseUrl = settings.value.lanBaseUrl,
                remoteBaseUrl = settings.value.remoteBaseUrl,
                apiToken = settings.value.apiToken,
                autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                persistentPlayerEnabled = settings.value.persistentPlayerEnabled,
                autoScrollWhileListening = settings.value.autoScrollWhileListening,
                continuousNowPlayingMarquee = settings.value.continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = settings.value.forceSentenceHighlightFallback,
                keepShareResultNotifications = settings.value.keepShareResultNotifications,
                autoDownloadSavedArticles = settings.value.autoDownloadSavedArticles,
                playbackSpeed = playbackSpeed,
                selectedPlaylistId = settings.value.selectedPlaylistId,
                defaultSavePlaylistId = settings.value.defaultSavePlaylistId,
                readingFontSizeSp = settings.value.readingFontSizeSp,
                readingFontOption = settings.value.readingFontOption,
                readingLineHeightPercent = settings.value.readingLineHeightPercent,
                readingMaxWidthDp = settings.value.readingMaxWidthDp,
                readingParagraphSpacing = settings.value.readingParagraphSpacing,
                playerControlsMode = settings.value.playerControlsMode,
                playerLastNonNubMode = settings.value.playerLastNonNubMode,
                playerChevronSnapEdge = settings.value.playerChevronSnapEdge,
                playerChevronEdgeOffset = settings.value.playerChevronEdgeOffset,
            )
        }
    }

    fun savePersistentPlayerEnabled(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(persistentPlayerEnabled = enabled)
        viewModelScope.launch {
            settingsStore.savePersistentPlayerEnabled(enabled)
        }
    }

    fun saveContinuousNowPlayingMarquee(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(continuousNowPlayingMarquee = enabled)
        viewModelScope.launch {
            settingsStore.saveContinuousNowPlayingMarquee(enabled)
        }
    }

    fun saveAutoDownloadSavedArticles(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(autoDownloadSavedArticles = enabled)
        viewModelScope.launch {
            settingsStore.saveAutoDownloadSavedArticles(enabled)
        }
    }

    fun consumePendingQueueFocusItemId(itemId: Int) {
        if (_pendingQueueFocusItemId.value == itemId) {
            _pendingQueueFocusItemId.value = null
        }
    }

    fun savePlayerControlsMode(mode: PlayerControlsMode) {
        val current = settings.value
        _settings.value = current.copy(
            playerControlsMode = mode,
            playerLastNonNubMode = if (mode == PlayerControlsMode.NUB) {
                current.playerLastNonNubMode
            } else {
                mode
            },
        )
        viewModelScope.launch {
            settingsStore.savePlayerControlsMode(mode)
        }
    }

    fun savePlayerControlsState(mode: PlayerControlsMode, lastNonNubMode: PlayerControlsMode) {
        val safeLastMode = lastNonNubMode.takeIf { it != PlayerControlsMode.NUB } ?: PlayerControlsMode.FULL
        _settings.value = settings.value.copy(
            playerControlsMode = mode,
            playerLastNonNubMode = safeLastMode,
        )
        viewModelScope.launch {
            settingsStore.savePlayerControlsState(mode, safeLastMode)
        }
    }

    fun savePlayerChevronSnap(edge: PlayerChevronSnapEdge, edgeOffset: Float) {
        val current = settings.value
        _settings.value = current.copy(
            playerChevronSnapEdge = edge,
            playerChevronEdgeOffset = edgeOffset.coerceIn(0f, 1f),
        )
        viewModelScope.launch {
            settingsStore.savePlayerChevronSnap(edge, edgeOffset)
        }
    }

    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        actionKey: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        _snackbarMessages.trySend(
            UiSnackbarMessage(
                message = message,
                actionLabel = actionLabel,
                actionKey = actionKey,
                duration = duration,
            ),
        )
    }

    fun clearSignInError() {
        if (_signInState.value is SignInState.Error) {
            _signInState.value = SignInState.Idle
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _signInState.value = SignInState.Loading
            try {
                val normalizedBaseUrl = serverUrl.trim().trimEnd('/')
                val response = apiClient.postAuthToken(
                    baseUrl = normalizedBaseUrl,
                    username = username.trim(),
                    password = password,
                    deviceName = buildAuthDeviceName(
                        manufacturer = Build.MANUFACTURER.orEmpty(),
                        model = Build.MODEL.orEmpty(),
                    ),
                )
                val connectionMode = inferConnectionModeForBaseUrl(normalizedBaseUrl)
                settingsStore.saveSignedInSession(
                    baseUrl = normalizedBaseUrl,
                    connectionMode = connectionMode,
                    apiToken = response.token,
                )
                _signInState.value = SignInState.Idle
                requestNavigation(ROUTE_UP_NEXT)
            } catch (error: Exception) {
                _signInState.value = SignInState.Error(resolveSignInErrorMessage(error))
            }
        }
    }

    suspend fun saveUrlFromUpNext(
        rawInput: String,
        destinationPlaylistId: Int? = null,
    ): ShareSaveResult {
        return saveUrlFromSource(
            sharedText = rawInput,
            sharedTitle = null,
            destinationPlaylistId = destinationPlaylistId,
        )
    }

    private suspend fun saveUrlFromSource(
        sharedText: String,
        sharedTitle: String?,
        destinationPlaylistId: Int?,
    ): ShareSaveResult {
        return shareSaveCoordinator.saveSharedText(
            sharedText = sharedText,
            sharedTitle = sharedTitle,
            destinationPlaylistIdOverride = destinationPlaylistId,
        )
    }

    suspend fun saveManualTextFromUpNext(
        urlInput: String,
        titleInput: String?,
        bodyInput: String,
        destinationPlaylistId: Int? = null,
    ): ShareSaveResult {
        return shareSaveCoordinator.saveManualText(
            urlInput = urlInput,
            titleInput = titleInput,
            bodyInput = bodyInput,
            destinationPlaylistIdOverride = destinationPlaylistId,
        )
    }

    fun queueFailedManualSave(
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        result: ShareSaveResult,
        destinationPlaylistId: Int?,
    ) {
        if (!isRetryablePendingSaveResult(result)) return
        viewModelScope.launch {
            settingsStore.enqueuePendingManualSave(
                source = PendingSaveSource.MANUAL,
                type = type,
                urlInput = urlInput,
                titleInput = titleInput,
                bodyInput = bodyInput,
                destinationPlaylistId = destinationPlaylistId,
                lastFailureMessage = result.notificationText,
                autoRetryEligible = shouldAutoRetryManualSave(result),
                incrementRetryCount = false,
            )
        }
    }

    fun enqueueAcceptedPendingSave(
        source: PendingSaveSource,
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
    ) {
        viewModelScope.launch {
            settingsStore.enqueuePendingManualSave(
                source = source,
                type = type,
                urlInput = urlInput,
                titleInput = titleInput,
                bodyInput = bodyInput,
                destinationPlaylistId = destinationPlaylistId,
                lastFailureMessage = "Saving...",
                autoRetryEligible = false,
                incrementRetryCount = false,
            )
        }
    }

    fun markAcceptedPendingSaveResolved(
        source: PendingSaveSource,
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
        resolvedItemId: Int,
    ) {
        viewModelScope.launch {
            settingsStore.markMatchingPendingManualSaveResolved(
                source = source,
                type = type,
                urlInput = urlInput,
                titleInput = titleInput,
                bodyInput = bodyInput,
                destinationPlaylistId = destinationPlaylistId,
                resolvedItemId = resolvedItemId,
                statusMessage = "Processing...",
            )
        }
    }

    fun consumeStatusMessage(expected: String) {
        if (_statusMessage.value == expected) {
            _statusMessage.value = null
        }
    }

    suspend fun retryPendingManualSave(itemId: Long): ShareSaveResult? {
        if (!pendingManualRetryMutex.tryLock()) return null
        _pendingManualRetryInProgress.value = true
        try {
        val item = _pendingManualSaves.value.firstOrNull { it.id == itemId } ?: return null
        val result = retryPendingManualSaveItem(item)
        if (result is ShareSaveResult.Saved && result.itemId != null) {
            settingsStore.markPendingManualSaveResolved(
                itemId = itemId,
                resolvedItemId = result.itemId,
                statusMessage = "Processing...",
            )
        } else if (!isRetryablePendingSaveResult(result)) {
            settingsStore.removePendingManualSave(itemId)
        } else {
            settingsStore.markPendingManualSaveRetryFailure(
                itemId = itemId,
                failureMessage = resolvePendingRetryFailureMessage(
                    existingMessage = item.lastFailureMessage,
                    result = result,
                ),
                autoRetryEligible = shouldAutoRetryManualSave(result),
            )
        }
        return result
        } finally {
            _pendingManualRetryInProgress.value = false
            pendingManualRetryMutex.unlock()
        }
    }

    suspend fun retryAllPendingManualSaves(limit: Int = 20): PendingRetryBatchResult {
        if (!pendingManualRetryMutex.tryLock()) return PendingRetryBatchResult(successCount = -1)
        _pendingManualRetryInProgress.value = true
        try {
        val retryIds = _pendingManualSaves.value.take(limit).map { it.id }
        var successCount = 0
        var firstFailureResult: ShareSaveResult? = null
        retryIds.forEach { itemId ->
            val item = _pendingManualSaves.value.firstOrNull { it.id == itemId } ?: return@forEach
            val result = retryPendingManualSaveItem(item)
            if (result is ShareSaveResult.Saved && result.itemId != null) {
                settingsStore.markPendingManualSaveResolved(
                    itemId = itemId,
                    resolvedItemId = result.itemId,
                    statusMessage = "Processing...",
                )
            } else if (!isRetryablePendingSaveResult(result)) {
                settingsStore.removePendingManualSave(itemId)
            } else {
                settingsStore.markPendingManualSaveRetryFailure(
                    itemId = itemId,
                    failureMessage = resolvePendingRetryFailureMessage(
                        existingMessage = item.lastFailureMessage,
                        result = result,
                    ),
                    autoRetryEligible = shouldAutoRetryManualSave(result),
                )
            }
            if (result is ShareSaveResult.Saved) {
                successCount += 1
            } else if (firstFailureResult == null) {
                firstFailureResult = result
            }
        }
        return PendingRetryBatchResult(
            successCount = successCount,
            firstFailureResult = firstFailureResult,
        )
        } finally {
            _pendingManualRetryInProgress.value = false
            pendingManualRetryMutex.unlock()
        }
    }

    private suspend fun retryPendingManualSaveItem(item: PendingManualSaveItem): ShareSaveResult {
        return when (item.type) {
            PendingManualSaveType.URL -> {
                val title = if (item.source == PendingSaveSource.SHARE) {
                    item.titleInput
                } else {
                    null
                }
                saveUrlFromSource(
                    sharedText = item.urlInput,
                    sharedTitle = title,
                    destinationPlaylistId = item.destinationPlaylistId,
                )
            }
            PendingManualSaveType.TEXT -> saveManualTextFromUpNext(
                urlInput = item.urlInput,
                titleInput = item.titleInput,
                bodyInput = item.bodyInput.orEmpty(),
                destinationPlaylistId = item.destinationPlaylistId,
            )
        }
    }

    fun clearPendingManualSaves() {
        viewModelScope.launch {
            settingsStore.clearPendingManualSaves()
        }
    }

    fun removePendingManualSave(itemId: Long) {
        viewModelScope.launch {
            settingsStore.removePendingManualSave(itemId)
        }
    }

    fun removeMatchingPendingManualSave(
        type: PendingManualSaveType,
        urlInput: String,
        titleInput: String?,
        bodyInput: String?,
        destinationPlaylistId: Int?,
    ) {
        viewModelScope.launch {
            settingsStore.removeMatchingPendingManualSave(
                source = PendingSaveSource.MANUAL,
                type = type,
                urlInput = urlInput,
                titleInput = titleInput,
                bodyInput = bodyInput,
                destinationPlaylistId = destinationPlaylistId,
            )
        }
    }

    fun testConnection() {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            _statusMessage.value = ConnectionTestMessageResolver.tokenRequired()
            return
        }
        viewModelScope.launch {
            _testingConnection.value = true
            try {
                val version = apiClient.getDebugVersion(current.baseUrl, current.apiToken)
                settingsStore.saveConnectionTestSuccess(
                    mode = current.connectionMode,
                    baseUrl = current.baseUrl,
                    gitSha = version.gitSha,
                )
                _statusMessage.value = ConnectionTestMessageResolver.connected(
                    mode = current.connectionMode,
                    baseUrl = current.baseUrl,
                    gitSha = version.gitSha,
                )
                _queueOffline.value = false
                updateSyncBadgeState()
                val retrySummary = retryAllPendingManualSaves()
                if (retrySummary.successCount >= 0) {
                    loadQueueOnce(autoRetryPendingSaves = false)
                }
            } catch (e: ApiException) {
                _statusMessage.value = ConnectionTestMessageResolver.forApiFailure(
                    mode = current.connectionMode,
                    baseUrl = current.baseUrl,
                    statusCode = e.statusCode,
                    message = e.message,
                )
            } catch (e: Exception) {
                _statusMessage.value = ConnectionTestMessageResolver.forException(
                    mode = current.connectionMode,
                    baseUrl = current.baseUrl,
                    message = e.message,
                )
            } finally {
                _testingConnection.value = false
            }
        }
    }

    suspend fun loadQueueOnce(autoRetryPendingSaves: Boolean = true): Result<Unit> = queueLoadMutex.withLock {
        val current = settings.value
        val wasOffline = _queueOffline.value
        val snapshotPreloaded = if (_queueItems.value.isEmpty()) {
            applySavedQueueSnapshot(
                selectedPlaylistId = current.selectedPlaylistId,
                markOffline = false,
                statusMessage = null,
            )
        } else {
            false
        }
        if (current.apiToken.isBlank()) {
            if (snapshotPreloaded) {
                return@withLock Result.success(Unit)
            }
            _statusMessage.value = "Token required"
            return@withLock Result.failure(IllegalStateException("Token required"))
        }
        _queueLoading.value = true
        return@withLock try {
            runCatching {
                repository.listPlaylists(current.baseUrl, current.apiToken)
            }.getOrNull()?.let { loaded ->
                _playlists.value = loaded
            }
            val queueResult = repository.loadQueueAndPrefetch(
                current.baseUrl,
                current.apiToken,
                playlistId = current.selectedPlaylistId,
                prefetchCount = 0,
            )
            val queue = queueResult.payload
            var offlineReadyIds = resolveOfflineReadyIds(queue.items)
            offlineReadyIds = runInitialPostSignInHydrationIfNeeded(
                current = current,
                queueItems = queue.items,
                cachedItemIds = offlineReadyIds,
            )
            offlineReadyIds = ensurePendingItemsOfflineReady(
                queueItems = queue.items,
                selectedPlaylistId = current.selectedPlaylistId,
                offlineReadyIds = offlineReadyIds,
                current = current,
            )
            _queueItems.value = queue.items
            val appliedSnapshot = queueResult.debugSnapshot.copy(
                appliedItemCount = _queueItems.value.size,
                appliedContains409 = _queueItems.value.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                lastFetchAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            )
            _lastQueueFetchDebug.value = appliedSnapshot
            if (BuildConfig.DEBUG) {
                Log.d(
                    QUEUE_DEBUG_TAG,
                    "viewModelApply playlistId=${appliedSnapshot.selectedPlaylistId} uiCount=${appliedSnapshot.appliedItemCount} uiContains409=${appliedSnapshot.appliedContains409} requestUrl=${appliedSnapshot.requestUrl}",
                )
            }
            _cachedItemIds.value = offlineReadyIds
            settingsStore.saveQueueSnapshot(current.selectedPlaylistId, queue)
            syncPendingSaveProcessingFailures(
                queueItems = queue.items,
                selectedPlaylistId = current.selectedPlaylistId,
                baseUrl = current.baseUrl,
                token = current.apiToken,
            )
            reconcilePendingSavesWithQueue(
                queueItems = queue.items,
                selectedPlaylistId = current.selectedPlaylistId,
            )
            _queueOffline.value = false
            _statusMessage.value = "Queue loaded (${queue.count})"
            flushPendingProgress()
            val shouldAttemptPendingAutoRetry = autoRetryPendingSaves && shouldAttemptPendingAutoRetryOnQueueLoad(
                wasOffline = wasOffline,
            )
            val autoRetrySuccessCount = if (shouldAttemptPendingAutoRetry) {
                autoRetryPendingManualSaves(limit = 20)
            } else {
                0
            }
            if (shouldAttemptPendingAutoRetry) {
                lastPendingAutoRetryAtMs = System.currentTimeMillis()
            }
            if (autoRetrySuccessCount > 0) {
                val refreshedQueueResult = repository.loadQueueAndPrefetch(
                    current.baseUrl,
                    current.apiToken,
                    playlistId = current.selectedPlaylistId,
                    prefetchCount = 0,
                )
                val refreshedQueue = refreshedQueueResult.payload
                var refreshedOfflineReadyIds = resolveOfflineReadyIds(refreshedQueue.items)
                refreshedOfflineReadyIds = ensurePendingItemsOfflineReady(
                    queueItems = refreshedQueue.items,
                    selectedPlaylistId = current.selectedPlaylistId,
                    offlineReadyIds = refreshedOfflineReadyIds,
                    current = current,
                )
                _queueItems.value = refreshedQueue.items
                val refreshedSnapshot = refreshedQueueResult.debugSnapshot.copy(
                    appliedItemCount = _queueItems.value.size,
                    appliedContains409 = _queueItems.value.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                    lastFetchAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                )
                _lastQueueFetchDebug.value = refreshedSnapshot
                if (BuildConfig.DEBUG) {
                    Log.d(
                        QUEUE_DEBUG_TAG,
                        "viewModelApply playlistId=${refreshedSnapshot.selectedPlaylistId} uiCount=${refreshedSnapshot.appliedItemCount} uiContains409=${refreshedSnapshot.appliedContains409} requestUrl=${refreshedSnapshot.requestUrl}",
                    )
                }
                _cachedItemIds.value = refreshedOfflineReadyIds
                settingsStore.saveQueueSnapshot(current.selectedPlaylistId, refreshedQueue)
                syncPendingSaveProcessingFailures(
                    queueItems = refreshedQueue.items,
                    selectedPlaylistId = current.selectedPlaylistId,
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                )
                reconcilePendingSavesWithQueue(
                    queueItems = refreshedQueue.items,
                    selectedPlaylistId = current.selectedPlaylistId,
                )
                _statusMessage.value = "Queue loaded (${refreshedQueue.count})"
            }
            Result.success(Unit)
        } catch (e: ApiException) {
            if (
                e.statusCode in 500..599 &&
                applySavedQueueSnapshot(
                    selectedPlaylistId = current.selectedPlaylistId,
                    markOffline = true,
                    statusMessage = null,
                )
            ) {
                return@withLock Result.success(Unit)
            }
            _queueOffline.value = false
            _statusMessage.value = userFacingRequestErrorMessage(e, fallback = "Refresh failed")
            updateSyncBadgeState()
            Result.failure(e)
        } catch (e: Exception) {
            val networkError = isNetworkError(e)
            if (
                networkError &&
                applySavedQueueSnapshot(
                    selectedPlaylistId = current.selectedPlaylistId,
                    markOffline = true,
                    statusMessage = null,
                )
            ) {
                return@withLock Result.success(Unit)
            }
            _queueOffline.value = networkError
            _statusMessage.value = userFacingRequestErrorMessage(e, fallback = "Couldn't refresh queue")
            updateSyncBadgeState()
            Result.failure(e)
        } finally {
            _queueLoading.value = false
            refreshPendingCount()
        }
    }

    fun loadQueue(autoRetryPendingSaves: Boolean = true) {
        viewModelScope.launch {
            loadQueueOnce(autoRetryPendingSaves = autoRetryPendingSaves)
        }
    }

    private suspend fun autoRetryPendingManualSaves(limit: Int): Int {
        val candidates = _pendingManualSaves.value
            .filter { it.autoRetryEligible || it.resolvedItemId == null }
            .take(limit)
        var successCount = 0
        candidates.forEach { item ->
            val result = retryPendingManualSave(item.id)
            if (result is ShareSaveResult.Saved) {
                successCount += 1
            }
        }
        return successCount
    }

    private suspend fun applySavedQueueSnapshot(
        selectedPlaylistId: Int?,
        markOffline: Boolean,
        statusMessage: String?,
    ): Boolean {
        val snapshot = runCatching {
            settingsStore.loadQueueSnapshot(selectedPlaylistId)
        }.getOrNull() ?: return false

        _queueItems.value = snapshot.items
        _cachedItemIds.value = resolveOfflineReadyIds(snapshot.items)
        val appliedSnapshot = QueueFetchDebugSnapshot(
            selectedPlaylistId = selectedPlaylistId,
            requestUrl = "local_snapshot",
            statusCode = null,
            responseItemCount = snapshot.items.size,
            responseContains409 = snapshot.items.any { it.itemId == DEBUG_TARGET_ITEM_ID },
            responseBytes = 0,
            responseHash = "",
            appliedItemCount = snapshot.items.size,
            appliedContains409 = snapshot.items.any { it.itemId == DEBUG_TARGET_ITEM_ID },
            lastFetchAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
        )
        _lastQueueFetchDebug.value = appliedSnapshot
        if (markOffline) {
            _queueOffline.value = true
            _statusMessage.value = statusMessage ?: "Offline: showing saved queue snapshot (${snapshot.items.size})"
        }
        updateSyncBadgeState()
        return true
    }

    private fun shouldAutoRetryManualSave(result: ShareSaveResult): Boolean {
        return isAutoRetryEligiblePendingSaveResult(result)
    }

    private suspend fun ensurePendingItemsOfflineReady(
        queueItems: List<PlaybackQueueItem>,
        selectedPlaylistId: Int?,
        offlineReadyIds: Set<Int>,
        current: AppSettings,
    ): Set<Int> {
        if (!current.autoDownloadSavedArticles) return offlineReadyIds
        val pendingForDestination = _pendingManualSaves.value.filter { it.destinationPlaylistId == selectedPlaylistId }
        if (pendingForDestination.isEmpty()) return offlineReadyIds

        val updated = offlineReadyIds.toMutableSet()
        val matchedItemsNeedingDownload = pendingForDestination
            .mapNotNull { pending ->
                queueItems.firstOrNull { queueItem -> pendingMatchesQueueItem(pending, queueItem) }
            }
            .distinctBy { it.itemId }
            .filterNot { updated.contains(it.itemId) }

        matchedItemsNeedingDownload.forEach { item ->
            val loaded = runCatching {
                repository.getItemText(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    itemId = item.itemId,
                    expectedActiveVersionId = expectedActiveVersionFor(item.itemId),
                )
            }.getOrNull() ?: return@forEach
            val relatedIds = if (loaded.payload.activeContentVersionId != null) {
                queueItems
                    .asSequence()
                    .filter { it.activeContentVersionId == loaded.payload.activeContentVersionId }
                    .map { it.itemId }
                    .toSet()
            } else {
                emptySet()
            }
            updated += item.itemId
            updated += relatedIds
        }

        return updated
    }

    private suspend fun reconcilePendingSavesWithQueue(
        queueItems: List<PlaybackQueueItem>,
        selectedPlaylistId: Int?,
    ) {
        val confirmedPendingIds = _pendingManualSaves.value
            .filter { it.destinationPlaylistId == selectedPlaylistId }
            .filter { pending ->
                queueItems.any { queueItem ->
                    pendingMatchesQueueItem(pending, queueItem) && !hasFailedQueueStatus(queueItem)
                }
            }
            .map { it.id }

        confirmedPendingIds.forEach { itemId ->
            settingsStore.removePendingManualSave(itemId)
        }
    }

    private suspend fun syncPendingSaveProcessingFailures(
        queueItems: List<PlaybackQueueItem>,
        selectedPlaylistId: Int?,
        baseUrl: String,
        token: String,
    ) {
        _pendingManualSaves.value
            .filter { it.destinationPlaylistId == selectedPlaylistId }
            .forEach { pending ->
                val failureMessage = resolveProcessingFailureMessage(
                    pending = pending,
                    queueItem = queueItems.firstOrNull { queueItem -> pendingMatchesQueueItem(pending, queueItem) },
                    baseUrl = baseUrl,
                    token = token,
                ) ?: return@forEach
                if (pending.lastFailureMessage != failureMessage) {
                    settingsStore.updatePendingManualSaveStatus(
                        itemId = pending.id,
                        statusMessage = failureMessage,
                        autoRetryEligible = false,
                    )
                    showSnackbar(failureMessage)
                }
            }
    }

    private fun pendingMatchesQueueItem(
        pending: PendingManualSaveItem,
        queueItem: PlaybackQueueItem,
    ): Boolean {
        pending.resolvedItemId?.let { resolvedItemId ->
            return queueItem.itemId == resolvedItemId
        }
        return normalizePendingComparisonUrl(pending.urlInput) == normalizePendingComparisonUrl(queueItem.url)
    }

    private fun normalizePendingComparisonUrl(raw: String?): String? {
        return raw?.trim()?.lowercase()?.removeSuffix("/")?.takeIf { it.isNotEmpty() }
    }

    private fun hasFailedQueueStatus(queueItem: PlaybackQueueItem): Boolean {
        return isTerminalPendingProcessingStatus(queueItem.status)
    }

    private suspend fun resolveProcessingFailureMessage(
        pending: PendingManualSaveItem,
        queueItem: PlaybackQueueItem?,
        baseUrl: String,
        token: String,
    ): String? {
        val itemId = pending.resolvedItemId ?: queueItem?.itemId
        if (itemId != null) {
            val summary = runCatching {
                apiClient.getItemSummary(baseUrl, token, itemId)
            }.getOrNull()
            if (summary != null) {
                val summaryHasFailure =
                    isTerminalPendingProcessingStatus(summary.status) ||
                        !summary.failureReason.isNullOrBlank()
                if (summaryHasFailure) {
                    return resolveTerminalPendingProcessingMessage(
                        status = summary.status,
                        failureReason = summary.failureReason,
                        fetchHttpStatus = summary.fetchHttpStatus,
                    )
                }
            }
        }
        val matchedQueueItem = queueItem ?: return null
        return if (hasFailedQueueStatus(matchedQueueItem)) {
            resolveTerminalPendingProcessingMessage(matchedQueueItem.status)
        } else {
            null
        }
    }

    private fun startConnectivityMonitoring() {
        val manager = connectivityManager ?: return
        if (connectivityCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                maybeRefreshAfterConnectivityRestored()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && validated) {
                    maybeRefreshAfterConnectivityRestored()
                }
            }
        }
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager.registerNetworkCallback(request, callback)
            connectivityCallback = callback
        }
    }

    private fun maybeRefreshAfterConnectivityRestored() {
        if (!_queueOffline.value && _pendingManualSaves.value.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastConnectivityRefreshAtMs < 2_000L) return
        lastConnectivityRefreshAtMs = now
        viewModelScope.launch {
            loadQueueOnce(autoRetryPendingSaves = true)
        }
    }

    private fun shouldAttemptPendingAutoRetryOnQueueLoad(wasOffline: Boolean): Boolean {
        val hasRetryCandidate = _pendingManualSaves.value.any { it.autoRetryEligible || it.resolvedItemId == null }
        if (!hasRetryCandidate) return false
        if (wasOffline) return true
        return System.currentTimeMillis() - lastPendingAutoRetryAtMs >= 3_000L
    }

    private fun resolvePendingRetryFailureMessage(
        existingMessage: String?,
        result: ShareSaveResult,
    ): String {
        return when (result) {
            ShareSaveResult.SaveFailed -> existingMessage ?: ShareSaveResult.NetworkError.notificationText
            else -> result.notificationText
        }
    }

    suspend fun refreshPlaylistsOnce(): Result<Unit> {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            return Result.failure(IllegalStateException("Token required"))
        }
        return try {
            val loaded = repository.listPlaylists(current.baseUrl, current.apiToken)
            _playlists.value = loaded
            val selected = current.selectedPlaylistId
            if (selected != null && loaded.none { it.id == selected }) {
                _settings.update { it.copy(selectedPlaylistId = null) }
                settingsStore.saveSelectedPlaylistId(null)
                _statusMessage.value = "Selected playlist removed; switched to Smart queue"
            }
            val defaultSave = current.defaultSavePlaylistId
            if (defaultSave != null && loaded.none { it.id == defaultSave }) {
                _settings.update { it.copy(defaultSavePlaylistId = null) }
                settingsStore.saveDefaultSavePlaylistId(null)
                _statusMessage.value = "Default save playlist removed; switched to Smart queue"
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: Exception) {
            if (isNetworkError(error)) {
                _queueOffline.value = true
                updateSyncBadgeState()
            }
            Result.failure(error)
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            refreshPlaylistsOnce()
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            _folders.value = foldersRepository.listFolders()
            _playlistFolderAssignments.value = foldersRepository.listPlaylistAssignments()
        }
    }

    fun selectPlaylist(playlistId: Int?) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(selectedPlaylistId = playlistId) }
            settingsStore.saveSelectedPlaylistId(playlistId)
            val snapshotApplied = applySavedQueueSnapshot(
                selectedPlaylistId = playlistId,
                markOffline = false,
                statusMessage = null,
            )
            if (!snapshotApplied) {
                _queueItems.value = emptyList()
                _cachedItemIds.value = emptySet()
            }
            loadQueue(autoRetryPendingSaves = false)
        }
    }

    fun saveDefaultSavePlaylistId(playlistId: Int?) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(defaultSavePlaylistId = playlistId) }
            settingsStore.saveDefaultSavePlaylistId(playlistId)
        }
    }

    fun saveForceSentenceHighlightFallback(enabled: Boolean) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(forceSentenceHighlightFallback = enabled) }
            settingsStore.saveForceSentenceHighlightFallback(enabled)
        }
    }

    fun setSettingsScrollOffset(offset: Int) {
        _settingsScrollOffset.value = offset.coerceAtLeast(0)
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
                foldersRepository.assignPlaylistToFolder(playlistId, null)
                _playlists.update { rows -> rows.filterNot { it.id == playlistId } }
                if (settings.value.selectedPlaylistId == playlistId) {
                    _settings.update { it.copy(selectedPlaylistId = null) }
                    settingsStore.saveSelectedPlaylistId(null)
                }
                if (settings.value.defaultSavePlaylistId == playlistId) {
                    _settings.update { it.copy(defaultSavePlaylistId = null) }
                    settingsStore.saveDefaultSavePlaylistId(null)
                }
                refreshFolders()
                _statusMessage.value = "Playlist deleted"
                loadQueue()
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Delete playlist failed"
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            foldersRepository.createFolder(trimmed)
            refreshFolders()
            showSnackbar("Folder created")
        }
    }

    fun renameFolder(folderId: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            foldersRepository.renameFolder(folderId, trimmed)
            refreshFolders()
            showSnackbar("Folder renamed")
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch {
            foldersRepository.deleteFolder(folderId)
            refreshFolders()
            showSnackbar("Folder deleted")
        }
    }

    fun assignPlaylistToFolder(playlistId: Int, folderId: Int?) {
        viewModelScope.launch {
            foldersRepository.assignPlaylistToFolder(playlistId, folderId)
            refreshFolders()
            val message = if (folderId == null) {
                "Removed from folder"
            } else {
                val folderName = _folders.value.firstOrNull { it.id == folderId }?.name ?: "folder"
                "Assigned to $folderName"
            }
            showSnackbar(message)
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
            }
            val relatedIds = if (loaded.payload.activeContentVersionId != null) {
                queueItems.value
                    .asSequence()
                    .filter { it.activeContentVersionId == loaded.payload.activeContentVersionId }
                    .map { it.itemId }
                    .toSet()
            } else {
                emptySet()
            }
            _cachedItemIds.update { previous -> previous + itemId + relatedIds }
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

    suspend fun downloadItemForOffline(itemId: Int): Result<Unit> {
        return fetchItemText(itemId).map { Unit }
    }

    suspend fun refreshCurrentPlayerItem(itemId: Int): Result<Unit> {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            return Result.failure(IllegalStateException("Token required"))
        }
        return try {
            runCatching {
                repository.listPlaylists(current.baseUrl, current.apiToken)
            }.getOrNull()?.let { loaded ->
                _playlists.value = loaded
            }

            val queueResult = repository.loadQueueAndPrefetch(
                current.baseUrl,
                current.apiToken,
                playlistId = current.selectedPlaylistId,
                prefetchCount = 0,
            )
            val queue = queueResult.payload
            _queueItems.value = queue.items
            val appliedSnapshot = queueResult.debugSnapshot.copy(
                appliedItemCount = _queueItems.value.size,
                appliedContains409 = _queueItems.value.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                lastFetchAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            )
            _lastQueueFetchDebug.value = appliedSnapshot
            _cachedItemIds.value = resolveOfflineReadyIds(queue.items)
            settingsStore.saveQueueSnapshot(current.selectedPlaylistId, queue)
            repository.reconcileSessionWithQueue(queue.items)?.let { updated ->
                _nowPlayingSession.value = updated
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            refreshPendingCount()
            Result.success(Unit)
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

    suspend fun toggleCompletion(itemId: Int, markDone: Boolean, resumePercent: Int): Result<ProgressPostResult> {
        val current = settings.value
        val canonicalPercent = if (markDone) 100 else 97
        val clampedResume = resumePercent.coerceIn(0, canonicalPercent)
        return try {
            val result = repository.toggleCompletion(current.baseUrl, current.apiToken, itemId, markDone)
            applyLocalCompletionState(
                itemId = itemId,
                canonicalPercent = canonicalPercent,
                resumePercent = clampedResume,
            )
            _queueOffline.value = false
            updateSyncBadgeState()
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

    fun requestNavigation(route: String) {
        _pendingNavigationRoute.value = route
    }

    fun consumePendingNavigation(route: String) {
        if (_pendingNavigationRoute.value == route) {
            _pendingNavigationRoute.value = null
        }
    }

    fun nowPlayingSummaryText(): String? {
        val session = nowPlayingSession.value ?: return null
        val current = session.currentItem ?: session.items.firstOrNull() ?: return null
        val title = current.title?.takeIf { it.isNotBlank() }
            ?: current.url.takeIf { it.isNotBlank() }
            ?: "Item ${current.itemId}"
        val doneSuffix = if (knownFurthestForItem(current.itemId) >= DONE_PERCENT_THRESHOLD) " (Done)" else ""
        return "$title$doneSuffix"
    }

    fun knownProgressForItem(itemId: Int): Int {
        val queueProgress = queueItems.value.firstOrNull { it.itemId == itemId }?.progressPercent ?: 0
        val sessionProgress = nowPlayingSession.value
            ?.items
            ?.firstOrNull { it.itemId == itemId }
            ?.lastReadPercent ?: 0
        return maxOf(queueProgress, sessionProgress)
    }

    fun knownFurthestForItem(itemId: Int): Int {
        val queueFurthest = queueItems.value.firstOrNull { it.itemId == itemId }?.furthestPercent ?: 0
        val sessionFurthest = nowPlayingSession.value
            ?.items
            ?.firstOrNull { it.itemId == itemId }
            ?.lastReadPercent ?: 0
        return maxOf(queueFurthest, sessionFurthest)
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

    private suspend fun applyLocalCompletionState(itemId: Int, canonicalPercent: Int, resumePercent: Int) {
        val clampedCanonical = canonicalPercent.coerceIn(0, 100)
        val clampedResume = resumePercent.coerceIn(0, clampedCanonical)
        _queueItems.update { existing ->
            existing.map { item ->
                if (item.itemId != itemId) {
                    item
                } else {
                    item.copy(
                        lastReadPercent = clampedCanonical,
                        resumeReadPercent = clampedResume,
                        apiProgressPercent = clampedResume,
                        apiFurthestPercent = clampedCanonical,
                    )
                }
            }
        }
        repository.setNowPlayingItemCanonicalProgress(itemId, clampedCanonical)?.let { updated ->
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

    private suspend fun resolveOfflineReadyIds(items: List<PlaybackQueueItem>): Set<Int> {
        if (items.isEmpty()) return emptySet()
        val candidates = items.map { item ->
            OfflineReadyCandidate(
                itemId = item.itemId,
                activeContentVersionId = item.activeContentVersionId,
            )
        }
        val cachedByItemId = repository.getCachedItemIds(candidates.map { it.itemId })
        val cachedByVersion = repository.getCachedActiveContentVersionIds(
            candidates.mapNotNull { it.activeContentVersionId },
        )
        return resolveOfflineReadyItemIds(candidates, cachedByItemId, cachedByVersion)
    }

    private suspend fun resolveOfflineReadyIdsForSession(sessionItems: List<NowPlayingSessionItem>): Set<Int> {
        if (sessionItems.isEmpty()) return emptySet()
        val candidates = sessionItems.map { item ->
            OfflineReadyCandidate(
                itemId = item.itemId,
                activeContentVersionId = item.activeContentVersionId,
            )
        }
        val cachedByItemId = repository.getCachedItemIds(candidates.map { it.itemId })
        val cachedByVersion = repository.getCachedActiveContentVersionIds(
            candidates.mapNotNull { it.activeContentVersionId },
        )
        return resolveOfflineReadyItemIds(candidates, cachedByItemId, cachedByVersion)
    }

    private suspend fun runInitialPostSignInHydrationIfNeeded(
        current: AppSettings,
        queueItems: List<PlaybackQueueItem>,
        cachedItemIds: Set<Int>,
    ): Set<Int> {
        if (!pendingInitialPostSignInHydration) return cachedItemIds
        pendingInitialPostSignInHydration = false
        if (!current.autoDownloadSavedArticles) return cachedItemIds
        val targets = selectInitialQueueHydrationTargets(
            queueItems = queueItems,
            cachedItemIds = cachedItemIds,
        )
        if (targets.isEmpty()) return cachedItemIds
        repository.prefetchItemTexts(
            baseUrl = current.baseUrl,
            token = current.apiToken,
            itemIds = targets,
        )
        return resolveOfflineReadyIds(queueItems)
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

    private fun userFacingRequestErrorMessage(error: Throwable, fallback: String): String {
        if (error is ApiException) {
            return when {
                error.statusCode == 401 -> "Check your API token"
                error.statusCode >= 500 -> "Server error. Try again."
                !error.message.isNullOrBlank() -> error.message!!
                else -> fallback
            }
        }
        if (error is IOException) {
            return "Couldn't reach server"
        }
        val message = error.message?.trim()
        if (message.isNullOrEmpty()) return fallback
        if (message.contains("java.", ignoreCase = true) || message.length > 180) {
            return fallback
        }
        return message
    }

    override fun onCleared() {
        connectivityCallback?.let { callback ->
            runCatching {
                connectivityManager?.unregisterNetworkCallback(callback)
            }
        }
        connectivityCallback = null
        super.onCleared()
    }

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
    val navItems = listOf(
        BottomNavDestination(ROUTE_UP_NEXT, "Up Next"),
        BottomNavDestination(ROUTE_LOCUS, "Locus"),
        BottomNavDestination(ROUTE_COLLECTIONS, "Collections"),
        BottomNavDestination(ROUTE_SETTINGS, "Settings"),
    )
    val settings by vm.settings.collectAsState()
    val signInState by vm.signInState.collectAsState()
    val nowPlayingSession by vm.nowPlayingSession.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val pendingNavigationRoute by vm.pendingNavigationRoute.collectAsState()
    val requiresSignIn = settings.apiToken.isBlank()
    val sessionNowPlayingItemId = vm.currentNowPlayingItemId()
    val routeItemId = navBackStack?.arguments?.let { args ->
        if (args.containsKey("itemId")) args.getInt("itemId").takeIf { it > 0 } else null
    }
    val requestedPlayerItemId = routeItemId ?: sessionNowPlayingItemId
    val selectedTab = when {
        currentRoute.startsWith(ROUTE_LOCUS) -> ROUTE_LOCUS
        currentRoute.startsWith(ROUTE_COLLECTIONS) -> ROUTE_COLLECTIONS
        currentRoute.startsWith(ROUTE_SETTINGS) -> ROUTE_SETTINGS
        else -> ROUTE_UP_NEXT
    }
    val isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS)
    var playbackActive by rememberSaveable { mutableStateOf(false) }
    var readerChromeHidden by rememberSaveable { mutableStateOf(false) }
    val controlsMode = settings.playerControlsMode
    val storedLastNonNubMode = settings.playerLastNonNubMode
        .takeIf { it != PlayerControlsMode.NUB }
        ?: PlayerControlsMode.FULL
    var previousRoute by rememberSaveable { mutableStateOf(currentRoute) }
    var lastLocusMode by rememberSaveable {
        mutableStateOf(
            if (settings.playerControlsMode == PlayerControlsMode.NUB) {
                storedLastNonNubMode
            } else {
                settings.playerControlsMode
            },
        )
    }
    val compactControlsOnly = !isOnLocusRoute
    val showCompactControls = settings.persistentPlayerEnabled
    var locusTabTapSignal by rememberSaveable { mutableIntStateOf(0) }
    var isNowPlayingStripExpanded by rememberSaveable { mutableStateOf(false) }
    val nowPlayingStripTitle = nowPlayingSession
        ?.currentItem
        ?.let { current ->
            current.title?.takeIf { it.isNotBlank() }
                ?: current.url.takeIf { it.isNotBlank() }
                ?: "Item ${current.itemId}"
        } ?: "No active playback"
    val nowPlayingStripDomain = nowPlayingSession
        ?.currentItem
        ?.host
        ?.takeIf { it.isNotBlank() }
    val nowPlayingStripSourceUrl = nowPlayingSession
        ?.currentItem
        ?.url
        ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    val canExpandNowPlayingTitle = nowPlayingSession?.currentItem != null
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
        queueOffline -> ""
        baseUrlHint != null -> "Connection guidance"
        statusLooksError -> "Request failed"
        else -> ""
    }
    val bannerDetail = when {
        queueOffline && !statusMessage.isNullOrBlank() -> "Cannot reach server at $baseAddress\n$statusMessage"
        queueOffline -> "Cannot reach server at $baseAddress"
        baseUrlHint != null && !statusMessage.isNullOrBlank() -> "$baseUrlHint\n$statusMessage"
        baseUrlHint != null -> baseUrlHint
        statusLooksError -> statusMessage
        else -> null
    }
    val showGlobalBanner = !requiresSignIn && (queueOffline || baseUrlHint != null || statusLooksError)

    LaunchedEffect(vm, snackbarHostState) {
        vm.snackbarMessages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.message,
                actionLabel = message.actionLabel,
                duration = message.duration,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when (message.actionKey) {
                    ACTION_KEY_OPEN_DIAGNOSTICS -> nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) { launchSingleTop = true }
                    ACTION_KEY_OPEN_SETTINGS -> nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                }
            }
        }
    }

    LaunchedEffect(pendingNavigationRoute) {
        pendingNavigationRoute?.let { route ->
            nav.navigate(route) { launchSingleTop = true }
            vm.consumePendingNavigation(route)
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == previousRoute) return@LaunchedEffect
        val wasOnLocus = previousRoute.startsWith(ROUTE_LOCUS)
        val nowOnLocus = currentRoute.startsWith(ROUTE_LOCUS)
        val currentMode = settings.playerControlsMode

        if (wasOnLocus && !nowOnLocus) {
            lastLocusMode = currentMode
            if (!playbackActive && currentMode != PlayerControlsMode.NUB) {
                val nextLastNonNub = currentMode.takeIf { it != PlayerControlsMode.NUB } ?: storedLastNonNubMode
                vm.savePlayerControlsState(PlayerControlsMode.NUB, nextLastNonNub)
            }
        } else if (!wasOnLocus && nowOnLocus) {
            val restoreMode = lastLocusMode
            val restoreLastNonNub = restoreMode.takeIf { it != PlayerControlsMode.NUB } ?: storedLastNonNubMode
            if (currentMode != restoreMode || settings.playerLastNonNubMode != restoreLastNonNub) {
                vm.savePlayerControlsState(restoreMode, restoreLastNonNub)
            }
        } else if (!wasOnLocus && !nowOnLocus) {
            if (!playbackActive && currentMode != PlayerControlsMode.NUB) {
                val nextLastNonNub = lastLocusMode.takeIf { it != PlayerControlsMode.NUB } ?: storedLastNonNubMode
                vm.savePlayerControlsState(PlayerControlsMode.NUB, nextLastNonNub)
            }
        }
        previousRoute = currentRoute
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !requiresSignIn && !(isOnLocusRoute && readerChromeHidden),
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120)),
            ) {
                NavigationBar(
                    modifier = Modifier.height(68.dp),
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    navItems.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedTab == destination.route,
                            onClick = {
                                if (destination.route == ROUTE_LOCUS) {
                                    locusTabTapSignal += 1
                                }
                                nav.navigate(destination.route) { launchSingleTop = true }
                            },
                            label = { Text(destination.label) },
                            icon = {},
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showGlobalBanner && !isOnLocusRoute) {
                    StatusBanner(
                        stateLabel = bannerStateLabel,
                        summary = bannerSummary,
                        detail = bannerDetail,
                        onRetry = { vm.loadQueue() },
                        onDiagnostics = { nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) },
                    )
                }
                if (!requiresSignIn) {
                    PersistentNowPlayingStrip(
                        title = nowPlayingStripTitle,
                        domain = nowPlayingStripDomain,
                        sourceUrl = nowPlayingStripSourceUrl,
                        continuous = settings.continuousNowPlayingMarquee,
                        expanded = isNowPlayingStripExpanded,
                        onTap = {
                            if (canExpandNowPlayingTitle) {
                                isNowPlayingStripExpanded = !isNowPlayingStripExpanded
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = if (requiresSignIn) ROUTE_SIGN_IN else ROUTE_UP_NEXT,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        composable(ROUTE_SIGN_IN) {
                            SignInScreen(
                                initialServerUrl = settings.baseUrl,
                                initialAutoDownloadEnabled = settings.autoDownloadSavedArticles,
                                signInState = signInState,
                                onSignIn = vm::signIn,
                                onAutoDownloadChanged = vm::saveAutoDownloadSavedArticles,
                                onOpenAdvancedSettings = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                                onClearError = vm::clearSignInError,
                            )
                        }
                        composable(ROUTE_COLLECTIONS) {
                            CollectionsScreen(
                                vm = vm,
                                onOpenPlaylistsManager = { nav.navigate(ROUTE_COLLECTIONS_PLAYLISTS) },
                                onOpenFolder = { folderId -> nav.navigate("collections/folder/$folderId") },
                            )
                        }
                        composable(
                            ROUTE_COLLECTIONS_FOLDER,
                            arguments = listOf(navArgument("folderId") { type = NavType.IntType }),
                        ) { backStack ->
                            val folderId = backStack.arguments?.getInt("folderId") ?: return@composable
                            FolderDetailScreen(
                                vm = vm,
                                folderId = folderId,
                                onBack = { nav.popBackStack() },
                                onOpenPlaylist = {
                                    nav.navigate(ROUTE_UP_NEXT) {
                                        popUpTo(ROUTE_COLLECTIONS)
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                        composable(ROUTE_COLLECTIONS_PLAYLISTS) {
                            PlaylistsScreen(vm = vm)
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                vm = vm,
                                onOpenDiagnostics = { nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) },
                            )
                        }
                        composable(ROUTE_SETTINGS_DIAGNOSTICS) {
                            ConnectivityDiagnosticsScreen(vm = vm)
                        }
                        composable(ROUTE_LOCUS) {
                            if (requestedPlayerItemId == null) {
                                NoNowPlayingScreen(onGoQueue = { nav.navigate(ROUTE_UP_NEXT) })
                            } else {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }
                        composable(
                            route = "$ROUTE_UP_NEXT?focusItemId={focusItemId}",
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
                                onOpenPlayer = { itemId -> nav.navigate("$ROUTE_LOCUS/$itemId") },
                                onOpenDiagnostics = { nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) },
                            )
                        }
                        composable(
                            ROUTE_LOCUS_ITEM,
                            arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
                        ) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    if (!requiresSignIn && requestedPlayerItemId != null) {
                        PlayerScreen(
                            vm = vm,
                            onShowSnackbar = { message, actionLabel, actionKey ->
                                vm.showSnackbar(message, actionLabel, actionKey)
                            },
                            initialItemId = requestedPlayerItemId,
                            requestedItemId = requestedPlayerItemId,
                            startExpanded = isOnLocusRoute && routeItemId != null,
                            locusTapSignal = locusTabTapSignal,
                            onOpenItem = { nextId -> nav.navigate("$ROUTE_LOCUS/$nextId") },
                            onRequestBack = {
                                val popped = nav.popBackStack()
                                if (!popped) {
                                    nav.navigate(ROUTE_UP_NEXT) { launchSingleTop = true }
                                }
                            },
                            onOpenDiagnostics = { nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) },
                            stopPlaybackOnDispose = true,
                            compactControlsOnly = compactControlsOnly,
                            showCompactControls = showCompactControls,
                            controlsMode = controlsMode,
                            lastNonNubMode = settings.playerLastNonNubMode,
                            chevronSnapEdge = settings.playerChevronSnapEdge,
                            onControlsModeChange = { mode, lastNonNubMode ->
                                vm.savePlayerControlsState(mode, lastNonNubMode)
                                if (isOnLocusRoute) {
                                    lastLocusMode = mode
                                }
                            },
                            onPlaybackActiveChange = { active ->
                                playbackActive = active
                            },
                            onReaderChromeVisibilityChange = { hidden ->
                                readerChromeHidden = hidden
                            },
                            onChevronSnapChange = { edge ->
                                vm.savePlayerChevronSnap(edge, 0.5f)
                            },
                            modifier = if (compactControlsOnly) {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = if (isOnLocusRoute && readerChromeHidden) 12.dp else 76.dp),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersistentNowPlayingStrip(
    title: String,
    domain: String?,
    sourceUrl: String?,
    continuous: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val displayTitle = title.ifBlank { "No active playback" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap),
        ) {
            Text(
                text = "❯ ",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                label = "nowPlayingStripExpand",
            ) { isExpanded ->
                Text(
                    text = displayTitle,
                    modifier = Modifier
                        .weight(1f)
                        .let { base ->
                            if (!isExpanded) {
                                base.basicMarquee(iterations = if (continuous) Int.MAX_VALUE else 1)
                            } else {
                                base
                            }
                        },
                    maxLines = if (isExpanded) 5 else 1,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
        if (!domain.isNullOrBlank()) {
            val domainBaseModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            val domainModifier = if (sourceUrl != null) {
                domainBaseModifier
                    .clickable { uriHandler.openUri(sourceUrl) }
            } else {
                domainBaseModifier
            }
            Text(
                text = domain,
                modifier = domainModifier,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            )
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
            Text("Go to Up Next")
        }
    }
}
