package com.mimeo.android

import android.Manifest
import android.app.Application
import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mimeo.android.data.ApiClient
import com.mimeo.android.data.ApiException
import com.mimeo.android.data.AutoDownloadStatusStore
import com.mimeo.android.data.AppDatabase
import com.mimeo.android.data.NoActiveContentStore
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.AutoDownloadDiagnostics
import com.mimeo.android.model.ConnectivityDiagnosticOutcome
import com.mimeo.android.model.ConnectivityDiagnosticRow
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.model.FolderSummary
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.LocusContentMode
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.model.PlaylistEntrySummary
import com.mimeo.android.model.PlaybackChunk
import com.mimeo.android.model.PlaybackPosition
import com.mimeo.android.model.PlaybackQueueItem
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingItemAction
import com.mimeo.android.model.PendingItemActionType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.ProblemReportCategory
import com.mimeo.android.model.ProblemReportRequest
import com.mimeo.android.model.toProblemReportAttachmentText
import com.mimeo.android.model.toProblemReportAttachmentTitle
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.repository.ItemTextResult
import com.mimeo.android.repository.ItemTextPrefetchAttempt
import com.mimeo.android.repository.FoldersRepository
import com.mimeo.android.repository.NowPlayingSession
import com.mimeo.android.repository.NowPlayingSessionItem
import com.mimeo.android.repository.OfflineReadyCandidate
import com.mimeo.android.repository.PlaylistMembershipToggleResult
import com.mimeo.android.repository.PlaybackRepository
import com.mimeo.android.repository.resolveOfflineReadyItemIds
import com.mimeo.android.player.PlaybackService
import com.mimeo.android.player.PlaybackServiceBridge
import com.mimeo.android.player.PlaybackServiceSnapshot
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
import com.mimeo.android.ui.settings.PasswordChangeState
import com.mimeo.android.ui.settings.SettingsScreen
import com.mimeo.android.ui.settings.passwordChangeSuccessMessage
import com.mimeo.android.ui.settings.resolvePasswordChangeError
import com.mimeo.android.ui.settings.validatePasswordChangeInput
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.player.PlaybackEngine
import com.mimeo.android.ui.player.PlaybackEngineEvent
import com.mimeo.android.ui.player.PlaybackEngineHost
import com.mimeo.android.ui.player.PlaybackEngineSettings
import com.mimeo.android.ui.player.PlaybackEngineState
import com.mimeo.android.ui.player.PlaybackOpenIntent
import com.mimeo.android.ui.player.buildPlaybackChunks
import com.mimeo.android.ui.common.nowPlayingCapturePresentation
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
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
private const val ACTION_KEY_UNDO_ARCHIVE = "undo_archive"
private const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
private const val DEBUG_TARGET_ITEM_ID = 409
private const val INITIAL_SIGN_IN_HYDRATION_DEBUG_TAG = "MimeoSignInHydration"
private const val TEXT_LOAD_POLICY_DEBUG_TAG = "MimeoTextLoadPolicy"
private const val INITIAL_SIGN_IN_AUTO_DOWNLOAD_LIMIT = 2_147_483_647
private const val INITIAL_SIGN_IN_HYDRATION_ATTEMPTS = 3
private const val INITIAL_SIGN_IN_HYDRATION_RETRY_DELAY_MS = 300L
private const val INITIAL_SIGN_IN_BACKGROUND_HYDRATION_ATTEMPTS = 10
private const val INITIAL_SIGN_IN_BACKGROUND_HYDRATION_RETRY_DELAY_MS = 500L
private const val SMART_QUEUE_SESSION_CONTEXT_ID = -1
private const val LOCUS_CONTINUATION_DEBUG_TAG = "MimeoLocusContinue"

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

internal fun isStaleTokenAuthFailure(error: Throwable): Boolean {
    return error is ApiException && error.statusCode == 401
}

internal fun staleTokenSignInMessage(): String = "Session expired. Please sign in again."

internal fun isNoActiveContentError(error: Throwable): Boolean {
    return error is ApiException &&
        error.statusCode == 409 &&
        error.message.orEmpty().contains("No active content", ignoreCase = true)
}

internal fun isNoActiveContentAttempt(attempt: ItemTextPrefetchAttempt): Boolean {
    return !attempt.success &&
        !attempt.retryable &&
        attempt.errorSummary.orEmpty().contains("No active content", ignoreCase = true)
}

internal fun noActiveContentOfflineMessage(): String = "Not available for offline reading"

internal fun shouldReplayCompletedItem(furthestPercent: Int): Boolean {
    return furthestPercent >= DONE_PERCENT_THRESHOLD
}

internal fun resolveNextPlaylistScopedSessionIndex(
    session: NowPlayingSession,
    currentId: Int,
): Int? {
    if (session.sourcePlaylistId == null || session.sourcePlaylistId == SMART_QUEUE_SESSION_CONTEXT_ID) return null
    val idx = session.items.indexOfFirst { it.itemId == currentId }.let { if (it >= 0) it else session.currentIndex }
    if (idx >= session.items.lastIndex) return null
    return idx + 1
}

internal fun resolveSessionSourcePlaylistId(selectedPlaylistId: Int?): Int {
    return selectedPlaylistId ?: SMART_QUEUE_SESSION_CONTEXT_ID
}

data class PendingRetryBatchResult(
    val successCount: Int,
    val firstFailureResult: ShareSaveResult? = null,
)

data class QueueScrollState(
    val index: Int = 0,
    val offset: Int = 0,
    val anchorItemId: Int? = null,
)

internal data class PendingItemActionFlushEntry(
    val action: PendingItemAction,
    val sourceIds: List<Long>,
)

private enum class PendingItemActionFamily {
    FAVORITE,
    ARCHIVE,
}

private fun pendingItemActionFamily(actionType: PendingItemActionType): PendingItemActionFamily {
    return when (actionType) {
        PendingItemActionType.SET_FAVORITE -> PendingItemActionFamily.FAVORITE
        PendingItemActionType.ARCHIVE,
        PendingItemActionType.UNARCHIVE,
        -> PendingItemActionFamily.ARCHIVE
    }
}

internal fun coalescePendingItemActions(actions: List<PendingItemAction>): List<PendingItemActionFlushEntry> {
    if (actions.isEmpty()) return emptyList()
    val sorted = actions.sortedBy { it.id }
    val latestByKey = linkedMapOf<Pair<Int, PendingItemActionFamily>, PendingItemActionFlushEntry>()
    sorted.forEach { action ->
        val key = action.itemId to pendingItemActionFamily(action.actionType)
        val existing = latestByKey[key]
        latestByKey[key] = if (existing == null) {
            PendingItemActionFlushEntry(action = action, sourceIds = listOf(action.id))
        } else {
            existing.copy(action = action, sourceIds = existing.sourceIds + action.id)
        }
    }
    return latestByKey
        .values
        .sortedBy { it.action.id }
}

enum class ArchiveActionSource {
    UP_NEXT,
    LOCUS,
}

private data class ArchiveUndoSnapshot(
    val item: PlaybackQueueItem,
    val originalIndex: Int,
    val wasCached: Boolean,
    val wasNoActiveContent: Boolean,
    val source: ArchiveActionSource,
    val actionType: UndoableActionType,
)

enum class UndoableActionType {
    ARCHIVE,
    BIN,
}

data class ArchiveUndoOutcome(
    val reopenItemId: Int? = null,
    val actionType: UndoableActionType = UndoableActionType.ARCHIVE,
)

internal fun selectInitialQueueHydrationTargets(
    queueItems: List<PlaybackQueueItem>,
    cachedItemIds: Set<Int>,
    limit: Int = INITIAL_SIGN_IN_AUTO_DOWNLOAD_LIMIT,
): List<Int> {
    if (limit <= 0) return emptyList()
    return queueItems
        .sortedByDescending { it.createdAt ?: "" }
        .asSequence()
        .filterNot { isTerminalPendingProcessingStatus(it.status) }
        .map { it.itemId }
        .distinct()
        .filterNot { cachedItemIds.contains(it) }
        .take(limit)
        .toList()
}

internal fun selectAutoDownloadTargetsForNewlySurfacedItems(
    autoDownloadEnabled: Boolean,
    queueItems: List<PlaybackQueueItem>,
    previousVisibleItemIds: Set<Int>,
    cachedItemIds: Set<Int>,
    knownNoActiveContentItemIds: Set<Int>,
    includeAllVisibleUncached: Boolean = false,
): List<Int> {
    if (!autoDownloadEnabled) return emptyList()
    return queueItems
        .asSequence()
        .map { it.itemId }
        .distinct()
        .filterNot { cachedItemIds.contains(it) }
        .filterNot { knownNoActiveContentItemIds.contains(it) }
        .filter { itemId ->
            includeAllVisibleUncached || previousVisibleItemIds.isEmpty() || !previousVisibleItemIds.contains(itemId)
        }
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
    private val noActiveContentStore = NoActiveContentStore(application.applicationContext)
    private val autoDownloadStatusStore = AutoDownloadStatusStore(application.applicationContext)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _queueItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val queueItems: StateFlow<List<PlaybackQueueItem>> = _queueItems.asStateFlow()
    private val _archivedItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val archivedItems: StateFlow<List<PlaybackQueueItem>> = _archivedItems.asStateFlow()
    private val _binItems = MutableStateFlow<List<PlaybackQueueItem>>(emptyList())
    val binItems: StateFlow<List<PlaybackQueueItem>> = _binItems.asStateFlow()
    private val _pendingManualSaves = MutableStateFlow<List<PendingManualSaveItem>>(emptyList())
    val pendingManualSaves: StateFlow<List<PendingManualSaveItem>> = _pendingManualSaves.asStateFlow()
    private val _pendingItemActions = MutableStateFlow<List<PendingItemAction>>(emptyList())
    val pendingItemActions: StateFlow<List<PendingItemAction>> = _pendingItemActions.asStateFlow()
    private val pendingManualRetryMutex = Mutex()
    private val pendingItemActionFlushMutex = Mutex()
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
    private val _noActiveContentItemIds = MutableStateFlow<Set<Int>>(emptySet())
    val noActiveContentItemIds: StateFlow<Set<Int>> = _noActiveContentItemIds.asStateFlow()
    private val _autoDownloadDiagnostics = MutableStateFlow(AutoDownloadDiagnostics())
    val autoDownloadDiagnostics: StateFlow<AutoDownloadDiagnostics> = _autoDownloadDiagnostics.asStateFlow()
    private val _pendingQueueFocusItemId = MutableStateFlow<Int?>(null)
    val pendingQueueFocusItemId: StateFlow<Int?> = _pendingQueueFocusItemId.asStateFlow()
    private val queueExplainLoggedItemIds = mutableSetOf<Int>()

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
    private val _passwordChangeState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val passwordChangeState: StateFlow<PasswordChangeState> = _passwordChangeState.asStateFlow()

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
    private val playbackEngine = PlaybackEngine(
        context = application.applicationContext,
        scope = viewModelScope,
        host = object : PlaybackEngineHost {
            override fun knownProgressForItem(itemId: Int): Int = this@AppViewModel.knownProgressForItem(itemId)
            override fun knownFurthestForItem(itemId: Int): Int = this@AppViewModel.knownFurthestForItem(itemId)
            override fun getPlaybackPosition(itemId: Int): PlaybackPosition = this@AppViewModel.getPlaybackPosition(itemId)
            override fun setPlaybackPosition(itemId: Int, chunkIndex: Int, offsetInChunkChars: Int) {
                this@AppViewModel.setPlaybackPosition(itemId, chunkIndex, offsetInChunkChars)
            }

            override suspend fun postProgress(itemId: Int, percent: Int): Result<*> {
                return this@AppViewModel.postProgress(itemId, percent)
            }

            override fun shouldAutoAdvanceAfterCompletion(): Boolean = this@AppViewModel.shouldAutoAdvanceAfterCompletion()
            override fun isCurrentSessionPlaylistScoped(): Boolean = this@AppViewModel.isCurrentSessionPlaylistScoped()
            override fun currentPlaybackSettings(): PlaybackEngineSettings {
                return PlaybackEngineSettings(
                    speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                    skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                    playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                    autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                    autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                    playbackSpeed = settings.value.playbackSpeed,
                )
            }
            override suspend fun nextSessionItemId(currentId: Int): Int? = this@AppViewModel.nextSessionItemId(currentId)
            override suspend fun nextPlaylistScopedSessionItemId(currentId: Int): Int? =
                this@AppViewModel.nextPlaylistScopedSessionItemId(currentId)
            override suspend fun onPlaybackArticleEnded(itemId: Int, autoArchiveAtArticleEnd: Boolean) {
                this@AppViewModel.onPlaybackArticleEnded(itemId, autoArchiveAtArticleEnd)
            }
        },
    )
    val playbackEngineState: StateFlow<PlaybackEngineState> = playbackEngine.state
    val playbackEngineEvents: SharedFlow<PlaybackEngineEvent> = playbackEngine.events
    private var playbackServiceBinder: PlaybackService.LocalBinder? = null
    private var playbackServiceBound: Boolean = false
    private var playbackServiceBindingRequested: Boolean = false
    private var lastPushedPlaybackServiceSnapshot: PlaybackServiceSnapshot? = null
    private val autoContinueLoadMutex = Mutex()
    private var lastAutoContinueLoadKey: Pair<Int, Int>? = null
    private val playbackServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?) {
            playbackServiceBinder = service as? PlaybackService.LocalBinder
            playbackServiceBound = playbackServiceBinder != null
            playbackServiceBindingRequested = false
            pushPlaybackServiceSnapshot()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackServiceBinder = null
            playbackServiceBound = false
            playbackServiceBindingRequested = false
            if (buildPlaybackServiceSnapshot().itemId != null) {
                bindPlaybackService()
                pushPlaybackServiceSnapshot()
            }
        }
    }

    private val _nowPlayingSession = MutableStateFlow<NowPlayingSession?>(null)
    val nowPlayingSession: StateFlow<NowPlayingSession?> = _nowPlayingSession.asStateFlow()
    private val _sessionIssueMessage = MutableStateFlow<String?>(null)
    val sessionIssueMessage: StateFlow<String?> = _sessionIssueMessage.asStateFlow()
    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute.asStateFlow()
    private val _settingsScrollOffset = MutableStateFlow(0)
    val settingsScrollOffset: StateFlow<Int> = _settingsScrollOffset.asStateFlow()
    private val _queueScrollState = MutableStateFlow(QueueScrollState())
    val queueScrollState: StateFlow<QueueScrollState> = _queueScrollState.asStateFlow()
    private val authFailureMutex = Mutex()
    private var authFailureHandledThisSession = false
    private var lastArchiveUndoSnapshot: ArchiveUndoSnapshot? = null
    private val favoriteOverridesByItemId = mutableMapOf<Int, Boolean>()
    private val binnedFavoriteStateByItemId = mutableMapOf<Int, Boolean>()
    private var lastPendingAutoRetryAtMs: Long = 0L
    private var pendingInitialPostSignInHydration = false
    private var initialPostSignInHydrationJob: Job? = null
    private val connectivityManager =
        application.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var lastConnectivityRefreshAtMs: Long = 0L

    init {
        bindPlaybackService()
        PlaybackServiceBridge.onPlay = {
            playbackPlay()
        }
        PlaybackServiceBridge.onPause = {
            playbackPause(forceSync = true)
        }
        PlaybackServiceBridge.onTogglePlayPause = {
            val state = playbackEngineState.value
            if (state.isSpeaking || state.isAutoPlaying) {
                playbackPause(forceSync = true)
            } else {
                playbackPlay()
            }
        }
        PlaybackServiceBridge.snapshotProvider = {
            buildPlaybackServiceSnapshot()
        }
        viewModelScope.launch {
            playbackEngineState.collect {
                pushPlaybackServiceSnapshot()
            }
        }
        viewModelScope.launch {
            playbackEngineState.collect { state ->
                if (!state.autoPlayAfterLoad || state.currentItemId <= 0) return@collect
                resolveAutoContinueLoadAndPlay(
                    itemId = state.currentItemId,
                    reloadNonce = state.reloadNonce,
                )
            }
        }
        viewModelScope.launch {
            playbackEngineEvents.collect { event ->
                when (event) {
                    is PlaybackEngineEvent.NavigateToItem -> {
                        Log.d(
                            LOCUS_CONTINUATION_DEBUG_TAG,
                            "engineContinue navigate nextId=${event.itemId} ${continuationAuditContext()}",
                        )
                    }
                    is PlaybackEngineEvent.UiMessage -> {
                        if (event.message.contains("Completed", ignoreCase = true)) {
                            Log.d(
                                LOCUS_CONTINUATION_DEBUG_TAG,
                                "engineContinue completion uiMessage=${event.message} ${continuationAuditContext()}",
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            nowPlayingSession.collect {
                pushPlaybackServiceSnapshot()
            }
        }
        viewModelScope.launch {
            settingsStore.migrateLegacyTokenIfNeeded()
            var previous = _settings.value
            settingsStore.settingsFlow.collect { next ->
                _settings.value = next
                refreshAutoDownloadDiagnostics()
                if (next.apiToken.isBlank()) {
                    initialPostSignInHydrationJob?.cancel()
                    initialPostSignInHydrationJob = null
                    _queueItems.value = emptyList()
                    _archivedItems.value = emptyList()
                    _binItems.value = emptyList()
                    _cachedItemIds.value = emptySet()
                    _noActiveContentItemIds.value = emptySet()
                    noActiveContentStore.clear()
                    autoDownloadStatusStore.clear()
                    refreshAutoDownloadDiagnostics()
                    _queueOffline.value = false
                    _playlists.value = emptyList()
                }
                if (previous.apiToken.isBlank() && next.apiToken.isNotBlank()) {
                    authFailureHandledThisSession = false
                    pendingInitialPostSignInHydration = true
                    loadQueue()
                } else if (previous.apiToken.isNotBlank() && next.apiToken.isBlank()) {
                    pendingInitialPostSignInHydration = false
                    settingsStore.clearQueueSnapshots()
                    settingsStore.clearPendingItemActions()
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
            settingsStore.pendingItemActionsFlow.collect { pending ->
                _pendingItemActions.value = pending
            }
        }
        viewModelScope.launch {
            settingsStore.connectionTestSuccessFlow.collect { snapshots ->
                _connectionTestSuccessByMode.value = snapshots
            }
        }
        // Reactively update offline-ready state whenever AutoDownloadWorker writes to the cache.
        // This eliminates the need for a second manual refresh to see newly downloaded items.
        viewModelScope.launch {
            database.cachedItemDao().observeAllCachedItemIds()
                .distinctUntilChanged()
                .drop(1) // skip the initial emission — queue load handles the first resolve
                .collect {
                    val currentQueue = _queueItems.value
                    val currentArchive = _archivedItems.value
                    val combined = (currentQueue + currentArchive).distinctBy { item -> item.itemId }
                    if (combined.isNotEmpty()) {
                        val offlineReadyIds = resolveOfflineReadyIds(combined)
                        _cachedItemIds.update { previous -> previous + offlineReadyIds }
                        reconcileCachedItemVisibility()
                        updateAutoDownloadQueueSnapshotDiagnostics(
                            current = settings.value,
                            queueItems = currentQueue,
                            offlineReadyIds = _cachedItemIds.value,
                            knownNoActiveIds = _noActiveContentItemIds.value,
                        )
                    }
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
                }
                loadQueue()
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

    fun playbackOpenItem(itemId: Int, intent: PlaybackOpenIntent, autoPlayAfterLoad: Boolean = false) {
        playbackEngine.openItem(itemId, intent, autoPlayAfterLoad)
    }

    fun playbackReloadCurrentItem(intent: PlaybackOpenIntent) {
        playbackEngine.reloadCurrentItem(intent)
    }

    fun playbackApplyLoadedItem(payload: ItemTextResponse, chunks: List<PlaybackChunk>, requestedItemId: Int?) {
        playbackEngine.applyLoadedItem(payload = payload, loadedChunks = chunks, requestedItemId = requestedItemId)
    }

    fun playbackMaybeAutoPlayAfterLoad() {
        playbackEngine.maybeAutoPlayAfterLoad(
            settings = PlaybackEngineSettings(
                speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                playbackSpeed = settings.value.playbackSpeed,
            ),
        )
    }

    private suspend fun resolveAutoContinueLoadAndPlay(itemId: Int, reloadNonce: Int) {
        val key = itemId to reloadNonce
        if (lastAutoContinueLoadKey == key) return
        autoContinueLoadMutex.withLock {
            if (lastAutoContinueLoadKey == key) return
            val before = playbackEngineState.value
            if (!before.autoPlayAfterLoad || before.currentItemId != itemId || before.reloadNonce != reloadNonce) {
                return
            }
            Log.d(
                LOCUS_CONTINUATION_DEBUG_TAG,
                "bgAutoContinue load start item=$itemId reloadNonce=$reloadNonce ${continuationAuditContext()}",
            )
            fetchItemText(itemId, preferLocal = true, loadPolicyTag = "engine_auto_continue")
                .onSuccess { loaded ->
                    val current = playbackEngineState.value
                    if (
                        !current.autoPlayAfterLoad ||
                        current.currentItemId != itemId ||
                        current.reloadNonce != reloadNonce
                    ) {
                        return@onSuccess
                    }
                    playbackApplyLoadedItem(
                        payload = loaded.payload,
                        chunks = buildPlaybackChunks(loaded.payload),
                        requestedItemId = itemId,
                    )
                    playbackMaybeAutoPlayAfterLoad()
                    Log.d(
                        LOCUS_CONTINUATION_DEBUG_TAG,
                        "bgAutoContinue load success item=$itemId reloadNonce=$reloadNonce usingCache=${loaded.usingCache} ${continuationAuditContext()}",
                    )
                    if (!playbackEngineState.value.autoPlayAfterLoad) {
                        lastAutoContinueLoadKey = key
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    val reason = when {
                        error is ApiException && error.statusCode == 401 -> "auth"
                        isNetworkError(error) -> "network"
                        else -> "other"
                    }
                    Log.d(
                        LOCUS_CONTINUATION_DEBUG_TAG,
                        "bgAutoContinue load fail item=$itemId reloadNonce=$reloadNonce reason=$reason " +
                            "err=${error.message} ${continuationAuditContext()}",
                    )
                }
        }
    }

    private fun continuationAuditContext(): String {
        val appContext = getApplication<Application>().applicationContext
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val processState = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processState)
        val interactive = when {
            power == null -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH -> power.isInteractive
            else -> {
                @Suppress("DEPRECATION")
                power.isScreenOn
            }
        }
        val locked = keyguard?.isKeyguardLocked ?: false
        val background = processState.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        return "interactive=$interactive locked=$locked background=$background"
    }

    fun playbackPlay() {
        playbackEngine.play(
            settings = PlaybackEngineSettings(
                speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                playbackSpeed = settings.value.playbackSpeed,
            ),
        )
    }

    fun playbackApplyCurrentSettings() {
        playbackEngine.applyPlaybackSpeed(
            settings = PlaybackEngineSettings(
                speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                autoAdvanceOnCompletion = settings.value.autoAdvanceOnCompletion,
                autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                playbackSpeed = settings.value.playbackSpeed,
            ),
        )
    }

    fun playbackPause(forceSync: Boolean) {
        playbackEngine.pause(forceSync = forceSync)
    }

    fun playbackSeekToChunkOffset(chunkIndex: Int, offsetInChunkChars: Int, keepPlaying: Boolean) {
        playbackEngine.seekToChunkOffset(
            chunkIndex = chunkIndex,
            offsetInChunkChars = offsetInChunkChars,
            keepPlaying = keepPlaying,
        )
    }

    fun playbackAdvanceToNextItem() {
        playbackEngine.advanceToNextItem()
    }

    private fun bindPlaybackService() {
        if (playbackServiceBound || playbackServiceBindingRequested) return
        val appContext = getApplication<Application>().applicationContext
        playbackServiceBindingRequested = true
        val bound = appContext.bindService(
            Intent(appContext, PlaybackService::class.java),
            playbackServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) {
            playbackServiceBindingRequested = false
        }
    }

    private fun unbindPlaybackService() {
        if (!playbackServiceBound) return
        val appContext = getApplication<Application>().applicationContext
        appContext.unbindService(playbackServiceConnection)
        playbackServiceBinder = null
        playbackServiceBound = false
        playbackServiceBindingRequested = false
        lastPushedPlaybackServiceSnapshot = null
    }

    private fun buildPlaybackServiceSnapshot(): PlaybackServiceSnapshot {
        val engine = playbackEngineState.value
        val sessionItem = nowPlayingSession.value?.currentItem
        val itemId = if (engine.currentItemId > 0) engine.currentItemId else sessionItem?.itemId
        val resolvedTitle = itemId?.let { playbackItemId ->
            nowPlayingSession.value
                ?.items
                ?.firstOrNull { it.itemId == playbackItemId }
                ?.let { item ->
                    item.title?.takeIf { it.isNotBlank() } ?: item.url.takeIf { it.isNotBlank() }
                }
                ?: _queueItems.value
                    .firstOrNull { it.itemId == playbackItemId }
                    ?.let { item ->
                        item.title?.takeIf { it.isNotBlank() } ?: item.url.takeIf { it.isNotBlank() }
                    }
                ?: _archivedItems.value
                    .firstOrNull { it.itemId == playbackItemId }
                    ?.let { item ->
                        item.title?.takeIf { it.isNotBlank() } ?: item.url.takeIf { it.isNotBlank() }
                    }
                ?: _binItems.value
                    .firstOrNull { it.itemId == playbackItemId }
                    ?.let { item ->
                        item.title?.takeIf { it.isNotBlank() } ?: item.url.takeIf { it.isNotBlank() }
                    }
        } ?: sessionItem?.title?.takeIf { it.isNotBlank() }
            ?: sessionItem?.url?.takeIf { it.isNotBlank() }
            ?: "Mimeo playback"
        return PlaybackServiceSnapshot(
            itemId = itemId,
            title = resolvedTitle,
            isPlaying = engine.isSpeaking || engine.isAutoPlaying,
        )
    }

    private fun pushPlaybackServiceSnapshot() {
        val snapshot = buildPlaybackServiceSnapshot()
        val previous = lastPushedPlaybackServiceSnapshot
        val shouldSkipBecauseNoChange = snapshot == previous &&
            (snapshot.itemId == null || playbackServiceBinder != null)
        if (shouldSkipBecauseNoChange) return
        lastPushedPlaybackServiceSnapshot = snapshot
        val appContext = getApplication<Application>().applicationContext
        if (snapshot.itemId != null) {
            if (!playbackServiceBound) {
                bindPlaybackService()
            }
        }
        if (snapshot.itemId != null && (previous?.itemId == null || playbackServiceBinder == null)) {
            val startIntent = Intent(appContext, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SYNC_FROM_BRIDGE
            }
            ContextCompat.startForegroundService(appContext, startIntent)
        } else if (snapshot.itemId == null && previous?.itemId != null) {
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
        }
        playbackServiceBinder?.updateSnapshot(snapshot)
    }

    fun saveSettings(
        baseUrl: String,
        connectionMode: ConnectionMode,
        localBaseUrl: String,
        lanBaseUrl: String,
        remoteBaseUrl: String,
        token: String,
        autoAdvanceOnCompletion: Boolean,
        autoArchiveAtArticleEnd: Boolean,
        speakTitleBeforeArticle: Boolean,
        skipDuplicateOpeningAfterTitleIntro: Boolean,
        playCompletionCueAtArticleEnd: Boolean,
        keepScreenOnDuringSession: Boolean,
        persistentPlayerEnabled: Boolean,
        autoScrollWhileListening: Boolean,
        locusTabReturnsToPlaybackPosition: Boolean,
        continuousNowPlayingMarquee: Boolean,
        forceSentenceHighlightFallback: Boolean,
        showPlaybackDiagnostics: Boolean,
        showAutoDownloadDiagnostics: Boolean,
        showQueueCaptureMetadata: Boolean,
        showPendingOutcomeSimulator: Boolean,
        ttsVoiceName: String,
        keepShareResultNotifications: Boolean,
        autoDownloadSavedArticles: Boolean,
        autoCacheFavoritedItems: Boolean,
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
                autoArchiveAtArticleEnd = autoArchiveAtArticleEnd,
                speakTitleBeforeArticle = speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = playCompletionCueAtArticleEnd,
                keepScreenOnDuringSession = keepScreenOnDuringSession,
                persistentPlayerEnabled = persistentPlayerEnabled,
                autoScrollWhileListening = autoScrollWhileListening,
                locusTabReturnsToPlaybackPosition = locusTabReturnsToPlaybackPosition,
                continuousNowPlayingMarquee = continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = forceSentenceHighlightFallback,
                showPlaybackDiagnostics = showPlaybackDiagnostics,
                showAutoDownloadDiagnostics = showAutoDownloadDiagnostics,
                showQueueCaptureMetadata = showQueueCaptureMetadata,
                showPendingOutcomeSimulator = showPendingOutcomeSimulator,
                ttsVoiceName = ttsVoiceName,
                keepShareResultNotifications = keepShareResultNotifications,
                autoDownloadSavedArticles = autoDownloadSavedArticles,
                autoCacheFavoritedItems = autoCacheFavoritedItems,
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
                autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                keepScreenOnDuringSession = settings.value.keepScreenOnDuringSession,
                persistentPlayerEnabled = settings.value.persistentPlayerEnabled,
                autoScrollWhileListening = settings.value.autoScrollWhileListening,
                locusTabReturnsToPlaybackPosition = settings.value.locusTabReturnsToPlaybackPosition,
                continuousNowPlayingMarquee = settings.value.continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = settings.value.forceSentenceHighlightFallback,
                showPlaybackDiagnostics = settings.value.showPlaybackDiagnostics,
                showAutoDownloadDiagnostics = settings.value.showAutoDownloadDiagnostics,
                showQueueCaptureMetadata = settings.value.showQueueCaptureMetadata,
                showPendingOutcomeSimulator = settings.value.showPendingOutcomeSimulator,
                ttsVoiceName = settings.value.ttsVoiceName,
                keepShareResultNotifications = settings.value.keepShareResultNotifications,
                autoDownloadSavedArticles = settings.value.autoDownloadSavedArticles,
                autoCacheFavoritedItems = settings.value.autoCacheFavoritedItems,
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
                autoArchiveAtArticleEnd = settings.value.autoArchiveAtArticleEnd,
                speakTitleBeforeArticle = settings.value.speakTitleBeforeArticle,
                skipDuplicateOpeningAfterTitleIntro = settings.value.skipDuplicateOpeningAfterTitleIntro,
                playCompletionCueAtArticleEnd = settings.value.playCompletionCueAtArticleEnd,
                keepScreenOnDuringSession = settings.value.keepScreenOnDuringSession,
                persistentPlayerEnabled = settings.value.persistentPlayerEnabled,
                autoScrollWhileListening = settings.value.autoScrollWhileListening,
                locusTabReturnsToPlaybackPosition = settings.value.locusTabReturnsToPlaybackPosition,
                continuousNowPlayingMarquee = settings.value.continuousNowPlayingMarquee,
                forceSentenceHighlightFallback = settings.value.forceSentenceHighlightFallback,
                showPlaybackDiagnostics = settings.value.showPlaybackDiagnostics,
                showAutoDownloadDiagnostics = settings.value.showAutoDownloadDiagnostics,
                showQueueCaptureMetadata = settings.value.showQueueCaptureMetadata,
                showPendingOutcomeSimulator = settings.value.showPendingOutcomeSimulator,
                ttsVoiceName = settings.value.ttsVoiceName,
                keepShareResultNotifications = settings.value.keepShareResultNotifications,
                autoDownloadSavedArticles = settings.value.autoDownloadSavedArticles,
                autoCacheFavoritedItems = settings.value.autoCacheFavoritedItems,
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

    fun saveAutoAdvanceOnCompletion(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(autoAdvanceOnCompletion = enabled)
        viewModelScope.launch {
            settingsStore.saveAutoAdvanceOnCompletion(enabled)
        }
    }

    fun saveAutoArchiveAtArticleEnd(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(autoArchiveAtArticleEnd = enabled)
        viewModelScope.launch {
            settingsStore.saveAutoArchiveAtArticleEnd(enabled)
        }
    }

    fun saveSpeakTitleBeforeArticle(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(speakTitleBeforeArticle = enabled)
        viewModelScope.launch {
            settingsStore.saveSpeakTitleBeforeArticle(enabled)
        }
    }

    fun saveSkipDuplicateOpeningAfterTitleIntro(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(skipDuplicateOpeningAfterTitleIntro = enabled)
        viewModelScope.launch {
            settingsStore.saveSkipDuplicateOpeningAfterTitleIntro(enabled)
        }
    }

    fun savePlayCompletionCueAtArticleEnd(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(playCompletionCueAtArticleEnd = enabled)
        viewModelScope.launch {
            settingsStore.savePlayCompletionCueAtArticleEnd(enabled)
        }
    }

    fun saveKeepScreenOnDuringSession(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(keepScreenOnDuringSession = enabled)
        viewModelScope.launch {
            settingsStore.saveKeepScreenOnDuringSession(enabled)
        }
    }

    fun saveLocusTabReturnsToPlaybackPosition(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(locusTabReturnsToPlaybackPosition = enabled)
        viewModelScope.launch {
            settingsStore.saveLocusTabReturnsToPlaybackPosition(enabled)
        }
    }

    fun saveLocusContentMode(mode: LocusContentMode) {
        val current = settings.value
        _settings.value = current.copy(locusContentMode = mode)
        viewModelScope.launch {
            settingsStore.saveLocusContentMode(mode)
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

    fun saveAutoCacheFavoritedItems(enabled: Boolean) {
        val current = settings.value
        _settings.value = current.copy(autoCacheFavoritedItems = enabled)
        viewModelScope.launch {
            settingsStore.saveAutoCacheFavoritedItems(enabled)
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

    fun clearPasswordChangeState() {
        _passwordChangeState.value = PasswordChangeState.Idle
    }

    fun signOut() {
        viewModelScope.launch {
            authFailureHandledThisSession = false
            settingsStore.saveTokenOnly("")
            requestNavigation(ROUTE_SIGN_IN)
        }
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmNewPassword: String,
    ) {
        val validationError = validatePasswordChangeInput(
            currentPassword = currentPassword,
            newPassword = newPassword,
            confirmNewPassword = confirmNewPassword,
        )
        if (validationError != null) {
            _passwordChangeState.value = PasswordChangeState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _passwordChangeState.value = PasswordChangeState.Submitting
            val current = settings.value
            try {
                apiClient.postChangePassword(
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                    oldPassword = currentPassword,
                    newPassword = newPassword,
                )
                val message = passwordChangeSuccessMessage()
                _passwordChangeState.value = PasswordChangeState.Success(message)
                showSnackbar(message)
            } catch (error: Exception) {
                val resolution = resolvePasswordChangeError(error)
                if (resolution.staleAuth && handleAuthFailureIfNeeded(error)) {
                    _passwordChangeState.value = PasswordChangeState.Idle
                } else {
                    _passwordChangeState.value = PasswordChangeState.Error(resolution.message)
                }
            }
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
                authFailureHandledThisSession = false
                _signInState.value = SignInState.Idle
                requestNavigation(ROUTE_UP_NEXT)
            } catch (error: Exception) {
                _signInState.value = SignInState.Error(resolveSignInErrorMessage(error))
            }
        }
    }

    private suspend fun handleAuthFailureIfNeeded(error: Throwable): Boolean {
        if (!isStaleTokenAuthFailure(error)) return false
        return authFailureMutex.withLock {
            if (authFailureHandledThisSession || settings.value.apiToken.isBlank()) {
                true
            } else {
                authFailureHandledThisSession = true
                settingsStore.saveTokenOnly("")
                _signInState.value = SignInState.Error(staleTokenSignInMessage())
                requestNavigation(ROUTE_SIGN_IN)
                true
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
                val retrySummary = retryAllPendingManualSaves()
                val queueResult = if (retrySummary.successCount >= 0) {
                    loadQueueOnce(autoRetryPendingSaves = false)
                } else {
                    Result.success(Unit)
                }
                if (queueResult.isSuccess && settings.value.apiToken.isNotBlank() && _signInState.value !is SignInState.Error) {
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
                }
            } catch (e: ApiException) {
                if (handleAuthFailureIfNeeded(e)) {
                    return@launch
                }
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

    suspend fun loadQueueOnce(
        autoRetryPendingSaves: Boolean = true,
        forceAutoDownloadAllVisibleUncached: Boolean = false,
    ): Result<Unit> = queueLoadMutex.withLock {
        val current = settings.value
        val wasOffline = _queueOffline.value
        val previousVisibleItemIds = _queueItems.value.mapTo(linkedSetOf()) { it.itemId }
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
        val flushedPendingActions = flushPendingItemActions()
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
            val queueItems = applyFavoriteOverrides(queue.items)
            var offlineReadyIds = resolveOfflineReadyIds(queueItems)
            offlineReadyIds = runInitialPostSignInHydrationIfNeeded(
                current = current,
                queueItems = queueItems,
                cachedItemIds = offlineReadyIds,
            )
            offlineReadyIds = ensurePendingItemsOfflineReady(
                queueItems = queueItems,
                selectedPlaylistId = current.selectedPlaylistId,
                offlineReadyIds = offlineReadyIds,
                current = current,
            )
            offlineReadyIds = autoDownloadNewlySurfacedQueueItems(
                current = current,
                queueItems = queueItems,
                previousVisibleItemIds = previousVisibleItemIds,
                offlineReadyIds = offlineReadyIds,
                includeAllVisibleUncached = forceAutoDownloadAllVisibleUncached,
            )
            _queueItems.value = queueItems
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
            _cachedItemIds.update { previous -> previous + offlineReadyIds }
            autoCacheFavoritedItemsIfEnabled(current = current, queueItems = queueItems)
            reconcileCachedItemVisibility()
            _noActiveContentItemIds.value = retainKnownNoActiveContentIds(
                queueItems = queueItems,
                existing = _noActiveContentItemIds.value,
            )
            updateAutoDownloadQueueSnapshotDiagnostics(
                current = current,
                queueItems = queueItems,
                offlineReadyIds = offlineReadyIds,
                knownNoActiveIds = _noActiveContentItemIds.value,
            )
            settingsStore.saveQueueSnapshot(current.selectedPlaylistId, queue.copy(items = queueItems))
            syncPendingSaveProcessingFailures(
                queueItems = queueItems,
                selectedPlaylistId = current.selectedPlaylistId,
                baseUrl = current.baseUrl,
                token = current.apiToken,
            )
            reconcilePendingSavesWithQueue(
                queueItems = queueItems,
                selectedPlaylistId = current.selectedPlaylistId,
                baseUrl = current.baseUrl,
                token = current.apiToken,
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
                val previousVisibleAfterAutoRetryIds = _queueItems.value.mapTo(linkedSetOf()) { it.itemId }
                val refreshedQueueResult = repository.loadQueueAndPrefetch(
                    current.baseUrl,
                    current.apiToken,
                    playlistId = current.selectedPlaylistId,
                    prefetchCount = 0,
                )
                val refreshedQueue = refreshedQueueResult.payload
                val refreshedQueueItems = applyFavoriteOverrides(refreshedQueue.items)
                var refreshedOfflineReadyIds = resolveOfflineReadyIds(refreshedQueueItems)
                refreshedOfflineReadyIds = ensurePendingItemsOfflineReady(
                    queueItems = refreshedQueueItems,
                    selectedPlaylistId = current.selectedPlaylistId,
                    offlineReadyIds = refreshedOfflineReadyIds,
                    current = current,
                )
                refreshedOfflineReadyIds = autoDownloadNewlySurfacedQueueItems(
                    current = current,
                    queueItems = refreshedQueueItems,
                    previousVisibleItemIds = previousVisibleAfterAutoRetryIds,
                    offlineReadyIds = refreshedOfflineReadyIds,
                    includeAllVisibleUncached = forceAutoDownloadAllVisibleUncached,
                )
                _queueItems.value = refreshedQueueItems
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
                _cachedItemIds.update { previous -> previous + refreshedOfflineReadyIds }
                autoCacheFavoritedItemsIfEnabled(current = current, queueItems = refreshedQueueItems)
                reconcileCachedItemVisibility()
                _noActiveContentItemIds.value = retainKnownNoActiveContentIds(
                    queueItems = refreshedQueueItems,
                    existing = _noActiveContentItemIds.value,
                )
                updateAutoDownloadQueueSnapshotDiagnostics(
                    current = current,
                    queueItems = refreshedQueueItems,
                    offlineReadyIds = refreshedOfflineReadyIds,
                    knownNoActiveIds = _noActiveContentItemIds.value,
                )
                settingsStore.saveQueueSnapshot(current.selectedPlaylistId, refreshedQueue.copy(items = refreshedQueueItems))
                syncPendingSaveProcessingFailures(
                    queueItems = refreshedQueueItems,
                    selectedPlaylistId = current.selectedPlaylistId,
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                )
                reconcilePendingSavesWithQueue(
                    queueItems = refreshedQueueItems,
                    selectedPlaylistId = current.selectedPlaylistId,
                    baseUrl = current.baseUrl,
                    token = current.apiToken,
                )
                _statusMessage.value = "Queue loaded (${refreshedQueue.count})"
            } else if (flushedPendingActions > 0) {
                _statusMessage.value = "Synced offline actions"
            }
            Result.success(Unit)
        } catch (e: ApiException) {
            if (handleAuthFailureIfNeeded(e)) {
                _queueOffline.value = false
                updateSyncBadgeState()
                return@withLock Result.failure(e)
            }
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

    private suspend fun enqueuePendingItemAction(
        itemId: Int,
        actionType: PendingItemActionType,
        favorited: Boolean? = null,
    ) {
        val persisted = settingsStore.enqueuePendingItemAction(
            itemId = itemId,
            actionType = actionType,
            favorited = favorited,
        )
        val family = pendingItemActionFamily(actionType)
        _pendingItemActions.update { existing ->
            val withoutSameFamily = existing.filterNot { pending ->
                pending.itemId == itemId && pendingItemActionFamily(pending.actionType) == family
            }
            listOf(persisted) + withoutSameFamily
        }
    }

    private suspend fun flushPendingItemActions(): Int {
        if (!pendingItemActionFlushMutex.tryLock()) return 0
        try {
            val current = settings.value
            if (current.apiToken.isBlank()) return 0
            val pending = _pendingItemActions.value
            if (pending.isEmpty()) return 0
            val coalesced = coalescePendingItemActions(pending)
            var successCount = 0
            coalesced.forEach { entry ->
                val action = entry.action
                val result = runCatching {
                    when (action.actionType) {
                        PendingItemActionType.SET_FAVORITE -> {
                            repository.setFavoriteState(
                                baseUrl = current.baseUrl,
                                token = current.apiToken,
                                itemId = action.itemId,
                                favorited = action.favorited == true,
                            )
                        }
                        PendingItemActionType.ARCHIVE -> {
                            repository.archiveItem(
                                baseUrl = current.baseUrl,
                                token = current.apiToken,
                                itemId = action.itemId,
                            )
                        }
                        PendingItemActionType.UNARCHIVE -> {
                            repository.unarchiveItem(
                                baseUrl = current.baseUrl,
                                token = current.apiToken,
                                itemId = action.itemId,
                            )
                            repository.toggleCompletion(
                                baseUrl = current.baseUrl,
                                token = current.apiToken,
                                itemId = action.itemId,
                                markDone = false,
                            )
                        }
                    }
                }
                if (result.isSuccess) {
                    entry.sourceIds.forEach { sourceId ->
                        settingsStore.removePendingItemAction(sourceId)
                    }
                    successCount += 1
                    _queueOffline.value = false
                    updateSyncBadgeState()
                } else {
                    val error = result.exceptionOrNull()
                    if (error != null) {
                        if (handleAuthFailureIfNeeded(error)) return successCount
                        if (isNetworkError(error)) {
                            _queueOffline.value = true
                            updateSyncBadgeState()
                            return successCount
                        }
                    }
                    return successCount
                }
            }
            return successCount
        } finally {
            pendingItemActionFlushMutex.unlock()
        }
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
        reconcileCachedItemVisibility()
        _noActiveContentItemIds.value = retainKnownNoActiveContentIds(
            queueItems = snapshot.items,
            existing = _noActiveContentItemIds.value,
        )
        updateAutoDownloadQueueSnapshotDiagnostics(
            current = settings.value,
            queueItems = snapshot.items,
            offlineReadyIds = _cachedItemIds.value,
            knownNoActiveIds = _noActiveContentItemIds.value,
        )
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

    private suspend fun autoCacheFavoritedItemsIfEnabled(
        current: AppSettings,
        queueItems: List<PlaybackQueueItem>,
    ) {
        if (!current.autoCacheFavoritedItems) return
        if (current.apiToken.isBlank()) return
        val uncachedFavorites = queueItems
            .asSequence()
            .filter { it.isFavorited }
            .map { it.itemId }
            .distinct()
            .filterNot { _cachedItemIds.value.contains(it) }
            .filterNot { _noActiveContentItemIds.value.contains(it) }
            .take(8)
            .toList()
        uncachedFavorites.forEach { itemId ->
            runCatching { fetchItemText(itemId) }
        }
    }

    private suspend fun autoDownloadNewlySurfacedQueueItems(
        current: AppSettings,
        queueItems: List<PlaybackQueueItem>,
        previousVisibleItemIds: Set<Int>,
        offlineReadyIds: Set<Int>,
        includeAllVisibleUncached: Boolean,
    ): Set<Int> {
        // Merge no-active-content IDs persisted by AutoDownloadWorker from previous runs.
        val persistedNoContent = withContext(Dispatchers.IO) { noActiveContentStore.getAll() }
        if (persistedNoContent.isNotEmpty()) {
            _noActiveContentItemIds.update { existing ->
                retainKnownNoActiveContentIds(queueItems, existing + persistedNoContent)
            }
            // Prune stale IDs to keep the store small.
            val currentQueueIds = queueItems.mapTo(linkedSetOf()) { it.itemId }
            withContext(Dispatchers.IO) { noActiveContentStore.retainOnly(currentQueueIds) }
        }

        val targets = selectAutoDownloadTargetsForNewlySurfacedItems(
            autoDownloadEnabled = current.autoDownloadSavedArticles,
            queueItems = queueItems,
            previousVisibleItemIds = previousVisibleItemIds,
            cachedItemIds = offlineReadyIds,
            knownNoActiveContentItemIds = _noActiveContentItemIds.value,
            includeAllVisibleUncached = includeAllVisibleUncached,
        )
        val distinctQueueItemIds = queueItems.mapTo(linkedSetOf()) { it.itemId }
        val skippedCachedCount = distinctQueueItemIds.count { offlineReadyIds.contains(it) }
        val skippedNoActiveCount = distinctQueueItemIds.count { _noActiveContentItemIds.value.contains(it) }
        autoDownloadStatusStore.recordSchedule(
            candidateCount = distinctQueueItemIds.size,
            queuedCount = targets.size,
            skippedCachedCount = skippedCachedCount,
            skippedNoActiveCount = skippedNoActiveCount,
            includeAllVisibleUncached = includeAllVisibleUncached,
        )
        refreshAutoDownloadDiagnostics()
        if (targets.isEmpty()) return offlineReadyIds

        WorkScheduler.enqueueAutoDownload(
            getApplication<Application>().applicationContext,
            targets,
        )
        // Downloads happen asynchronously; cached IDs will refresh on the next queue load.
        return offlineReadyIds
    }

    private fun updateAutoDownloadQueueSnapshotDiagnostics(
        current: AppSettings,
        queueItems: List<PlaybackQueueItem>,
        offlineReadyIds: Set<Int>,
        knownNoActiveIds: Set<Int>,
    ) {
        autoDownloadStatusStore.recordQueueSnapshot(
            autoDownloadEnabled = current.autoDownloadSavedArticles,
            queueItemCount = queueItems.size,
            offlineReadyCount = offlineReadyIds.size,
            knownNoActiveCount = knownNoActiveIds.size,
        )
        refreshAutoDownloadDiagnostics()
    }

    private fun refreshAutoDownloadDiagnostics() {
        _autoDownloadDiagnostics.value = autoDownloadStatusStore.read()
    }

    private suspend fun reconcilePendingSavesWithQueue(
        queueItems: List<PlaybackQueueItem>,
        selectedPlaylistId: Int?,
        baseUrl: String,
        token: String,
    ) {
        val confirmedPendingIds = linkedSetOf<Long>()
        val summaryCache = mutableMapOf<Int, com.mimeo.android.model.ArticleSummary?>()
        _pendingManualSaves.value
            .forEach { pending ->
                pending.resolvedItemId?.let { resolvedItemId ->
                    val summary = summaryCache.getOrPut(resolvedItemId) {
                        runCatching {
                            apiClient.getItemSummary(baseUrl, token, resolvedItemId)
                        }.getOrNull()
                    }
                    if (summary != null) {
                        val summaryHasFailure =
                            isTerminalPendingProcessingStatus(summary.status) ||
                                !summary.failureReason.isNullOrBlank()
                        val summaryStillProcessing = isProcessingQueueStatus(summary.status)
                        if (!summaryHasFailure && !summaryStillProcessing) {
                            if (
                                BuildConfig.DEBUG &&
                                queueItems.none { it.itemId == resolvedItemId } &&
                                queueExplainLoggedItemIds.add(resolvedItemId)
                            ) {
                                val explain = runCatching {
                                    apiClient.getQueueExplain(baseUrl, token, resolvedItemId)
                                }.getOrNull()
                                Log.d(
                                    QUEUE_DEBUG_TAG,
                                    "queueExplain itemId=$resolvedItemId eligible=${explain?.eligible} exclusionReasons=${explain?.exclusionReasons?.joinToString("|").orEmpty()} sortNote=${explain?.sortNote.orEmpty()}",
                                )
                            }
                            confirmedPendingIds += pending.id
                            return@forEach
                        }
                    }
                }
                if (pending.destinationPlaylistId != selectedPlaylistId && pending.destinationPlaylistId != null) {
                    return@forEach
                }
                val matchedQueueItem = queueItems.firstOrNull { queueItem ->
                    pendingMatchesQueueItem(pending, queueItem)
                }
                val queueConfirmed = matchedQueueItem != null &&
                    !hasFailedQueueStatus(matchedQueueItem) &&
                    !isProcessingQueueStatus(matchedQueueItem.status)
                if (queueConfirmed) {
                    confirmedPendingIds += pending.id
                    return@forEach
                }
            }

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

    private fun isProcessingQueueStatus(status: String?): Boolean {
        val normalized = status.orEmpty().trim().lowercase(Locale.US)
        if (normalized.isBlank()) return false
        return normalized.contains("pending") ||
            normalized.contains("processing") ||
            normalized.contains("queued") ||
            normalized.contains("fetch") ||
            normalized.contains("extract")
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
        if (!_queueOffline.value && _pendingManualSaves.value.isEmpty() && _pendingItemActions.value.isEmpty()) return
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
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
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
                _noActiveContentItemIds.value = emptySet()
                noActiveContentStore.clear()
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

    fun saveShowPlaybackDiagnostics(enabled: Boolean) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(showPlaybackDiagnostics = enabled) }
            settingsStore.saveShowPlaybackDiagnostics(enabled)
        }
    }

    fun saveShowAutoDownloadDiagnostics(enabled: Boolean) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(showAutoDownloadDiagnostics = enabled) }
            settingsStore.saveShowAutoDownloadDiagnostics(enabled)
        }
    }

    fun saveShowQueueCaptureMetadata(enabled: Boolean) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(showQueueCaptureMetadata = enabled) }
            settingsStore.saveShowQueueCaptureMetadata(enabled)
        }
    }

    fun saveShowPendingOutcomeSimulator(enabled: Boolean) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(showPendingOutcomeSimulator = enabled) }
            settingsStore.saveShowPendingOutcomeSimulator(enabled)
        }
    }

    fun saveTtsVoiceName(ttsVoiceName: String) {
        viewModelScope.launch {
            _settings.update { current -> current.copy(ttsVoiceName = ttsVoiceName.trim()) }
            settingsStore.saveTtsVoiceName(ttsVoiceName)
        }
    }

    fun setSettingsScrollOffset(offset: Int) {
        _settingsScrollOffset.value = offset.coerceAtLeast(0)
    }

    fun setQueueScrollState(index: Int, offset: Int, anchorItemId: Int?) {
        _queueScrollState.value = QueueScrollState(
            index = index.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0),
            anchorItemId = anchorItemId,
        )
    }

    fun clearQueueScrollState() {
        _queueScrollState.value = QueueScrollState()
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
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
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
                val healthDeferred = async {
                    runRawCheck(
                        baseUrl = baseUrl,
                        token = token,
                        path = "/health",
                        name = "health",
                        mode = current.connectionMode,
                    )
                }
                val debugVersionDeferred = async {
                    runRawCheck(
                        baseUrl = baseUrl,
                        token = token,
                        path = "/debug/version",
                        name = "debug/version",
                        mode = current.connectionMode,
                    )
                }
                val debugPythonDeferred = async {
                    runRawCheck(
                        baseUrl = baseUrl,
                        token = token,
                        path = "/debug/python",
                        name = "debug/python",
                        mode = current.connectionMode,
                    )
                }

                val health = healthDeferred.await()
                rows += health
                if (health.outcome != ConnectivityDiagnosticOutcome.PASS && health.hint != null) {
                    lastError = health.hint
                }

                val debugVersion = debugVersionDeferred.await()
                rows += if (debugVersion.outcome == ConnectivityDiagnosticOutcome.PASS) {
                    val gitSha = extractJsonField(debugVersion.detail, "git_sha") ?: "unknown"
                    debugVersion.copy(detail = withProbeMeta("git_sha=$gitSha", debugVersion.detail))
                } else {
                    debugVersion
                }
                if (debugVersion.outcome != ConnectivityDiagnosticOutcome.PASS && debugVersion.hint != null) {
                    lastError = debugVersion.hint
                }

                val debugPython = debugPythonDeferred.await()
                rows += if (debugPython.outcome == ConnectivityDiagnosticOutcome.PASS) {
                    val sysPrefix = extractJsonField(debugPython.detail, "sys_prefix") ?: "unknown"
                    debugPython.copy(detail = withProbeMeta("sys_prefix=$sysPrefix", debugPython.detail))
                } else {
                    debugPython
                }
                if (debugPython.outcome != ConnectivityDiagnosticOutcome.PASS && debugPython.hint != null) {
                    lastError = debugPython.hint
                }
            } catch (e: Exception) {
                val message = e.message ?: "diagnostics failed"
                rows += diagnosticRow(
                    name = "diagnostics",
                    url = baseUrl,
                    outcome = ConnectivityDiagnosticOutcome.FAIL,
                    detail = "error=$message",
                    hint = ConnectionTestMessageResolver.forDiagnosticsException(
                        mode = current.connectionMode,
                        baseUrl = baseUrl,
                        message = message,
                    ),
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
                if (handleAuthFailureIfNeeded(e)) {
                    updateSyncBadgeState()
                    return@launch
                }
                if (isNetworkError(e)) {
                    _queueOffline.value = true
                }
                updateSyncBadgeState()
            } finally {
                refreshPendingCount()
            }
        }
    }

    suspend fun fetchItemText(
        itemId: Int,
        preferLocal: Boolean = false,
        loadPolicyTag: String = "unspecified",
    ): Result<ItemTextResult> {
        val current = settings.value
        val expectedVersion = expectedActiveVersionFor(itemId)
        if (BuildConfig.DEBUG) {
            val policy = if (preferLocal) "cache_first" else "network_first"
            Log.d(
                TEXT_LOAD_POLICY_DEBUG_TAG,
                "trigger=$loadPolicyTag item=$itemId requested_policy=$policy queueOffline=${_queueOffline.value}",
            )
        }
        return try {
            val loaded = repository.getItemText(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                itemId = itemId,
                expectedActiveVersionId = expectedVersion,
                preferLocal = preferLocal,
            )
            if (BuildConfig.DEBUG) {
                Log.d(
                    TEXT_LOAD_POLICY_DEBUG_TAG,
                    "trigger=$loadPolicyTag item=$itemId resolved_source=${if (loaded.usingCache) "cache" else "network"}",
                )
            }
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
            _noActiveContentItemIds.update { previous -> previous - itemId - relatedIds }
            updateSyncBadgeState()
            Result.success(loaded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TEXT_LOAD_POLICY_DEBUG_TAG,
                    "trigger=$loadPolicyTag item=$itemId failed=${error::class.simpleName}:${error.message.orEmpty()}",
                )
            }
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNoActiveContentError(error)) {
                _noActiveContentItemIds.update { previous -> previous + itemId }
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                updateSyncBadgeState()
            }
            Result.failure(error)
        }
    }

    suspend fun downloadItemForOffline(itemId: Int): Result<Unit> {
        return fetchItemText(itemId, loadPolicyTag = "manual_download_offline").map { Unit }
    }

    fun warmItemTextForPlayer(itemId: Int) {
        viewModelScope.launch {
            fetchItemText(itemId, loadPolicyTag = "queue_row_warm_open")
        }
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
            val queueItems = applyFavoriteOverrides(queue.items)
            _queueItems.value = queueItems
            val appliedSnapshot = queueResult.debugSnapshot.copy(
                appliedItemCount = _queueItems.value.size,
                appliedContains409 = _queueItems.value.any { it.itemId == DEBUG_TARGET_ITEM_ID },
                lastFetchAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            )
            _lastQueueFetchDebug.value = appliedSnapshot
            val resolvedOfflineReadyIds = resolveOfflineReadyIds(queueItems)
            _cachedItemIds.update { previous -> previous + resolvedOfflineReadyIds }
            reconcileCachedItemVisibility()
            settingsStore.saveQueueSnapshot(current.selectedPlaylistId, queue.copy(items = queueItems))
            repository.reconcileSessionWithQueue(queueItems)?.let { updated ->
                _nowPlayingSession.value = updated
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            refreshPendingCount()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
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
        val playbackPointer = playbackPositionByItem.value[itemId]
            ?: nowPlayingSession.value?.items?.firstOrNull { it.itemId == itemId }?.let { item ->
                PlaybackPosition(
                    chunkIndex = item.chunkIndex.coerceAtLeast(0),
                    offsetInChunkChars = item.offsetInChunkChars.coerceAtLeast(0),
                )
            }
        val readerPointer = nowPlayingSession.value
            ?.items
            ?.firstOrNull { it.itemId == itemId }
            ?.readerScrollOffset
        return try {
            val result = repository.postProgress(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                itemId = itemId,
                percent = clamped,
                chunkIndex = playbackPointer?.chunkIndex,
                offsetInChunkChars = playbackPointer?.offsetInChunkChars,
                readerScrollOffset = readerPointer,
            )
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
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
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
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun onPlaybackArticleEnded(itemId: Int, autoArchiveAtArticleEnd: Boolean) {
        if (!autoArchiveAtArticleEnd || itemId <= 0) return
        val current = settings.value
        try {
            repository.archiveItem(current.baseUrl, current.apiToken, itemId)
            removeArchivedItemLocally(itemId)
            _queueOffline.value = false
            updateSyncBadgeState()
            Log.d(
                LOCUS_CONTINUATION_DEBUG_TAG,
                "autoArchiveAtEnd archived item=$itemId ${continuationAuditContext()}",
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) return
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Log.w(
                LOCUS_CONTINUATION_DEBUG_TAG,
                "autoArchiveAtEnd failed item=$itemId err=${error.message}",
                error,
            )
        }
    }

    suspend fun archiveItem(
        itemId: Int,
        refreshQueue: Boolean = true,
        source: ArchiveActionSource = ArchiveActionSource.UP_NEXT,
    ): Result<Unit> {
        val current = settings.value
        val queueBeforeArchive = _queueItems.value
        val queueIndex = queueBeforeArchive.indexOfFirst { it.itemId == itemId }
        val queueItemSnapshot = queueBeforeArchive.getOrNull(queueIndex)
        val archivedItemSnapshot = _archivedItems.value.firstOrNull { it.itemId == itemId }
        val wasCached = _cachedItemIds.value.contains(itemId)
        val wasNoActiveContent = _noActiveContentItemIds.value.contains(itemId)
        val archiveSeed = queueItemSnapshot ?: archivedItemSnapshot
        return try {
            if (queueItemSnapshot != null && queueIndex >= 0) {
                lastArchiveUndoSnapshot = ArchiveUndoSnapshot(
                    item = queueItemSnapshot,
                    originalIndex = queueIndex,
                    wasCached = wasCached,
                    wasNoActiveContent = wasNoActiveContent,
                    source = source,
                    actionType = UndoableActionType.ARCHIVE,
                )
            } else {
                lastArchiveUndoSnapshot = null
            }
            val archivedCurrentSessionItem = currentPlaybackOwnerItemId() == itemId
            val deferPlaybackCleanup = archivedCurrentSessionItem && isItemActivelyPlaying(itemId)
            if (archivedCurrentSessionItem && !deferPlaybackCleanup) {
                playbackPause(forceSync = true)
                clearNowPlayingSessionNow()
            }
            removeArchivedItemLocally(
                itemId = itemId,
                preservePlaybackContext = deferPlaybackCleanup,
            )
            if (archiveSeed != null) {
                _archivedItems.update { existing ->
                    mergeItemIntoList(existing, archiveSeed, addToFront = true)
                }
            }
            updateAutoDownloadQueueSnapshotDiagnostics(
                current = settings.value,
                queueItems = _queueItems.value,
                offlineReadyIds = _cachedItemIds.value,
                knownNoActiveIds = _noActiveContentItemIds.value,
            )
            repository.archiveItem(current.baseUrl, current.apiToken, itemId)
            if (refreshQueue) {
                val refreshResult = loadQueueOnce(autoRetryPendingSaves = false)
                if (refreshResult.isFailure) {
                    _statusMessage.value = "Archived; queue refresh failed"
                } else {
                    _statusMessage.value = "Archived"
                }
            } else {
                _statusMessage.value = "Archived"
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                enqueuePendingItemAction(
                    itemId = itemId,
                    actionType = PendingItemActionType.ARCHIVE,
                )
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
                _statusMessage.value = "Archived offline; will sync"
                return Result.success(Unit)
            }
            runCatching { loadQueueOnce(autoRetryPendingSaves = false) }
            runCatching { loadArchivedItems() }
            Result.failure(error)
        }
    }

    suspend fun moveItemToBin(
        itemId: Int,
        refreshQueue: Boolean = true,
        source: ArchiveActionSource = ArchiveActionSource.UP_NEXT,
    ): Result<Unit> {
        val current = settings.value
        val queueBeforeMove = _queueItems.value
        val queueIndex = queueBeforeMove.indexOfFirst { it.itemId == itemId }
        val queueItemSnapshot = queueBeforeMove.getOrNull(queueIndex)
        val archivedItemSnapshot = _archivedItems.value.firstOrNull { it.itemId == itemId }
        val wasCached = _cachedItemIds.value.contains(itemId)
        val wasNoActiveContent = _noActiveContentItemIds.value.contains(itemId)
        val favoritedSnapshot = queueItemSnapshot?.isFavorited ?: archivedItemSnapshot?.isFavorited
        return try {
            repository.moveItemToBin(current.baseUrl, current.apiToken, itemId)
            if (favoritedSnapshot != null) {
                binnedFavoriteStateByItemId[itemId] = favoritedSnapshot
            }
            if (queueItemSnapshot != null && queueIndex >= 0) {
                lastArchiveUndoSnapshot = ArchiveUndoSnapshot(
                    item = queueItemSnapshot,
                    originalIndex = queueIndex,
                    wasCached = wasCached,
                    wasNoActiveContent = wasNoActiveContent,
                    source = source,
                    actionType = UndoableActionType.BIN,
                )
            } else {
                lastArchiveUndoSnapshot = null
            }
            val binnedCurrentSessionItem = currentPlaybackOwnerItemId() == itemId
            if (binnedCurrentSessionItem) {
                playbackPause(forceSync = true)
                clearNowPlayingSessionNow()
            }
            if (refreshQueue) {
                val refreshResult = loadQueueOnce(autoRetryPendingSaves = false)
                if (refreshResult.isFailure) {
                    removeArchivedItemLocally(itemId, preserveFavoriteState = true)
                    _statusMessage.value = "Moved to Bin (14 days); queue refresh failed"
                } else {
                    _statusMessage.value = "Moved to Bin (14 days)"
                }
            } else {
                removeArchivedItemLocally(itemId, preserveFavoriteState = true)
                val binSeed = queueItemSnapshot ?: archivedItemSnapshot
                if (binSeed != null) {
                    _binItems.update { existing ->
                        mergeItemIntoList(existing, binSeed, addToFront = true)
                    }
                }
                _statusMessage.value = "Moved to Bin (14 days)"
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun loadBinItems(): Result<Unit> {
        val current = settings.value
        return try {
            val trashed = repository.listTrashedItems(current.baseUrl, current.apiToken)
            _binItems.value = trashed.map { item ->
                PlaybackQueueItem(
                    itemId = item.id,
                    title = item.title,
                    url = item.url,
                    host = item.siteName,
                    status = item.status,
                    lastReadPercent = item.lastReadPercent,
                    resumeReadPercent = item.resumeReadPercent,
                    apiProgressPercent = item.progressPercent,
                    apiFurthestPercent = item.furthestPercent,
                    lastOpenedAt = item.lastOpenedAt,
                    createdAt = item.createdAt,
                    isFavorited = item.isFavorited,
                )
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun loadArchivedItems(): Result<Unit> {
        val current = settings.value
        return try {
            val archived = repository.listArchivedItems(current.baseUrl, current.apiToken)
            _archivedItems.value = archived.map { item ->
                PlaybackQueueItem(
                    itemId = item.id,
                    title = item.title,
                    url = item.url,
                    host = item.siteName,
                    status = item.status,
                    lastReadPercent = item.lastReadPercent,
                    resumeReadPercent = item.resumeReadPercent,
                    apiProgressPercent = item.progressPercent,
                    apiFurthestPercent = item.furthestPercent,
                    lastOpenedAt = item.lastOpenedAt,
                    createdAt = item.createdAt,
                    isFavorited = item.isFavorited,
                )
            }
            val combined = (_queueItems.value + _archivedItems.value).distinctBy { item -> item.itemId }
            val offlineReadyIds = resolveOfflineReadyIds(combined)
            _cachedItemIds.update { previous -> previous + offlineReadyIds }
            autoCacheFavoritedItemsIfEnabled(
                current = current,
                queueItems = _archivedItems.value,
            )
            reconcileCachedItemVisibility()
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun unarchiveItem(itemId: Int): Result<Unit> {
        val current = settings.value
        val archivedSnapshot = _archivedItems.value.firstOrNull { it.itemId == itemId }
        return try {
            _archivedItems.update { existing -> existing.filterNot { it.itemId == itemId } }
            if (archivedSnapshot != null && _queueItems.value.none { it.itemId == itemId }) {
                _queueItems.update { existing ->
                    mergeItemIntoList(existing, archivedSnapshot, addToFront = true)
                }
            }
            updateAutoDownloadQueueSnapshotDiagnostics(
                current = settings.value,
                queueItems = _queueItems.value,
                offlineReadyIds = _cachedItemIds.value,
                knownNoActiveIds = _noActiveContentItemIds.value,
            )
            repository.unarchiveItem(current.baseUrl, current.apiToken, itemId)
            repository.toggleCompletion(current.baseUrl, current.apiToken, itemId, markDone = false)
            loadQueueOnce(autoRetryPendingSaves = false)
            _statusMessage.value = "Unarchived"
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                enqueuePendingItemAction(
                    itemId = itemId,
                    actionType = PendingItemActionType.UNARCHIVE,
                )
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
                _statusMessage.value = "Unarchived offline; will sync"
                return Result.success(Unit)
            }
            runCatching { loadQueueOnce(autoRetryPendingSaves = false) }
            runCatching { loadArchivedItems() }
            Result.failure(error)
        }
    }

    suspend fun restoreItemFromBin(itemId: Int): Result<Unit> {
        val current = settings.value
        return try {
            repository.restoreItemFromBin(current.baseUrl, current.apiToken, itemId)
            _binItems.update { existing -> existing.filterNot { it.itemId == itemId } }
            loadQueueOnce(autoRetryPendingSaves = false)
            val shouldRestoreFavorite = binnedFavoriteStateByItemId[itemId] == true
            if (shouldRestoreFavorite) {
                runCatching {
                    repository.setFavoriteState(
                        baseUrl = current.baseUrl,
                        token = current.apiToken,
                        itemId = itemId,
                        favorited = true,
                    )
                    favoriteOverridesByItemId[itemId] = true
                    applyLocalFavoriteState(itemId, favorited = true)
                }
            }
            binnedFavoriteStateByItemId.remove(itemId)
            _statusMessage.value = "Restored from Bin"
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun purgeItemFromBin(itemId: Int): Result<Unit> {
        val current = settings.value
        return try {
            repository.purgeItemFromBin(current.baseUrl, current.apiToken, itemId)
            _binItems.update { existing -> existing.filterNot { it.itemId == itemId } }
            binnedFavoriteStateByItemId.remove(itemId)
            favoriteOverridesByItemId.remove(itemId)
            _statusMessage.value = "Permanently deleted from Bin"
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    suspend fun setItemFavorited(
        itemId: Int,
        favorited: Boolean,
    ): Result<Unit> {
        val current = settings.value
        favoriteOverridesByItemId[itemId] = favorited
        applyLocalFavoriteState(itemId, favorited)
        if (favorited && current.autoCacheFavoritedItems && _binItems.value.none { it.itemId == itemId }) {
            runCatching { fetchItemText(itemId) }
        }
        return try {
            repository.setFavoriteState(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                itemId = itemId,
                favorited = favorited,
            )
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                enqueuePendingItemAction(
                    itemId = itemId,
                    actionType = PendingItemActionType.SET_FAVORITE,
                    favorited = favorited,
                )
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
                _statusMessage.value = if (favorited) {
                    "Favourited offline; will sync"
                } else {
                    "Unfavourited offline; will sync"
                }
                return Result.success(Unit)
            }
            runCatching { loadQueueOnce(autoRetryPendingSaves = false) }
            runCatching { loadArchivedItems() }
            Result.failure(error)
        }
    }

    suspend fun submitProblemReport(
        category: ProblemReportCategory,
        userNote: String,
        itemId: Int?,
        url: String?,
        sourceType: String?,
        sourceLabel: String?,
        sourceUrl: String?,
        captureKind: String?,
        articleTitle: String?,
        articleText: String?,
        includeTitleAttachment: Boolean,
        includeTextAttachment: Boolean,
    ): Result<Int> {
        val current = settings.value
        if (current.apiToken.isBlank()) {
            return Result.failure(IllegalStateException("Sign in to submit problem reports."))
        }
        val trimmedNote = userNote.trim()
        if (trimmedNote.isBlank()) {
            return Result.failure(IllegalArgumentException("Please add a note before submitting."))
        }
        if (trimmedNote.length > 500) {
            return Result.failure(IllegalArgumentException("Note is too long (max 500 characters)."))
        }
        val normalizedUrl = url?.trim()?.takeIf { it.isNotBlank() }
        val attachedTitle = if (includeTitleAttachment) toProblemReportAttachmentTitle(articleTitle) else null
        val attachedText = if (includeTextAttachment) toProblemReportAttachmentText(articleText) else null
        return try {
            val response = apiClient.postProblemReport(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                requestPayload = ProblemReportRequest(
                    category = category.wireValue,
                    userNote = trimmedNote,
                    itemId = itemId?.takeIf { it > 0 },
                    url = normalizedUrl,
                    clientType = "android",
                    clientVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    reportTime = utcNowIso8601(),
                    sourceType = sourceType,
                    sourceLabel = sourceLabel,
                    sourceUrl = sourceUrl,
                    captureKind = captureKind,
                    includeFullTextAttachment = includeTitleAttachment || includeTextAttachment,
                    articleTitleAttached = attachedTitle,
                    articleTextAttached = attachedText,
                    attachmentTruncated = null,
                ),
            )
            _queueOffline.value = false
            updateSyncBadgeState()
            Result.success(response.id)
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

    private fun utcNowIso8601(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    suspend fun undoLastArchive(): Result<ArchiveUndoOutcome> {
        val current = settings.value
        val snapshot = lastArchiveUndoSnapshot ?: return Result.failure(IllegalStateException("Nothing to undo"))
        return try {
            when (snapshot.actionType) {
                UndoableActionType.ARCHIVE -> {
                    repository.unarchiveItem(current.baseUrl, current.apiToken, snapshot.item.itemId)
                    repository.toggleCompletion(current.baseUrl, current.apiToken, snapshot.item.itemId, markDone = false)
                }
                UndoableActionType.BIN -> {
                    repository.restoreItemFromBin(current.baseUrl, current.apiToken, snapshot.item.itemId)
                }
            }
            _archivedItems.update { previous -> previous.filterNot { it.itemId == snapshot.item.itemId } }
            _binItems.update { previous -> previous.filterNot { it.itemId == snapshot.item.itemId } }
            _queueItems.update { previous ->
                val withoutItem = previous.filterNot { it.itemId == snapshot.item.itemId }
                val insertAt = snapshot.originalIndex.coerceIn(0, withoutItem.size)
                withoutItem.toMutableList().apply {
                    add(insertAt, snapshot.item)
                }
            }
            if (snapshot.wasCached) {
                _cachedItemIds.update { previous -> previous + snapshot.item.itemId }
            }
            if (snapshot.wasNoActiveContent) {
                _noActiveContentItemIds.update { previous -> previous + snapshot.item.itemId }
            }
            updateAutoDownloadQueueSnapshotDiagnostics(
                current = settings.value,
                queueItems = _queueItems.value,
                offlineReadyIds = _cachedItemIds.value,
                knownNoActiveIds = _noActiveContentItemIds.value,
            )
            val reopenItemId = when (snapshot.source) {
                ArchiveActionSource.LOCUS -> snapshot.item.itemId
                ArchiveActionSource.UP_NEXT -> null
            }
            if (reopenItemId != null) {
                startNowPlayingSession(startItemId = reopenItemId)
                playbackOpenItem(
                    itemId = reopenItemId,
                    intent = PlaybackOpenIntent.ManualOpen,
                    autoPlayAfterLoad = false,
                )
            }
            _statusMessage.value = if (snapshot.actionType == UndoableActionType.BIN) {
                "Bin move undone"
            } else {
                "Archive undone"
            }
            _queueOffline.value = false
            updateSyncBadgeState()
            lastArchiveUndoSnapshot = null
            Result.success(
                ArchiveUndoOutcome(
                    reopenItemId = reopenItemId,
                    actionType = snapshot.actionType,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (handleAuthFailureIfNeeded(error)) {
                return Result.failure(error)
            }
            if (isNetworkError(error)) {
                _queueOffline.value = true
                _progressSyncBadgeState.value = ProgressSyncBadgeState.OFFLINE
            }
            Result.failure(error)
        }
    }

    private suspend fun removeArchivedItemLocally(
        itemId: Int,
        preserveFavoriteState: Boolean = false,
        preservePlaybackContext: Boolean = false,
    ) {
        _queueItems.update { previous -> previous.filterNot { it.itemId == itemId } }
        _archivedItems.update { previous -> previous.filterNot { it.itemId == itemId } }
        _binItems.update { previous -> previous.filterNot { it.itemId == itemId } }
        _cachedItemIds.update { previous -> previous - itemId }
        _noActiveContentItemIds.update { previous -> previous - itemId }
        if (!preserveFavoriteState) {
            favoriteOverridesByItemId.remove(itemId)
        }
        updateAutoDownloadQueueSnapshotDiagnostics(
            current = settings.value,
            queueItems = _queueItems.value,
            offlineReadyIds = _cachedItemIds.value,
            knownNoActiveIds = _noActiveContentItemIds.value,
        )
        if (!preservePlaybackContext && currentPlaybackOwnerItemId() == itemId) {
            clearNowPlayingSessionNow()
        }
    }

    private fun applyLocalFavoriteState(itemId: Int, favorited: Boolean) {
        _queueItems.update { existing -> updateFavoriteStateInList(existing, itemId, favorited) }
        _archivedItems.update { existing -> updateFavoriteStateInList(existing, itemId, favorited) }
        _binItems.update { existing -> updateFavoriteStateInList(existing, itemId, favorited) }
        reconcileCachedItemVisibility()
    }

    private fun updateFavoriteStateInList(
        existing: List<PlaybackQueueItem>,
        itemId: Int,
        favorited: Boolean,
    ): List<PlaybackQueueItem> {
        return existing.map { item ->
            if (item.itemId != itemId) {
                item
            } else {
                item.copy(isFavorited = favorited)
            }
        }
    }

    private fun mergeItemIntoList(
        existing: List<PlaybackQueueItem>,
        item: PlaybackQueueItem,
        addToFront: Boolean,
    ): List<PlaybackQueueItem> {
        val withoutItem = existing.filterNot { it.itemId == item.itemId }
        return if (addToFront) {
            listOf(item) + withoutItem
        } else {
            withoutItem + item
        }
    }

    private fun reconcileCachedItemVisibility() {
        val allowedQueueIds = _queueItems.value.mapTo(mutableSetOf()) { it.itemId }
        val allowedArchivedFavoritedIds = _archivedItems.value
            .asSequence()
            .filter { it.isFavorited }
            .map { it.itemId }
            .toSet()
        val allowedIds = allowedQueueIds + allowedArchivedFavoritedIds
        _cachedItemIds.update { existing -> existing.filterTo(mutableSetOf()) { it in allowedIds } }
    }

    private fun applyFavoriteOverrides(items: List<PlaybackQueueItem>): List<PlaybackQueueItem> {
        if (items.isEmpty()) {
            favoriteOverridesByItemId.clear()
            return items
        }
        val visibleIds = items.mapTo(hashSetOf()) { it.itemId }
        favoriteOverridesByItemId.keys.retainAll(visibleIds)
        return items.map { item ->
            val override = favoriteOverridesByItemId[item.itemId] ?: return@map item
            if (item.isFavorited == override) item else item.copy(isFavorited = override)
        }
    }

    private fun currentPlaybackOwnerItemId(): Int? {
        val engineItemId = playbackEngineState.value.currentItemId
        return if (engineItemId > 0) engineItemId else currentNowPlayingItemId()
    }

    private fun isItemActivelyPlaying(itemId: Int): Boolean {
        val engine = playbackEngineState.value
        if (engine.currentItemId != itemId || itemId <= 0) return false
        return engine.isSpeaking || engine.isAutoPlaying || engine.autoPlayAfterLoad
    }

    suspend fun resolveInitialPlayerItemId(fallbackItemId: Int): Int {
        val existing = nowPlayingSession.value
        val existingItem = existing?.currentItem
        if (existingItem != null) {
            return existingItem.itemId
        }
        val queue = queueItems.value
        if (queue.isNotEmpty()) {
            val session = repository.startSession(
                queueItems = queue,
                startItemId = fallbackItemId,
                sourcePlaylistId = resolveSessionSourcePlaylistId(settings.value.selectedPlaylistId),
            )
            applySessionSnapshot(session)
            return session.currentItem?.itemId ?: fallbackItemId
        }
        return fallbackItemId
    }

    fun startNowPlayingSession(startItemId: Int, orderedQueueItems: List<PlaybackQueueItem>? = null) {
        val queue = orderedQueueItems?.takeIf { it.isNotEmpty() } ?: queueItems.value
        if (queue.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val session = repository.startSession(
                queueItems = queue,
                startItemId = startItemId,
                sourcePlaylistId = resolveSessionSourcePlaylistId(settings.value.selectedPlaylistId),
            )
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
    fun isCurrentSessionPlaylistScoped(): Boolean {
        val sourcePlaylistId = nowPlayingSession.value?.sourcePlaylistId ?: return false
        return sourcePlaylistId != SMART_QUEUE_SESSION_CONTEXT_ID
    }

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
        return queueItems.value.firstOrNull { it.itemId == itemId }?.progressPercent ?: 0
    }

    fun knownFurthestForItem(itemId: Int): Int {
        return queueItems.value.firstOrNull { it.itemId == itemId }?.furthestPercent ?: 0
    }

    fun isItemCompletedForPlaybackStart(itemId: Int): Boolean {
        return shouldReplayCompletedItem(knownFurthestForItem(itemId))
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

    suspend fun nextPlaylistScopedSessionItemId(currentId: Int): Int? {
        val session = nowPlayingSession.value ?: run {
            Log.d(LOCUS_CONTINUATION_DEBUG_TAG, "vm.nextPlaylistScopedSessionItemId currentId=$currentId session=null")
            return null
        }
        Log.d(
            LOCUS_CONTINUATION_DEBUG_TAG,
            "vm.nextPlaylistScopedSessionItemId start currentId=$currentId currentIndex=${session.currentIndex} " +
                "currentSessionItem=${session.currentItem?.itemId} itemCount=${session.items.size} " +
                "sourcePlaylistId=${session.sourcePlaylistId} firstItems=${session.items.take(8).joinToString { it.itemId.toString() }}",
        )
        val nextIndex = resolveNextPlaylistScopedSessionIndex(session, currentId) ?: run {
            Log.d(
                LOCUS_CONTINUATION_DEBUG_TAG,
                "vm.nextPlaylistScopedSessionItemId noNext currentId=$currentId currentIndex=${session.currentIndex} " +
                    "currentSessionItem=${session.currentItem?.itemId} itemCount=${session.items.size}",
            )
            return null
        }
        val updated = repository.setCurrentIndex(nextIndex) ?: session.copy(currentIndex = nextIndex)
        _nowPlayingSession.value = updated
        Log.d(
            LOCUS_CONTINUATION_DEBUG_TAG,
            "vm.nextPlaylistScopedSessionItemId currentId=$currentId nextIndex=$nextIndex nextId=${updated.currentItem?.itemId} sourcePlaylistId=${updated.sourcePlaylistId}",
        )
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
        val session = repository.startSession(
            queueItems = queue,
            startItemId = currentId,
            sourcePlaylistId = resolveSessionSourcePlaylistId(settings.value.selectedPlaylistId),
        )
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

    fun setReaderScrollOffset(itemId: Int, offset: Int) {
        val safeOffset = offset.coerceAtLeast(0)
        viewModelScope.launch {
            val updated = repository.setCurrentReaderScrollOffset(
                itemId = itemId,
                readerScrollOffset = safeOffset,
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
        var offlineReadyIds = cachedItemIds
        var remainingTargets = selectInitialQueueHydrationTargets(
            queueItems = queueItems,
            cachedItemIds = offlineReadyIds,
        )
        if (remainingTargets.isEmpty()) return offlineReadyIds
        repeat(INITIAL_SIGN_IN_HYDRATION_ATTEMPTS) { attempt ->
            val attempts = repository.prefetchItemTexts(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                itemIds = remainingTargets,
            )
            val noActiveContentIds = attempts
                .filter(::isNoActiveContentAttempt)
                .map { it.itemId }
                .toSet()
            val terminalFailedIds = attempts
                .filterNot { it.success || it.retryable }
                .map { it.itemId }
                .toSet()
            if (noActiveContentIds.isNotEmpty()) {
                _noActiveContentItemIds.update { previous ->
                    retainKnownNoActiveContentIds(queueItems, previous + noActiveContentIds)
                }
            }
            offlineReadyIds = resolveOfflineReadyIds(queueItems)
            remainingTargets = selectInitialQueueHydrationTargets(
                queueItems = queueItems,
                cachedItemIds = offlineReadyIds,
            ).filterNot { terminalFailedIds.contains(it) }
            logInitialHydrationAttempt(
                phase = "initial",
                attempt = attempt + 1,
                requestedIds = attempts.map { it.itemId },
                attempts = attempts,
                unresolvedIds = remainingTargets,
            )
            if (remainingTargets.isEmpty()) {
                return offlineReadyIds
            }
            if (attempt < INITIAL_SIGN_IN_HYDRATION_ATTEMPTS - 1) {
                delay(INITIAL_SIGN_IN_HYDRATION_RETRY_DELAY_MS)
            }
        }
        if (remainingTargets.isNotEmpty()) {
            initialPostSignInHydrationJob?.cancel()
            initialPostSignInHydrationJob = viewModelScope.launch {
                continueInitialPostSignInHydration(
                    current = current,
                    targetItemIds = remainingTargets,
                )
            }
        }
        return offlineReadyIds
    }

    private suspend fun continueInitialPostSignInHydration(
        current: AppSettings,
        targetItemIds: List<Int>,
    ) {
        var remainingTargets = targetItemIds.distinct()
        repeat(INITIAL_SIGN_IN_BACKGROUND_HYDRATION_ATTEMPTS) { attempt ->
            if (remainingTargets.isEmpty()) return
            val attempts = repository.prefetchItemTexts(
                baseUrl = current.baseUrl,
                token = current.apiToken,
                itemIds = remainingTargets,
            )
            val noActiveContentIds = attempts
                .filter(::isNoActiveContentAttempt)
                .map { it.itemId }
                .toSet()
            val terminalFailedIds = attempts
                .filterNot { it.success || it.retryable }
                .map { it.itemId }
                .toSet()
            val cachedTargetIds = repository.getCachedItemIds(remainingTargets)
            if (cachedTargetIds.isNotEmpty()) {
                _cachedItemIds.value = resolveOfflineReadyIds(_queueItems.value)
                reconcileCachedItemVisibility()
            }
            if (noActiveContentIds.isNotEmpty()) {
                _noActiveContentItemIds.update { previous ->
                    retainKnownNoActiveContentIds(_queueItems.value, previous + noActiveContentIds)
                }
            }
            remainingTargets = remainingTargets.filterNot {
                cachedTargetIds.contains(it) || terminalFailedIds.contains(it)
            }
            logInitialHydrationAttempt(
                phase = "background",
                attempt = attempt + 1,
                requestedIds = attempts.map { it.itemId },
                attempts = attempts,
                unresolvedIds = remainingTargets,
            )
            if (remainingTargets.isEmpty()) return
            if (attempt < INITIAL_SIGN_IN_BACKGROUND_HYDRATION_ATTEMPTS - 1) {
                delay(INITIAL_SIGN_IN_BACKGROUND_HYDRATION_RETRY_DELAY_MS)
            }
        }
        if (remainingTargets.isNotEmpty()) {
            Log.w(
                INITIAL_SIGN_IN_HYDRATION_DEBUG_TAG,
                "unresolvedAfterBackground itemIds=${remainingTargets.joinToString(",")}",
            )
        }
    }

    private fun retainKnownNoActiveContentIds(
        queueItems: List<PlaybackQueueItem>,
        existing: Set<Int>,
    ): Set<Int> {
        if (existing.isEmpty()) return emptySet()
        val queueItemIds = queueItems.mapTo(linkedSetOf()) { it.itemId }
        return existing.filterTo(linkedSetOf()) { queueItemIds.contains(it) }
    }

    private fun logInitialHydrationAttempt(
        phase: String,
        attempt: Int,
        requestedIds: List<Int>,
        attempts: List<ItemTextPrefetchAttempt>,
        unresolvedIds: List<Int>,
    ) {
        if (!BuildConfig.DEBUG) return
        val failures = attempts
            .filterNot { it.success }
            .joinToString(";") { "${it.itemId}:${it.errorSummary.orEmpty()}" }
        Log.d(
            INITIAL_SIGN_IN_HYDRATION_DEBUG_TAG,
            "phase=$phase attempt=$attempt requested=${requestedIds.joinToString(",")} unresolved=${unresolvedIds.joinToString(",")} failures=$failures",
        )
    }

    private fun updateSyncBadgeState(pendingCount: Int = _pendingProgressCount.value) {
        _progressSyncBadgeState.value = when {
            _queueOffline.value && pendingCount > 0 -> ProgressSyncBadgeState.OFFLINE
            _queueOffline.value -> ProgressSyncBadgeState.OFFLINE
            pendingCount > 0 -> ProgressSyncBadgeState.QUEUED
            else -> ProgressSyncBadgeState.SYNCED
        }
    }

    private fun isNetworkError(error: Throwable): Boolean = error is IOException

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
        val engine = playbackEngineState.value
        val preservePlaybackSession =
            engine.currentItemId > 0 && (engine.isSpeaking || engine.isAutoPlaying || engine.autoPlayAfterLoad)
        if (preservePlaybackSession) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    LOCUS_CONTINUATION_DEBUG_TAG,
                    "onCleared preservePlaybackSession item=${engine.currentItemId} " +
                        "speaking=${engine.isSpeaking} auto=${engine.isAutoPlaying} autoAfterLoad=${engine.autoPlayAfterLoad}",
                )
            }
        } else {
            PlaybackServiceBridge.clear()
            unbindPlaybackService()
            playbackEngine.shutdown()
        }
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

    private suspend fun runRawCheck(
        baseUrl: String,
        token: String,
        path: String,
        name: String,
        mode: ConnectionMode = settings.value.connectionMode,
    ): ConnectivityDiagnosticRow {
        val url = "$baseUrl$path"
        val attemptCount = if (mode == ConnectionMode.REMOTE) 3 else 1
        val attempts = mutableListOf<ConnectivityProbeAttempt>()
        repeat(attemptCount) {
            val startedAt = System.nanoTime()
            try {
                val response = apiClient.getRawEndpoint(baseUrl, token, path)
                val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val pass = response.statusCode in 200..299
                attempts += ConnectivityProbeAttempt(
                    pass = pass,
                    statusCode = response.statusCode,
                    errorMessage = null,
                    hint = if (pass) {
                        null
                    } else {
                        ConnectionTestMessageResolver.forDiagnosticsHttpFailure(
                            mode = mode,
                            baseUrl = baseUrl,
                            path = path,
                            statusCode = response.statusCode,
                        )
                    },
                    latencyMs = latencyMs,
                    bodySnippet = response.body.take(200).ifBlank { "status=${response.statusCode}" },
                )
            } catch (error: Exception) {
                val message = error.message ?: "request failed"
                val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                attempts += ConnectivityProbeAttempt(
                    pass = false,
                    statusCode = null,
                    errorMessage = message,
                    hint = ConnectionTestMessageResolver.forDiagnosticsException(
                        mode = mode,
                        baseUrl = baseUrl,
                        message = message,
                    ),
                    latencyMs = latencyMs,
                    bodySnippet = null,
                )
            }
        }

        val passCount = attempts.count { it.pass }
        val totalCount = attempts.size
        val sortedLatency = attempts.map { it.latencyMs }.sorted()
        val p50Latency = sortedLatency[sortedLatency.lastIndex / 2]
        val p95Latency = sortedLatency[((sortedLatency.size - 1) * 95) / 100]
        val attemptsSummary = "attempts=$passCount/$totalCount latency_ms(p50=$p50Latency,p95=$p95Latency)"
        val dominantHint = attempts
            .mapNotNull { it.hint }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val firstPassDetail = attempts.firstOrNull { it.pass }?.bodySnippet
        val firstFailureDetail = attempts.firstOrNull { !it.pass }?.let { failure ->
            failure.statusCode?.let { "status=$it" } ?: "error=${failure.errorMessage.orEmpty()}"
        }

        return when {
            passCount == totalCount -> diagnosticRow(
                name = name,
                url = url,
                outcome = ConnectivityDiagnosticOutcome.PASS,
                detail = "${firstPassDetail ?: "status=200"} ($attemptsSummary, class=stable)",
                hint = null,
            )
            passCount > 0 -> diagnosticRow(
                name = name,
                url = url,
                outcome = ConnectivityDiagnosticOutcome.INFO,
                detail = "${firstFailureDetail ?: "intermittent failure"} ($attemptsSummary, class=flaky)",
                hint = dominantHint ?: "Intermittent connectivity; rerun diagnostics and verify network path.",
            )
            else -> diagnosticRow(
                name = name,
                url = url,
                outcome = ConnectivityDiagnosticOutcome.FAIL,
                detail = "${firstFailureDetail ?: "request failed"} ($attemptsSummary, class=down)",
                hint = dominantHint ?: "Connection failed. Verify backend reachability.",
            )
        }
    }

    private data class ConnectivityProbeAttempt(
        val pass: Boolean,
        val statusCode: Int?,
        val errorMessage: String?,
        val hint: String?,
        val latencyMs: Long,
        val bodySnippet: String?,
    )

    private fun withProbeMeta(prefix: String, originalDetail: String): String {
        val marker = " (attempts="
        val suffix = originalDetail.substringAfter(marker, "")
        return if (suffix.isBlank()) {
            prefix
        } else {
            "$prefix$marker$suffix"
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

internal fun shouldKeepScreenOnForSession(
    keepScreenOnEnabled: Boolean,
    requiresSignIn: Boolean,
    isOnLocusRoute: Boolean,
    requestedPlayerItemId: Int?,
    playbackActive: Boolean,
    manualReadingActive: Boolean,
): Boolean {
    if (!keepScreenOnEnabled || requiresSignIn || !isOnLocusRoute) return false
    if ((requestedPlayerItemId ?: -1) <= 0) return false
    return playbackActive || manualReadingActive
}

@Composable
private fun MimeoApp(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
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
    val playbackEngineState by vm.playbackEngineState.collectAsState()
    val requiresSignIn = settings.apiToken.isBlank()
    var pendingLocusOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLocusItemId by rememberSaveable { mutableIntStateOf(-1) }
    val sessionNowPlayingItemId = vm.currentNowPlayingItemId()
    val hasPlaybackItemInProgress =
        (sessionNowPlayingItemId ?: -1) > 0 &&
            (
                playbackEngineState.hasStartedPlaybackForCurrentItem ||
                    playbackEngineState.isSpeaking ||
                    playbackEngineState.isAutoPlaying
                )
    val routeItemId = navBackStack?.arguments?.let { args ->
        if (args.containsKey("itemId")) args.getInt("itemId").takeIf { it > 0 } else null
    }
    val requestedPlayerItemId =
        routeItemId
            ?: pendingLocusItemId.takeIf { pendingLocusOpen && it > 0 }
            ?: sessionNowPlayingItemId
    var playbackActive by rememberSaveable { mutableStateOf(false) }
    var manualReadingActive by rememberSaveable { mutableStateOf(false) }
    val keepScreenOnForSession = shouldKeepScreenOnForSession(
        keepScreenOnEnabled = settings.keepScreenOnDuringSession,
        requiresSignIn = requiresSignIn,
        isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS),
        requestedPlayerItemId = requestedPlayerItemId,
        playbackActive = playbackActive || hasPlaybackItemInProgress,
        manualReadingActive = manualReadingActive,
    )
    SideEffect {
        val window = hostActivity?.window ?: return@SideEffect
        if (keepScreenOnForSession) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(hostActivity) {
        onDispose {
            hostActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(sessionNowPlayingItemId, routeItemId, requestedPlayerItemId, currentRoute, pendingLocusOpen, pendingLocusItemId) {
        Log.d(
            LOCUS_CONTINUATION_DEBUG_TAG,
            "mimeoApp route=$currentRoute routeItemId=$routeItemId sessionItemId=$sessionNowPlayingItemId requestedItemId=$requestedPlayerItemId " +
                "handoffPending=$pendingLocusOpen handoffTarget=$pendingLocusItemId",
        )
    }
    val selectedTab = when {
        currentRoute.startsWith(ROUTE_LOCUS) -> ROUTE_LOCUS
        currentRoute.startsWith(ROUTE_COLLECTIONS) -> ROUTE_COLLECTIONS
        currentRoute.startsWith(ROUTE_SETTINGS) -> ROUTE_SETTINGS
        else -> ROUTE_UP_NEXT
    }
    val isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS)
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
    val showCompactControls = settings.persistentPlayerEnabled
    var locusTabTapSignal by rememberSaveable { mutableIntStateOf(0) }
    var upNextTabTapSignal by rememberSaveable { mutableIntStateOf(0) }
    var playerOpenRequestSignal by rememberSaveable { mutableIntStateOf(0) }
    val presentingLocus = isOnLocusRoute
    val compactControlsOnly = !isOnLocusRoute
    val bottomNavVisible = !requiresSignIn && !(presentingLocus && readerChromeHidden)
    val bottomNavClearance = if (bottomNavVisible) 68.dp else 0.dp
    var isNowPlayingStripExpanded by rememberSaveable { mutableStateOf(false) }
    val nowPlayingPresentation = nowPlayingCapturePresentation(nowPlayingSession?.currentItem)
    val nowPlayingStripTitle = nowPlayingPresentation.title.ifBlank { "No active playback" }
    val nowPlayingStripDomain = nowPlayingPresentation.sourceLabel
    val nowPlayingStripSourceUrl = nowPlayingPresentation.sourceUrl
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
                    ACTION_KEY_UNDO_ARCHIVE -> {
                        vm.undoLastArchive()
                            .onSuccess { outcome ->
                                val message = if (outcome.actionType == UndoableActionType.BIN) {
                                    "Bin move undone"
                                } else {
                                    "Archive undone"
                                }
                                vm.showSnackbar(message, null, null)
                                outcome.reopenItemId?.let { itemId ->
                                    nav.navigate("$ROUTE_LOCUS/$itemId") { launchSingleTop = true }
                                }
                            }
                            .onFailure {
                                vm.showSnackbar("Couldn't undo last action", "Diagnostics", ACTION_KEY_OPEN_DIAGNOSTICS)
                            }
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingNavigationRoute) {
        pendingNavigationRoute?.let { route ->
            if (route == ROUTE_SIGN_IN) {
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            } else if (route == ROUTE_UP_NEXT && currentRoute == ROUTE_SIGN_IN) {
                nav.navigate(route) {
                    popUpTo(ROUTE_SIGN_IN) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                nav.navigate(route) { launchSingleTop = true }
            }
            vm.consumePendingNavigation(route)
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute.startsWith(ROUTE_LOCUS)) {
            pendingLocusOpen = false
            pendingLocusItemId = -1
        }
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

    LaunchedEffect(pendingLocusOpen, currentRoute) {
        if (!pendingLocusOpen || currentRoute.startsWith(ROUTE_LOCUS)) return@LaunchedEffect
        delay(750)
        if (pendingLocusOpen && !currentRoute.startsWith(ROUTE_LOCUS)) {
            pendingLocusOpen = false
            pendingLocusItemId = -1
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                if (showGlobalBanner && !presentingLocus) {
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
                                onChangePassword = vm::changePassword,
                                onClearPasswordChangeState = vm::clearPasswordChangeState,
                                onSignOut = vm::signOut,
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
                                upNextTabTapSignal = upNextTabTapSignal,
                                onOpenPlayer = { itemId ->
                                    val activeNowPlayingItemId = sessionNowPlayingItemId?.takeIf { it > 0 }
                                    if (hasPlaybackItemInProgress && activeNowPlayingItemId != null && activeNowPlayingItemId != itemId) {
                                        pendingLocusOpen = true
                                        pendingLocusItemId = itemId
                                        vm.showSnackbar(
                                            "Viewing item while playback continues. Use long-press Play or 'Play this item' to switch.",
                                            null,
                                            null,
                                        )
                                        nav.navigate("$ROUTE_LOCUS/$itemId") {
                                            launchSingleTop = true
                                        }
                                        return@QueueScreen
                                    }
                                    pendingLocusOpen = true
                                    pendingLocusItemId = itemId
                                    nav.navigate("$ROUTE_LOCUS/$itemId") {
                                        launchSingleTop = true
                                    }
                                },
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
                            openRequestSignal = playerOpenRequestSignal,
                            onOpenItem = { nextId ->
                                if (selectedTab == ROUTE_LOCUS && currentRoute.startsWith(ROUTE_LOCUS)) {
                                    nav.navigate("$ROUTE_LOCUS/$nextId") {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onRequestBack = {
                                nav.navigate(ROUTE_UP_NEXT) {
                                    popUpTo(ROUTE_LOCUS) { inclusive = true }
                                    launchSingleTop = true
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
                            onManualReadingActiveChange = { active ->
                                manualReadingActive = active
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
                                    .padding(bottom = bottomNavClearance)
                                    .fillMaxWidth()
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = bottomNavClearance)
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
                    .padding(bottom = if (presentingLocus && readerChromeHidden) 12.dp else 76.dp),
            )

            AnimatedVisibility(
                visible = bottomNavVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(animationSpec = tween(150)),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120)),
                modifier = Modifier.align(Alignment.BottomCenter),
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
                                } else if (destination.route == ROUTE_UP_NEXT && selectedTab == ROUTE_UP_NEXT) {
                                    upNextTabTapSignal += 1
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
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
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
