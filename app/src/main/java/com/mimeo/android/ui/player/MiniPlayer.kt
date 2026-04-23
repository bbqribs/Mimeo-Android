package com.mimeo.android.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.PlayerControlsMode

@Composable
fun MiniPlayer(
    vm: AppViewModel,
    onShowSnackbar: (String, String?, String?) -> Unit,
    initialItemId: Int,
    requestedItemId: Int? = null,
    locusTapSignal: Int = 0,
    openRequestSignal: Int = 0,
    onOpenItem: (Int) -> Unit,
    onOpenLocusForItem: (Int) -> Unit,
    onRequestBack: () -> Unit = {},
    onOpenDiagnostics: () -> Unit,
    onChevronTap: () -> Unit = {},
    showCompactControls: Boolean = true,
    controlsMode: PlayerControlsMode = PlayerControlsMode.FULL,
    lastNonNubMode: PlayerControlsMode = PlayerControlsMode.FULL,
    chevronSnapEdge: PlayerChevronSnapEdge = PlayerChevronSnapEdge.RIGHT,
    onControlsModeChange: (PlayerControlsMode, PlayerControlsMode) -> Unit = { _, _ -> },
    onPlaybackActiveChange: (Boolean) -> Unit = {},
    onManualReadingActiveChange: (Boolean) -> Unit = {},
    onPlaybackProgressPercentChange: (Int) -> Unit = {},
    onReaderChromeVisibilityChange: (Boolean) -> Unit = {},
    onChevronSnapChange: (PlayerChevronSnapEdge) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlayerScreen(
        vm = vm,
        onShowSnackbar = onShowSnackbar,
        initialItemId = initialItemId,
        requestedItemId = requestedItemId,
        locusTapSignal = locusTapSignal,
        openRequestSignal = openRequestSignal,
        onOpenItem = onOpenItem,
        onOpenLocusForItem = onOpenLocusForItem,
        onRequestBack = onRequestBack,
        onOpenDiagnostics = onOpenDiagnostics,
        onChevronTap = onChevronTap,
        compactControlsOnly = true,
        showCompactControls = showCompactControls,
        controlsMode = controlsMode,
        lastNonNubMode = lastNonNubMode,
        chevronSnapEdge = chevronSnapEdge,
        onControlsModeChange = onControlsModeChange,
        onPlaybackActiveChange = onPlaybackActiveChange,
        onManualReadingActiveChange = onManualReadingActiveChange,
        onPlaybackProgressPercentChange = onPlaybackProgressPercentChange,
        onReaderChromeVisibilityChange = onReaderChromeVisibilityChange,
        onChevronSnapChange = onChevronSnapChange,
        modifier = modifier,
    )
}
