package com.mimeo.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.DrawerPanelSide
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode
import com.mimeo.android.model.PlaylistSummary
import com.mimeo.android.ui.components.StatusBanner
import com.mimeo.android.ui.library.LibraryBatchAction
import com.mimeo.android.ui.library.LibraryItemsScreen
import com.mimeo.android.ui.library.LibrarySortOption
import com.mimeo.android.ui.player.MiniPlayer
import com.mimeo.android.ui.player.PlayerScreen
import com.mimeo.android.ui.playlists.PlaylistDetailScreen
import com.mimeo.android.ui.queue.JumpToNowPlayingPill
import com.mimeo.android.ui.queue.QueueScreen
import com.mimeo.android.ui.settings.ConnectivityDiagnosticsScreen
import com.mimeo.android.ui.settings.SettingsScreen
import com.mimeo.android.ui.signin.SignInScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PlayerRouteHandlers(
    val onShowSnackbar: (String, String?, String?) -> Unit,
    val onOpenItem: (Int) -> Unit,
    val onOpenLocusForItem: (Int) -> Unit,
    val onRequestBack: () -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val onChevronSnapChange: (PlayerChevronSnapEdge) -> Unit,
    val onChevronTap: () -> Unit,
)

private fun snapToActiveBottomClearance(
    showMiniPlayer: Boolean,
    controlsMode: PlayerControlsMode,
    shellBottomClearance: Dp,
): Dp {
    if (!showMiniPlayer) return 0.dp
    val playerPanelHeight = when (controlsMode) {
        PlayerControlsMode.FULL -> 96.dp
        PlayerControlsMode.MINIMAL -> 72.dp
        PlayerControlsMode.NUB -> 1.dp
    }
    return playerPanelHeight + shellBottomClearance
}

@Composable
internal fun MainActivityShell(
    vm: AppViewModel,
    nav: NavHostController,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    settings: AppSettings,
    currentRoute: String,
    requiresSignIn: Boolean,
    libraryShellVisible: Boolean,
    shellState: PlayerShellState,
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerItems = remember {
        listOf(
            DrawerDestination(ROUTE_INBOX, "Inbox"),
            DrawerDestination(ROUTE_FAVORITES, "Favorites"),
            DrawerDestination(ROUTE_ARCHIVE, "Archive"),
            DrawerDestination(ROUTE_BIN, "Bin"),
            DrawerDestination(ROUTE_UP_NEXT, "Up Next"),
        )
    }
    val playlists by vm.playlists.collectAsState()
    val signInState by vm.signInState.collectAsState()
    val inboxItems by vm.inboxItems.collectAsState()
    val favoriteItems by vm.favoriteItems.collectAsState()
    val archivedItems by vm.archivedItems.collectAsState()
    val binItems by vm.binItems.collectAsState()
    val inboxSort by vm.inboxSort.collectAsState()
    val favoritesSort by vm.favoritesSort.collectAsState()
    val archiveSort by vm.archiveSort.collectAsState()
    val binSort by vm.binSort.collectAsState()
    val inboxSearchQuery by vm.inboxSearchQuery.collectAsState()
    val favoritesSearchQuery by vm.favoritesSearchQuery.collectAsState()
    val archiveSearchQuery by vm.archiveSearchQuery.collectAsState()
    val binSearchQuery by vm.binSearchQuery.collectAsState()
    val queueOffline by vm.queueOffline.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistDialogName by remember { mutableStateOf("") }
    var locusTabTapSignal by rememberSaveable { mutableIntStateOf(0) }
    var upNextTabTapSignal by rememberSaveable { mutableIntStateOf(0) }
    var playerOpenRequestSignal by rememberSaveable { mutableIntStateOf(0) }
    var snapToActiveSignal by rememberSaveable { mutableIntStateOf(0) }
    var showUpNextSnapPill by remember { mutableStateOf(false) }
    var offlineBannerVisible by rememberSaveable { mutableStateOf(false) }

    val selectedDrawerRoute = resolveSelectedDrawerRoute(currentRoute)
    val isOnLocusRoute = currentRoute.startsWith(ROUTE_LOCUS)
    val isOnUpNextRoute = currentRoute.startsWith(ROUTE_UP_NEXT)
    val presentingLocus = isOnLocusRoute
    val drawerAvailable = !requiresSignIn
    val requestedPlayerItemId = shellState.requestedPlayerItemId
    val readerChromeHidden = shellState.readerChromeHidden
    val playerControlsVisible = !requiresSignIn && requestedPlayerItemId != null && !(presentingLocus && readerChromeHidden)
    val showMiniPlayer = !requiresSignIn && !isOnLocusRoute && requestedPlayerItemId != null
    val showCompactControls = settings.persistentPlayerEnabled
    val snackbarBottomPadding = when {
        playerControlsVisible -> 108.dp
        presentingLocus && readerChromeHidden -> 12.dp
        else -> 16.dp
    }
    val shellBottomClearance = 12.dp
    val snapBottomClearance = snapToActiveBottomClearance(
        showMiniPlayer = showMiniPlayer,
        controlsMode = settings.playerControlsMode,
        shellBottomClearance = shellBottomClearance,
    )
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

    LaunchedEffect(isOnUpNextRoute) {
        if (!isOnUpNextRoute) {
            showUpNextSnapPill = false
        }
    }

    LaunchedEffect(queueOffline) {
        if (!queueOffline) {
            offlineBannerVisible = false
            return@LaunchedEffect
        }
        delay(450)
        if (queueOffline) {
            offlineBannerVisible = true
        }
    }

    val bannerStateLabel = when {
        offlineBannerVisible -> "Offline"
        baseUrlHint != null -> "Connection setup"
        else -> "Status"
    }
    val bannerSummary = when {
        offlineBannerVisible -> ""
        baseUrlHint != null -> "Connection guidance"
        statusLooksError -> "Request failed"
        else -> ""
    }
    val bannerDetail = when {
        offlineBannerVisible && !statusMessage.isNullOrBlank() -> "Cannot reach server at $baseAddress\n$statusMessage"
        offlineBannerVisible -> "Cannot reach server at $baseAddress"
        baseUrlHint != null && !statusMessage.isNullOrBlank() -> "$baseUrlHint\n$statusMessage"
        baseUrlHint != null -> baseUrlHint
        statusLooksError -> statusMessage
        else -> null
    }
    val showGlobalBanner = !requiresSignIn && (offlineBannerVisible || baseUrlHint != null || statusLooksError)

    val playerHandlers = buildPlayerRouteHandlers(
        vm = vm,
        nav = nav,
        currentRoute = currentRoute,
        onChevronTap = {
            if (drawerAvailable) {
                coroutineScope.launch { drawerState.open() }
            }
        },
    )

    val drawerLayoutDirection = when (settings.drawerPanelSide) {
        DrawerPanelSide.RIGHT -> LayoutDirection.Rtl
        DrawerPanelSide.LEFT -> LayoutDirection.Ltr
    }

    LaunchedEffect(drawerAvailable) {
        if (!drawerAvailable && drawerState.isOpen) {
            drawerState.close()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides drawerLayoutDirection) {
        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerAvailable,
            drawerContent = {
                if (drawerAvailable) {
                    MimeoDrawerContent(
                        drawerItems = drawerItems,
                        playlists = playlists,
                        selectedDrawerRoute = selectedDrawerRoute,
                        selectedPlaylistId = settings.selectedPlaylistId,
                        onNavItemClick = { route ->
                            if (route == ROUTE_UP_NEXT && selectedDrawerRoute == ROUTE_UP_NEXT) {
                                upNextTabTapSignal += 1
                            }
                            if (route == ROUTE_UP_NEXT) vm.selectPlaylist(null)
                            nav.navigate(route) { launchSingleTop = true }
                            coroutineScope.launch { drawerState.close() }
                        },
                        onPlaylistClick = { playlistId ->
                            vm.selectPlaylist(playlistId)
                            nav.navigate("playlist/$playlistId") { launchSingleTop = true }
                            coroutineScope.launch { drawerState.close() }
                        },
                        onSmartQueueClick = {
                            vm.selectPlaylist(null)
                            nav.navigate(ROUTE_UP_NEXT) { launchSingleTop = true }
                            coroutineScope.launch { drawerState.close() }
                        },
                        onNewPlaylistClick = {
                            coroutineScope.launch { drawerState.close() }
                            showNewPlaylistDialog = true
                        },
                        onSettingsClick = {
                            nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                            coroutineScope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if (showNewPlaylistDialog) {
            AlertDialog(
                onDismissRequest = {
                    showNewPlaylistDialog = false
                    newPlaylistDialogName = ""
                },
                title = { Text("New Playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistDialogName,
                        onValueChange = { newPlaylistDialogName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = newPlaylistDialogName.trim()
                            if (trimmed.isNotEmpty()) {
                                vm.createPlaylist(trimmed)
                            }
                            showNewPlaylistDialog = false
                            newPlaylistDialogName = ""
                        },
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNewPlaylistDialog = false
                            newPlaylistDialogName = ""
                        },
                    ) { Text("Cancel") }
                },
            )
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
                    if (libraryShellVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Text("☰", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = drawerRouteLabel(selectedDrawerRoute, playlists),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
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
                            composable(ROUTE_INBOX) {
                                var loading by rememberSaveable { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    loading = true
                                    vm.loadInboxItems()
                                    loading = false
                                }
                                LibraryItemsScreen(
                                    title = "Inbox",
                                    items = inboxItems,
                                    loading = loading,
                                    emptyMessage = "No inbox items.",
                                    sortOption = inboxSort,
                                    availableSorts = LibrarySortOption.INBOX_SORTS,
                                    searchQuery = inboxSearchQuery,
                                    isInbox = true,
                                    batchActions = listOf(
                                        LibraryBatchAction("Archive", Icons.Default.Archive, "archive"),
                                        LibraryBatchAction("Move to Bin", Icons.Default.Delete, "bin"),
                                        LibraryBatchAction("Favorite", Icons.Default.FavoriteBorder, "favorite_toggle"),
                                    ),
                                    playlists = playlists,
                                    onBatchAddToPlaylist = { playlistId, playlistName, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchAddToPlaylist(playlistId, playlistName, itemIds.toList())
                                        }
                                    },
                                    onBatchAddToUpNext = { itemIds -> vm.playLastBatch(itemIds) },
                                    onSortChange = { vm.setInboxSort(it) },
                                    onSearchQueryChange = { vm.setInboxSearchQuery(it) },
                                    onSearchSubmit = { vm.submitInboxSearch() },
                                    onRefresh = {
                                        loading = true
                                        val result = vm.loadInboxItems()
                                        loading = false
                                        result
                                    },
                                    onOpenItem = shellState.openItemInLocus,
                                    onBatchAction = { action, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchLibraryItems(action, itemIds.toList(), ACTION_KEY_UNDO_BATCH)
                                            loading = true
                                            vm.loadInboxItems()
                                            loading = false
                                        }
                                    },
                                    onPlayNow = { vm.playNow(it) },
                                    onPlayNext = { vm.playNext(it) },
                                    onPlayLast = { vm.playLast(it) },
                                )
                            }
                            composable(ROUTE_FAVORITES) {
                                var loading by rememberSaveable { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    loading = true
                                    vm.loadFavoriteItems()
                                    loading = false
                                }
                                LibraryItemsScreen(
                                    title = "Favorites",
                                    items = favoriteItems,
                                    loading = loading,
                                    emptyMessage = "No favorite items.",
                                    sortOption = favoritesSort,
                                    availableSorts = LibrarySortOption.FAVORITES_SORTS,
                                    searchQuery = favoritesSearchQuery,
                                    batchActions = listOf(
                                        LibraryBatchAction("Archive", Icons.Default.Archive, "archive"),
                                        LibraryBatchAction("Move to Bin", Icons.Default.Delete, "bin"),
                                        LibraryBatchAction("Unfavorite", Icons.Default.FavoriteBorder, "favorite_toggle"),
                                    ),
                                    playlists = playlists,
                                    onBatchAddToPlaylist = { playlistId, playlistName, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchAddToPlaylist(playlistId, playlistName, itemIds.toList())
                                        }
                                    },
                                    onBatchAddToUpNext = { itemIds -> vm.playLastBatch(itemIds) },
                                    onSortChange = { vm.setFavoritesSort(it) },
                                    onSearchQueryChange = { vm.setFavoritesSearchQuery(it) },
                                    onSearchSubmit = { vm.submitFavoritesSearch() },
                                    onRefresh = {
                                        loading = true
                                        val result = vm.loadFavoriteItems()
                                        loading = false
                                        result
                                    },
                                    onOpenItem = shellState.openItemInLocus,
                                    onBatchAction = { action, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchLibraryItems(action, itemIds.toList(), ACTION_KEY_UNDO_BATCH)
                                            loading = true
                                            vm.loadFavoriteItems()
                                            loading = false
                                        }
                                    },
                                    onPlayNow = { vm.playNow(it) },
                                    onPlayNext = { vm.playNext(it) },
                                    onPlayLast = { vm.playLast(it) },
                                )
                            }
                            composable(ROUTE_ARCHIVE) {
                                var loading by rememberSaveable { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    loading = true
                                    vm.loadArchivedItems()
                                    loading = false
                                }
                                LibraryItemsScreen(
                                    title = "Archive",
                                    items = archivedItems,
                                    loading = loading,
                                    emptyMessage = "No archived items.",
                                    sortOption = archiveSort,
                                    availableSorts = LibrarySortOption.ARCHIVE_SORTS,
                                    searchQuery = archiveSearchQuery,
                                    batchActions = listOf(
                                        LibraryBatchAction("Unarchive", Icons.Default.Unarchive, "unarchive"),
                                        LibraryBatchAction("Move to Bin", Icons.Default.Delete, "bin"),
                                    ),
                                    playlists = playlists,
                                    onBatchAddToPlaylist = { playlistId, playlistName, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchAddToPlaylist(playlistId, playlistName, itemIds.toList())
                                        }
                                    },
                                    onBatchAddToUpNext = { itemIds -> vm.playLastBatch(itemIds) },
                                    onSortChange = { vm.setArchiveSort(it) },
                                    onSearchQueryChange = { vm.setArchiveSearchQuery(it) },
                                    onSearchSubmit = { vm.submitArchiveSearch() },
                                    onRefresh = {
                                        loading = true
                                        val result = vm.loadArchivedItems()
                                        loading = false
                                        result
                                    },
                                    onOpenItem = shellState.openItemInLocus,
                                    onBatchAction = { action, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchLibraryItems(action, itemIds.toList(), ACTION_KEY_UNDO_BATCH)
                                            loading = true
                                            vm.loadArchivedItems()
                                            loading = false
                                        }
                                    },
                                    onPlayNow = { vm.playNow(it) },
                                    onPlayNext = { vm.playNext(it) },
                                    onPlayLast = { vm.playLast(it) },
                                )
                            }
                            composable(ROUTE_BIN) {
                                var loading by rememberSaveable { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    loading = true
                                    vm.loadBinItems()
                                    loading = false
                                }
                                LibraryItemsScreen(
                                    title = "Bin",
                                    items = binItems,
                                    loading = loading,
                                    emptyMessage = "Bin is empty.",
                                    sortOption = binSort,
                                    availableSorts = LibrarySortOption.BIN_SORTS,
                                    searchQuery = binSearchQuery,
                                    batchActions = listOf(
                                        LibraryBatchAction("Restore", Icons.Default.Restore, "restore"),
                                    ),
                                    onSortChange = { vm.setBinSort(it) },
                                    onSearchQueryChange = { vm.setBinSearchQuery(it) },
                                    onSearchSubmit = { vm.submitBinSearch() },
                                    onRefresh = {
                                        loading = true
                                        val result = vm.loadBinItems()
                                        loading = false
                                        result
                                    },
                                    onOpenItem = shellState.openItemInLocus,
                                    onBatchAction = { action, itemIds ->
                                        coroutineScope.launch {
                                            vm.batchLibraryItems(action, itemIds.toList(), ACTION_KEY_UNDO_BATCH)
                                            loading = true
                                            vm.loadBinItems()
                                            loading = false
                                        }
                                    },
                                )
                            }
                            composable(
                                ROUTE_PLAYLIST_DETAIL,
                                arguments = listOf(navArgument("playlistId") { type = NavType.IntType }),
                            ) { backStack ->
                                val playlistId = backStack.arguments?.getInt("playlistId") ?: return@composable
                                PlaylistDetailScreen(
                                    playlistId = playlistId,
                                    vm = vm,
                                    onOpenPlayer = shellState.openItemInLocus,
                                    onShowSnackbar = playerHandlers.onShowSnackbar,
                                    onNavigateBack = { nav.popBackStack() },
                                )
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
                                LocusPlayerRoute(
                                    vm = vm,
                                    shellState = shellState,
                                    requestedPlayerItemId = requestedPlayerItemId,
                                    playerHandlers = playerHandlers,
                                    locusTapSignal = locusTabTapSignal,
                                    openRequestSignal = playerOpenRequestSignal,
                                    showCompactControls = showCompactControls,
                                    settings = settings,
                                    shellBottomClearance = shellBottomClearance,
                                    onGoQueue = { nav.navigate(ROUTE_UP_NEXT) },
                                    onChevronTap = playerHandlers.onChevronTap,
                                    drawerIsOpen = drawerState.isOpen,
                                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                                )
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
                                    onShowSnackbar = playerHandlers.onShowSnackbar,
                                    focusItemId = focusItemId,
                                    upNextTabTapSignal = upNextTabTapSignal,
                                    snapToActiveSignal = snapToActiveSignal,
                                    snapBottomClearance = snapBottomClearance,
                                    renderSnapPillLocally = !showMiniPlayer,
                                    onSnapPillVisibilityChange = { showUpNextSnapPill = it },
                                    onOpenPlayer = shellState.openItemInLocus,
                                    onOpenDiagnostics = playerHandlers.onOpenDiagnostics,
                                )
                            }
                            composable(
                                ROUTE_LOCUS_ITEM,
                                arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
                            ) {
                                LocusPlayerRoute(
                                    vm = vm,
                                    shellState = shellState,
                                    requestedPlayerItemId = requestedPlayerItemId,
                                    playerHandlers = playerHandlers,
                                    locusTapSignal = locusTabTapSignal,
                                    openRequestSignal = playerOpenRequestSignal,
                                    showCompactControls = showCompactControls,
                                    settings = settings,
                                    shellBottomClearance = shellBottomClearance,
                                    onGoQueue = { nav.navigate(ROUTE_UP_NEXT) },
                                    onChevronTap = playerHandlers.onChevronTap,
                                    drawerIsOpen = drawerState.isOpen,
                                    onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                                )
                            }
                        }

                        if (showMiniPlayer) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                            ) {
                                MiniPlayer(
                                    vm = vm,
                                    onShowSnackbar = playerHandlers.onShowSnackbar,
                                    initialItemId = requestedPlayerItemId,
                                    requestedItemId = requestedPlayerItemId,
                                    locusTapSignal = locusTabTapSignal,
                                    openRequestSignal = playerOpenRequestSignal,
                                    onOpenItem = playerHandlers.onOpenItem,
                                    onOpenLocusForItem = playerHandlers.onOpenLocusForItem,
                                    onRequestBack = playerHandlers.onRequestBack,
                                    onOpenDiagnostics = playerHandlers.onOpenDiagnostics,
                                    onChevronTap = playerHandlers.onChevronTap,
                                    showCompactControls = showCompactControls,
                                    controlsMode = settings.playerControlsMode,
                                    lastNonNubMode = settings.playerLastNonNubMode,
                                    chevronSnapEdge = settings.playerChevronSnapEdge,
                                    onControlsModeChange = shellState.onControlsModeChange,
                                    onPlaybackActiveChange = shellState.onPlaybackActiveChange,
                                    onManualReadingActiveChange = shellState.onManualReadingActiveChange,
                                    onReaderChromeVisibilityChange = shellState.onReaderChromeVisibilityChange,
                                    onChevronSnapChange = playerHandlers.onChevronSnapChange,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(shellBottomClearance)
                                        .background(Color.Black),
                                )
                            }
                        }
                        if (showMiniPlayer && isOnUpNextRoute && showUpNextSnapPill) {
                            JumpToNowPlayingPill(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = snapBottomClearance),
                                onClick = { snapToActiveSignal += 1 },
                            )
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.ime)
                        .padding(bottom = snackbarBottomPadding),
                )
            }
        }
        }
    }
    }
}

@Composable
private fun LocusPlayerRoute(
    vm: AppViewModel,
    shellState: PlayerShellState,
    requestedPlayerItemId: Int?,
    playerHandlers: PlayerRouteHandlers,
    locusTapSignal: Int,
    openRequestSignal: Int,
    showCompactControls: Boolean,
    settings: AppSettings,
    shellBottomClearance: Dp,
    onGoQueue: () -> Unit,
    onChevronTap: () -> Unit,
    drawerIsOpen: Boolean,
    onCloseDrawer: () -> Unit,
) {
    if (requestedPlayerItemId == null) {
        NoNowPlayingScreen(onGoQueue = onGoQueue)
        return
    }
    PlayerScreen(
        vm = vm,
        onShowSnackbar = playerHandlers.onShowSnackbar,
        initialItemId = requestedPlayerItemId,
        requestedItemId = requestedPlayerItemId,
        locusTapSignal = locusTapSignal,
        openRequestSignal = openRequestSignal,
        onOpenItem = playerHandlers.onOpenItem,
        onOpenLocusForItem = playerHandlers.onOpenLocusForItem,
        onRequestBack = playerHandlers.onRequestBack,
        onOpenDiagnostics = playerHandlers.onOpenDiagnostics,
        onChevronTap = onChevronTap,
        drawerIsOpen = drawerIsOpen,
        onCloseDrawer = onCloseDrawer,
        compactControlsOnly = false,
        showCompactControls = showCompactControls,
        controlsMode = settings.playerControlsMode,
        lastNonNubMode = settings.playerLastNonNubMode,
        chevronSnapEdge = settings.playerChevronSnapEdge,
        onControlsModeChange = shellState.onControlsModeChange,
        onPlaybackActiveChange = shellState.onPlaybackActiveChange,
        onManualReadingActiveChange = shellState.onManualReadingActiveChange,
        onReaderChromeVisibilityChange = shellState.onReaderChromeVisibilityChange,
        onChevronSnapChange = playerHandlers.onChevronSnapChange,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = shellBottomClearance),
    )
}

private fun buildPlayerRouteHandlers(
    vm: AppViewModel,
    nav: NavHostController,
    currentRoute: String,
    onChevronTap: () -> Unit,
): PlayerRouteHandlers {
    return PlayerRouteHandlers(
        onShowSnackbar = { message, actionLabel, actionKey ->
            vm.showSnackbar(message, actionLabel, actionKey)
        },
        onOpenItem = { nextId ->
            if (currentRoute.startsWith(ROUTE_LOCUS)) {
                nav.navigate("$ROUTE_LOCUS/$nextId") {
                    launchSingleTop = true
                }
            }
        },
        onOpenLocusForItem = { itemId ->
            nav.navigate("$ROUTE_LOCUS/$itemId") {
                launchSingleTop = true
            }
        },
        onRequestBack = {
            if (!nav.popBackStack()) {
                nav.navigate(ROUTE_UP_NEXT) { launchSingleTop = true }
            }
        },
        onOpenDiagnostics = {
            nav.navigate(ROUTE_SETTINGS_DIAGNOSTICS) { launchSingleTop = true }
        },
        onChevronSnapChange = { edge ->
            vm.savePlayerChevronSnap(edge, 0.5f)
        },
        onChevronTap = onChevronTap,
    )
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

private fun resolveSelectedDrawerRoute(currentRoute: String): String = when {
    currentRoute.startsWith(ROUTE_SETTINGS_DIAGNOSTICS) -> ROUTE_SETTINGS
    currentRoute.startsWith(ROUTE_SETTINGS) -> ROUTE_SETTINGS
    currentRoute.startsWith("playlist/") -> currentRoute
    currentRoute.startsWith(ROUTE_UP_NEXT) -> ROUTE_UP_NEXT
    currentRoute.startsWith(ROUTE_ARCHIVE) -> ROUTE_ARCHIVE
    currentRoute.startsWith(ROUTE_BIN) -> ROUTE_BIN
    currentRoute.startsWith(ROUTE_FAVORITES) -> ROUTE_FAVORITES
    currentRoute.startsWith(ROUTE_INBOX) -> ROUTE_INBOX
    else -> ROUTE_UP_NEXT
}

private fun drawerRouteLabel(route: String, playlists: List<PlaylistSummary>): String = when {
    route == ROUTE_INBOX -> "Inbox"
    route == ROUTE_FAVORITES -> "Favorites"
    route == ROUTE_ARCHIVE -> "Archive"
    route == ROUTE_BIN -> "Bin"
    route == ROUTE_UP_NEXT -> "Up Next"
    route == ROUTE_SETTINGS -> "Settings"
    route.startsWith("playlist/") -> {
        val id = route.removePrefix("playlist/").toIntOrNull()
        playlists.firstOrNull { it.id == id }?.name ?: "Playlist"
    }
    else -> "Mimeo"
}
