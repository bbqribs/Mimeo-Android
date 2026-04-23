package com.mimeo.android

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.mimeo.android.model.PlayerControlsMode
import kotlinx.coroutines.delay

data class PlayerShellState(
    val requestedPlayerItemId: Int?,
    val playbackActive: Boolean,
    val manualReadingActive: Boolean,
    val readerChromeHidden: Boolean,
    val openItemInLocus: (Int) -> Unit,
    val onPlaybackActiveChange: (Boolean) -> Unit,
    val onManualReadingActiveChange: (Boolean) -> Unit,
    val onReaderChromeVisibilityChange: (Boolean) -> Unit,
    val onControlsModeChange: (PlayerControlsMode, PlayerControlsMode) -> Unit,
)

@Composable
fun rememberPlayerShellState(
    vm: AppViewModel,
    nav: NavController,
    currentRoute: String,
    routeItemId: Int?,
): PlayerShellState {
    val sessionNowPlayingItemId = vm.currentNowPlayingItemId()

    var pendingLocusOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLocusItemId by rememberSaveable { mutableIntStateOf(-1) }
    var playbackActive by rememberSaveable { mutableStateOf(false) }
    var manualReadingActive by rememberSaveable { mutableStateOf(false) }
    var readerChromeHidden by rememberSaveable { mutableStateOf(false) }

    val requestedPlayerItemId =
        routeItemId
            ?: pendingLocusItemId.takeIf { pendingLocusOpen && it > 0 }
            ?: sessionNowPlayingItemId

    val openItemInLocus: (Int) -> Unit = { itemId ->
        pendingLocusOpen = true
        pendingLocusItemId = itemId
        nav.navigate("$ROUTE_LOCUS/$itemId") {
            launchSingleTop = true
        }
    }

    LaunchedEffect(sessionNowPlayingItemId, routeItemId, requestedPlayerItemId, currentRoute, pendingLocusOpen, pendingLocusItemId) {
        Log.d(
            LOCUS_CONTINUATION_DEBUG_TAG,
            "mimeoApp route=$currentRoute routeItemId=$routeItemId sessionItemId=$sessionNowPlayingItemId requestedItemId=$requestedPlayerItemId " +
                "handoffPending=$pendingLocusOpen handoffTarget=$pendingLocusItemId",
        )
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute.startsWith(ROUTE_LOCUS)) {
            pendingLocusOpen = false
            pendingLocusItemId = -1
        }
    }

    LaunchedEffect(pendingLocusOpen, currentRoute) {
        if (!pendingLocusOpen || currentRoute.startsWith(ROUTE_LOCUS)) return@LaunchedEffect
        delay(750)
        if (pendingLocusOpen && !currentRoute.startsWith(ROUTE_LOCUS)) {
            pendingLocusOpen = false
            pendingLocusItemId = -1
        }
    }

    return PlayerShellState(
        requestedPlayerItemId = requestedPlayerItemId,
        playbackActive = playbackActive,
        manualReadingActive = manualReadingActive,
        readerChromeHidden = readerChromeHidden,
        openItemInLocus = openItemInLocus,
        onPlaybackActiveChange = { active -> playbackActive = active },
        onManualReadingActiveChange = { active -> manualReadingActive = active },
        onReaderChromeVisibilityChange = { hidden -> readerChromeHidden = hidden },
        onControlsModeChange = { mode, lastNonNubMode ->
            vm.savePlayerControlsState(mode, lastNonNubMode)
        },
    )
}
