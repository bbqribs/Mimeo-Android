package com.mimeo.android.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mimeo.android.AppViewModel
import com.mimeo.android.PlayerShellState
import com.mimeo.android.model.AppSettings

@Composable
fun MiniPlayer(
    vm: AppViewModel,
    shellState: PlayerShellState,
    settings: AppSettings,
    onShowSnackbar: (String, String?, String?) -> Unit,
    onOpenLocusForItem: (Int) -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requestedPlayerItemId = shellState.requestedPlayerItemId ?: return
    PlayerScreen(
        vm = vm,
        onShowSnackbar = onShowSnackbar,
        initialItemId = requestedPlayerItemId,
        requestedItemId = requestedPlayerItemId,
        onOpenItem = { /* no-op: auto-continue updates session; mini-player follows naturally */ },
        onOpenLocusForItem = onOpenLocusForItem,
        onRequestBack = {},
        onOpenDiagnostics = onOpenDiagnostics,
        compactControlsOnly = true,
        showCompactControls = true,
        controlsMode = settings.playerControlsMode,
        lastNonNubMode = settings.playerLastNonNubMode,
        chevronSnapEdge = settings.playerChevronSnapEdge,
        onControlsModeChange = shellState.onControlsModeChange,
        onPlaybackActiveChange = shellState.onPlaybackActiveChange,
        onManualReadingActiveChange = shellState.onManualReadingActiveChange,
        onReaderChromeVisibilityChange = shellState.onReaderChromeVisibilityChange,
        onChevronSnapChange = { edge -> vm.savePlayerChevronSnap(edge, 0.5f) },
        modifier = modifier,
    )
}
