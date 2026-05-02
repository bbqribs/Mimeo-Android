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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
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
import com.mimeo.android.model.ProblemReportCategory
import com.mimeo.android.model.ProblemReportRequest
import com.mimeo.android.model.toProblemReportAttachmentText
import com.mimeo.android.model.toProblemReportAttachmentTitle
import com.mimeo.android.model.ProgressSyncBadgeState
import com.mimeo.android.model.QueueFetchDebugSnapshot
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.repository.ItemTextResult
import com.mimeo.android.repository.ItemTextPrefetchAttempt
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
import com.mimeo.android.share.ShareRefreshEvent
import com.mimeo.android.share.ShareSaveRefreshBus
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.isAutoRetryEligiblePendingSaveResult
import com.mimeo.android.share.isRetryablePendingSaveResult
import com.mimeo.android.repository.ProgressPostResult
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.library.LibraryBatchAction
import com.mimeo.android.ui.library.LibraryItemsScreen
import com.mimeo.android.ui.library.LibrarySortOption
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import com.mimeo.android.ui.settings.ConnectivityDiagnosticsScreen
import com.mimeo.android.ui.settings.ConnectionTestMessageResolver
import com.mimeo.android.ui.settings.PasswordChangeState
import com.mimeo.android.ui.settings.SettingsScreen
import com.mimeo.android.ui.settings.passwordChangeSuccessMessage
import com.mimeo.android.ui.settings.resolvePasswordChangeError
import com.mimeo.android.ui.settings.validatePasswordChangeInput
import com.mimeo.android.ui.player.MiniPlayer
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.player.PlaybackEngine
import com.mimeo.android.ui.player.PlaybackEngineEvent
import com.mimeo.android.ui.player.PlaybackEngineHost
import com.mimeo.android.ui.player.PlaybackEngineSettings
import com.mimeo.android.ui.player.PlaybackEngineState
import com.mimeo.android.ui.player.PlaybackOpenIntent
import com.mimeo.android.ui.player.buildPlaybackChunks
import com.mimeo.android.ui.playlists.PlaylistDetailScreen
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
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

internal const val DONE_PERCENT_THRESHOLD = 98
internal const val ROUTE_INBOX = "inbox"
internal const val ROUTE_FAVORITES = "favorites"
internal const val ROUTE_ARCHIVE = "archive"
internal const val ROUTE_BIN = "bin"
internal const val ROUTE_UP_NEXT = "upNext"
internal const val ROUTE_SIGN_IN = "signIn"
internal const val ROUTE_LOCUS = "locus"
internal const val ROUTE_LOCUS_ITEM = "locus/{itemId}"
internal const val ROUTE_PLAYLIST_DETAIL = "playlist/{playlistId}"
internal const val ROUTE_SMART_PLAYLIST_DETAIL = "smartPlaylist/{playlistId}"
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_SETTINGS_DIAGNOSTICS = "settings/diagnostics"
internal const val ROUTE_BLUESKY_BROWSE = "bluesky/browse"
internal const val ACTION_KEY_OPEN_DIAGNOSTICS = "open_diagnostics"
internal const val ACTION_KEY_OPEN_SETTINGS = "open_settings"
internal const val ACTION_KEY_UNDO_ARCHIVE = "undo_archive"
internal const val ACTION_KEY_UNDO_BATCH = "undo_batch"
internal const val ACTION_KEY_UNDO_PLAYLIST_REMOVE = "undo_playlist_remove"
internal const val ACTION_KEY_OPEN_PLAYLIST_PREFIX = "open_playlist:"
internal const val QUEUE_DEBUG_TAG = "MimeoQueueFetch"
internal const val DEBUG_TARGET_ITEM_ID = 409
internal const val INITIAL_SIGN_IN_HYDRATION_DEBUG_TAG = "MimeoSignInHydration"
internal const val TEXT_LOAD_POLICY_DEBUG_TAG = "MimeoTextLoadPolicy"
internal const val INITIAL_SIGN_IN_AUTO_DOWNLOAD_LIMIT = 2_147_483_647
internal const val INITIAL_SIGN_IN_HYDRATION_ATTEMPTS = 3
internal const val INITIAL_SIGN_IN_HYDRATION_RETRY_DELAY_MS = 300L
internal const val INITIAL_SIGN_IN_BACKGROUND_HYDRATION_ATTEMPTS = 10
internal const val INITIAL_SIGN_IN_BACKGROUND_HYDRATION_RETRY_DELAY_MS = 500L
internal const val SMART_QUEUE_SESSION_CONTEXT_ID = -1
internal const val SMART_VIEW_SESSION_SOURCE_PREFIX = "Smart view"
internal const val LOCUS_CONTINUATION_DEBUG_TAG = "MimeoLocusContinue"
internal const val SHARE_REFRESH_COALESCE_MS = 300L
internal const val SHARE_REFRESH_KEYED_DEDUPE_WINDOW_MS = 1_500L
internal const val SHARE_REFRESH_DEBUG_TAG = "MimeoShareRefresh"

internal data class ShareRefreshDebugSnapshot(
    val seen: Int = 0,
    val postDebounceSeen: Int = 0,
    val skippedByKey: Int = 0,
    val executed: Int = 0,
    val coalescedOrSkipped: Int = 0,
    val lastSignalAtMs: Long = 0L,
    val lastExecutedAtMs: Long = 0L,
    val sourceCounts: Map<String, Int> = emptyMap(),
)

internal fun resolveShareRefreshBurstKey(event: ShareRefreshEvent): String {
    val playlistPart = event.playlistId?.toString() ?: "smart"
    return if (event.itemId != null && event.itemId > 0) {
        "playlist:$playlistPart:item:${event.itemId}"
    } else {
        "playlist:$playlistPart:source:${event.source}"
    }
}

internal fun shouldSkipShareRefreshBurst(
    lastExecutedAtMs: Long?,
    nowMs: Long,
    dedupeWindowMs: Long = SHARE_REFRESH_KEYED_DEDUPE_WINDOW_MS,
): Boolean {
    if (lastExecutedAtMs == null) return false
    return (nowMs - lastExecutedAtMs) < dedupeWindowMs
}

internal data class DrawerDestination(
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

internal fun resolveSmartPlaylistSessionSourceId(smartPlaylistId: Int): Int {
    return -smartPlaylistId - 2
}

internal fun resolveSmartPlaylistIdFromSessionSourceId(sourceId: Int?): Int? {
    return sourceId
        ?.takeIf { it < SMART_QUEUE_SESSION_CONTEXT_ID }
        ?.let { -it - 2 }
}

internal fun smartPlaylistSessionSourceLabel(name: String): String {
    return "$SMART_VIEW_SESSION_SOURCE_PREFIX: $name"
}

data class PendingRetryBatchResult(
    val successCount: Int,
    val firstFailureResult: ShareSaveResult? = null,
)

internal data class PendingItemActionFlushEntry(
    val action: PendingItemAction,
    val sourceIds: List<Long>,
)

internal enum class PendingItemActionFamily {
    FAVORITE,
    ARCHIVE,
    BIN,
}

internal fun pendingItemActionFamily(actionType: PendingItemActionType): PendingItemActionFamily {
    return when (actionType) {
        PendingItemActionType.SET_FAVORITE -> PendingItemActionFamily.FAVORITE
        PendingItemActionType.ARCHIVE,
        PendingItemActionType.UNARCHIVE,
        -> PendingItemActionFamily.ARCHIVE
        PendingItemActionType.MOVE_TO_BIN,
        PendingItemActionType.RESTORE_FROM_BIN,
        PendingItemActionType.PURGE_FROM_BIN,
        -> PendingItemActionFamily.BIN
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

internal data class ArchiveUndoSnapshot(
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

internal fun mergeWorkerPersistedNoActiveContentIdsOnQueueLoad(
    queueItems: List<PlaybackQueueItem>,
    existingKnownIds: Set<Int>,
    persistedNoContentIds: Set<Int>,
): Set<Int> {
    if (persistedNoContentIds.isEmpty()) return existingKnownIds
    val queueItemIds = queueItems.mapTo(linkedSetOf()) { it.itemId }
    return (existingKnownIds + persistedNoContentIds).filterTo(linkedSetOf()) { queueItemIds.contains(it) }
}

internal fun isLikelyPhysicalDevice(): Boolean {
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val currentRoute = navBackStack?.destination?.route.orEmpty()
    val settings by vm.settings.collectAsState()
    val pendingNavigationRoute by vm.pendingNavigationRoute.collectAsState()
    val requiresSignIn = settings.apiToken.isBlank()
    val routeItemId = navBackStack?.arguments?.let { args ->
        if (args.containsKey("itemId")) args.getInt("itemId").takeIf { it > 0 } else null
    }
    val shellState = rememberPlayerShellState(
        vm = vm,
        nav = nav,
        currentRoute = currentRoute,
        routeItemId = routeItemId,
    )
    val requestedPlayerItemId = shellState.requestedPlayerItemId
    val playbackActive = shellState.playbackActive
    val manualReadingActive = shellState.manualReadingActive
    val keepScreenOnForSession = shouldKeepScreenOnForSession(
        keepScreenOnEnabled = settings.keepScreenOnDuringSession,
        requiresSignIn = requiresSignIn,
        isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS),
        requestedPlayerItemId = requestedPlayerItemId,
        playbackActive = playbackActive,
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
    val isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS)
    val presentingLocus = isOnLocusRoute
    val libraryShellVisible = !requiresSignIn && !presentingLocus

    LaunchedEffect(vm, snackbarHostState) {
        vm.snackbarMessages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.message,
                actionLabel = message.actionLabel,
                duration = message.duration,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when {
                    message.actionKey == ACTION_KEY_OPEN_DIAGNOSTICS ->
                        nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) { launchSingleTop = true }
                    message.actionKey == ACTION_KEY_OPEN_SETTINGS ->
                        nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                    message.actionKey?.startsWith(ACTION_KEY_OPEN_PLAYLIST_PREFIX) == true -> {
                        message.actionKey
                            .removePrefix(ACTION_KEY_OPEN_PLAYLIST_PREFIX)
                            .toIntOrNull()
                            ?.let { playlistId ->
                                nav.navigate("playlist/$playlistId") { launchSingleTop = true }
                            }
                    }
                    message.actionKey == ACTION_KEY_UNDO_ARCHIVE -> {
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
                    message.actionKey == ACTION_KEY_UNDO_BATCH -> {
                        vm.undoLastBatch()
                            .onSuccess { vm.showSnackbar("Undone") }
                            .onFailure { vm.showSnackbar("Couldn't undo", "Diagnostics", ACTION_KEY_OPEN_DIAGNOSTICS) }
                    }
                    message.actionKey == ACTION_KEY_UNDO_PLAYLIST_REMOVE -> {
                        vm.undoLastPlaylistRemoval()
                            .onSuccess { restored ->
                                vm.showSnackbar(
                                    if (restored == 1) {
                                        "Playlist removal undone"
                                    } else {
                                        "Restored $restored to playlist"
                                    },
                                )
                            }
                            .onFailure {
                                vm.showSnackbar("Couldn't undo", "Diagnostics", ACTION_KEY_OPEN_DIAGNOSTICS)
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

    MainActivityShell(
        vm = vm,
        nav = nav,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        settings = settings,
        currentRoute = currentRoute,
        requiresSignIn = requiresSignIn,
        libraryShellVisible = libraryShellVisible,
        shellState = shellState,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
