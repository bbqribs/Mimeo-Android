package com.mimeo.android.ui.bluesky

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyCandidate
import com.mimeo.android.model.BlueskyCandidateScanResponse
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.model.BlueskyPickerPinItem
import com.mimeo.android.model.BlueskyPickerResponse
import com.mimeo.android.ui.components.RefreshActionButton
import com.mimeo.android.ui.components.RefreshActionVisualState
import com.mimeo.android.ui.common.JumpPill
import com.mimeo.android.ui.common.jumpPillBottomPadding
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.common.shouldShowJumpToTopLazy
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BlueskyBrowseScreen(
    vm: AppViewModel,
    onOpenItem: (Int) -> Unit,
    onOpenConnectionSettings: () -> Unit = {},
    jumpPillBottomClearance: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val actionScope = rememberCoroutineScope()
    val picker by vm.blueskyCandidatePicker.collectAsState()
    val pickerLoading by vm.blueskyCandidatePickerLoading.collectAsState()
    val pickerError by vm.blueskyCandidatePickerError.collectAsState()
    val selection by vm.blueskyCandidateSelection.collectAsState()
    val scan by vm.blueskyCandidateScan.collectAsState()
    val scanning by vm.blueskyCandidateLoading.collectAsState()
    val scanError by vm.blueskyCandidateError.collectAsState()
    val savingUrls by vm.blueskyCandidateSavingUrls.collectAsState()
    val saveErrors by vm.blueskyCandidateSaveErrors.collectAsState()
    val pinning by vm.blueskyCandidatePinning.collectAsState()
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    // Only show the pull-to-refresh indicator for an explicit pull, not for the initial
    // automatic picker load / home-timeline scan that runs when the screen first opens.
    var userRefreshing by remember { mutableStateOf(false) }
    val showJumpToTop by remember {
        derivedStateOf {
            shouldShowJumpToTopLazy(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(Unit) {
        vm.loadBlueskyCandidatePicker()
    }

    LaunchedEffect(picker?.timeline?.available, selection, scan, scanning) {
        if (picker?.timeline?.available == true && selection == null && scan == null && !scanning) {
            vm.scanBlueskyCandidateSource(
                BlueskyCandidateSourceSelection(
                    sourceKind = "home_timeline",
                    displayLabel = "Bluesky Home Timeline",
                ),
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = userRefreshing,
        onRefresh = {
            // Refresh the picker (connection health, pins, lists/feeds) and re-scan the
            // active source so the candidate list reloads. When nothing is selected yet,
            // the home-timeline auto-scan effect repopulates the list after the reload.
            actionScope.launch {
                userRefreshing = true
                try {
                    vm.loadBlueskyCandidatePicker()
                    selection?.let { vm.scanBlueskyCandidateSource(it) }
                    // Loading flags flip asynchronously; wait for them to start and then
                    // settle so the indicator tracks the real reload (timeout guards the
                    // case where no request runs, e.g. a missing token).
                    withTimeoutOrNull(10_000) {
                        snapshotFlow { pickerLoading || scanning }.first { it }
                        snapshotFlow { pickerLoading || scanning }.first { !it }
                    }
                } finally {
                    userRefreshing = false
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .then(if (isV1) Modifier.background(mColors.bg) else Modifier)
            .passiveVerticalScrollIndicator(
                listState = listState,
                color = if (isV1) mColors.fg4 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                BlueskyHealthHeader(
                    connection = picker?.connection,
                    loading = pickerLoading,
                    onOpenConnectionSettings = onOpenConnectionSettings,
                    onTryAgain = vm::loadBlueskyCandidatePicker,
                )
            }
            item {
                SourcePicker(
                    picker = picker,
                    loading = pickerLoading,
                    error = pickerError,
                    selected = selection,
                    scan = scan,
                    onReload = vm::loadBlueskyCandidatePicker,
                    onScan = vm::scanBlueskyCandidateSource,
                )
            }
            item {
                ScanStatus(
                    scan = scan,
                    selected = selection,
                    pins = picker?.pins.orEmpty(),
                    scanning = scanning,
                    error = scanError,
                    pinning = pinning,
                    onPin = vm::pinCurrentBlueskyCandidateSource,
                    onUnpin = vm::unpinBlueskyCandidateSource,
                )
            }
            when {
                scanning -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                scan != null && scan!!.candidates.isEmpty() -> item {
                    Text(
                        text = "No links found for this source in the current scan window.",
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
                scan != null -> items(scan!!.candidates, key = { it.articleUrl }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        saving = savingUrls.contains(candidate.articleUrl),
                        saveError = saveErrors[candidate.articleUrl],
                        onSave = { vm.saveBlueskyCandidate(candidate) },
                        onOpenItem = onOpenItem,
                    )
                }
            }
        }
        if (showJumpToTop) {
            JumpPill(
                label = "Jump to top",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = jumpPillBottomPadding(jumpPillBottomClearance)),
                onClick = { actionScope.launch { listState.animateScrollToItem(0) } },
            )
        }
    }
}

@Composable
private fun BlueskyHealthHeader(
    connection: BlueskyAccountConnectionResponse?,
    loading: Boolean,
    onOpenConnectionSettings: () -> Unit,
    onTryAgain: () -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val health = resolveBlueskyHealth(connection, null)
    val statusColor = when (health.state) {
        BlueskyHealthState.CONNECTED -> if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface
        BlueskyHealthState.ACTION_NEEDED -> if (isV1) mColors.danger else MaterialTheme.colorScheme.error
        BlueskyHealthState.TEMPORARY_TROUBLE -> Color(0xFFB8860B)
        else -> if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val actionLabel = when (health.action) {
        BlueskyRecoveryAction.CONNECT -> "Connect"
        BlueskyRecoveryAction.RECONNECT -> "Reconnect"
        BlueskyRecoveryAction.TRY_AGAIN -> "Try again"
        BlueskyRecoveryAction.NONE -> null
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.medium,
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isV1) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        // When connected, tapping the status opens the Bluesky settings
                        // section to manage/reconnect the account.
                        if (health.handle != null) {
                            Modifier.clickable { onOpenConnectionSettings() }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = health.title,
                    style = if (isV1) mTypography.row else MaterialTheme.typography.titleSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    loading -> "Checking Bluesky status…"
                    health.detail != null -> health.detail
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (actionLabel != null) {
                OutlinedButton(
                    enabled = !loading,
                    onClick = {
                        when (health.action) {
                            BlueskyRecoveryAction.CONNECT, BlueskyRecoveryAction.RECONNECT -> onOpenConnectionSettings()
                            else -> onTryAgain()
                        }
                    },
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SourcePicker(
    picker: BlueskyPickerResponse?,
    loading: Boolean,
    error: String?,
    selected: BlueskyCandidateSourceSelection?,
    scan: BlueskyCandidateScanResponse?,
    onReload: () -> Unit,
    onScan: (BlueskyCandidateSourceSelection) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    var expanded by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val pinnedOptions = picker.pinnedSourceOptions()
    val browseOptions = picker.browseSourceOptions()
    val sectionChevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "sourcePickerChevron",
    )
    val dropdownChevronRotation by animateFloatAsState(
        targetValue = if (dropdownExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "sourceDropdownChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.large,
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val activeSourceLabel = if (scan != null || selected != null) {
                    resolvePickerSourceLabel(
                        scanLabel = scan?.source?.displayLabel,
                        scanType = scan?.source?.sourceType,
                        selectedLabel = selected?.displayLabel,
                        selectedKind = selected?.sourceKind,
                    )
                } else {
                    "Choose a source"
                }
                val scannedStats = scan?.let { blueskyScanStatsCopy(it) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = activeSourceLabel,
                        style = if (isV1) mTypography.row else MaterialTheme.typography.titleMedium,
                        color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (scannedStats != null) {
                        Text(
                            text = scannedStats,
                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                RefreshActionButton(
                    state = if (loading) RefreshActionVisualState.Refreshing else RefreshActionVisualState.Idle,
                    showConnectivityIssue = false,
                    onClick = onReload,
                    contentDescription = "Refresh Bluesky sources",
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(sectionChevronRotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!error.isNullOrBlank()) {
                        Text(
                            error,
                            color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        )
                    }
                    val connected = picker?.connection?.connected == true
                    val connectedHandle = picker?.connection?.handle ?: "Bluesky"
                    // Only surface the connection line when it carries error/status
                    // signal; a healthy connected account stays implicit to cut bulk.
                    if (!connected) {
                        Text(
                            text = sourcePickerConnectionCopy(
                                picker = picker,
                                pickerError = error,
                                connected = connected,
                                connectedHandle = connectedHandle,
                            ),
                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (pinnedOptions.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            pinnedOptions.forEach { option ->
                                val isSelected = selected != null &&
                                    selected.sourceKind == option.selection.sourceKind &&
                                    (selected.actor ?: "") == (option.selection.actor ?: "") &&
                                    (selected.uri ?: "") == (option.selection.uri ?: "")
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onScan(option.selection) },
                                    label = {
                                        Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    enabled = !loading,
                                    colors = if (isV1) FilterChipDefaults.filterChipColors(
                                        labelColor = mColors.fg3,
                                        selectedContainerColor = mColors.accentDim,
                                        selectedLabelColor = mColors.accent,
                                    ) else FilterChipDefaults.filterChipColors(
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }

                    if (browseOptions.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Browse lists & feeds",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(dropdownChevronRotation),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 280.dp, max = 420.dp)
                                    .heightIn(max = 320.dp),
                            ) {
                                browseOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        onClick = {
                                            dropdownExpanded = false
                                            onScan(option.selection)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    BlueskyCandidateInputSection(loading = loading, onScan = onScan)
                }
            }
        }
    }
}

@Composable
private fun ScanStatus(
    scan: BlueskyCandidateScanResponse?,
    selected: BlueskyCandidateSourceSelection?,
    pins: List<BlueskyPickerPinItem>,
    scanning: Boolean,
    error: String?,
    pinning: Boolean,
    onPin: () -> Unit,
    onUnpin: (Int) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    if (scan == null && selected == null && error.isNullOrBlank() && !scanning) {
        Text(
            text = "Choose a source to scan for links from Bluesky.",
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val pinnedSourceId = findPinnedSourceId(scan, selected, pins)
    val sourceType = scan?.source?.sourceType ?: selected?.sourceKind
    val pinSupported = sourceType == "author_feed" || sourceType == "list_feed" || sourceType == "account"
    val showPinRow = pinSupported && (scan != null || pinnedSourceId != null)
    // The source title and scan counts now live in the picker header, so this card
    // only renders when it carries an error or pin controls.
    if (error.isNullOrBlank() && !showPinRow) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.medium,
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isV1) 0.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!error.isNullOrBlank()) {
                Text(
                    error,
                    color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                )
            }
            if (showPinRow) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pinnedSourceId != null) {
                        OutlinedButton(onClick = { onUnpin(pinnedSourceId) }, enabled = !pinning) {
                            Text(if (pinning) "Updating..." else "Unpin source")
                        }
                    } else if (scan != null) {
                        OutlinedButton(onClick = onPin, enabled = !pinning) {
                            Text(if (pinning) "Pinning..." else "Pin source")
                        }
                    }
                    Text(
                        text = blueskyPinShortcutCopy(),
                        style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: BlueskyCandidate,
    saving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
    onOpenItem: (Int) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.large,
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { uriHandler.openUri(candidate.articleUrl) },
                ) {
                    Text(
                        text = candidate.title?.takeIf { it.isNotBlank() } ?: "Untitled link",
                        style = if (isV1) mTypography.row else MaterialTheme.typography.titleMedium,
                        color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = candidate.domain?.takeIf { it.isNotBlank() } ?: "External link",
                        style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SaveActionChip(
                    candidate = candidate,
                    saving = saving,
                    onSave = onSave,
                    onOpenItem = onOpenItem,
                    modifier = Modifier.offset(y = (-8).dp),
                )
            }
            val postMeta = buildList {
                candidate.bluesky.authorDisplayName?.takeIf { it.isNotBlank() }?.let { add(it) }
                candidate.bluesky.authorHandle?.takeIf { it.isNotBlank() }?.let { add("@$it") }
                candidate.bluesky.indexedAt?.takeIf { it.isNotBlank() }?.let { add(formatCandidateTimestamp(it)) }
            }
            val postUrl = candidate.bluesky.postUrl
            val hasPostContent = postMeta.isNotEmpty() || !candidate.bluesky.textSnippet.isNullOrBlank()
            if (hasPostContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!postUrl.isNullOrBlank()) {
                                Modifier.clickable { uriHandler.openUri(postUrl) }
                            } else {
                                Modifier
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (postMeta.isNotEmpty()) {
                        Text(
                            text = postMeta.joinToString(" · "),
                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                            color = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!candidate.bluesky.textSnippet.isNullOrBlank()) {
                        Text(
                            text = candidate.bluesky.textSnippet,
                            style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
                            color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Text(
                text = blueskyCandidateSourceLine(candidate.sourceLabel, candidate.sourceType),
                style = if (isV1) mTypography.meta else MaterialTheme.typography.labelSmall,
                color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!saveError.isNullOrBlank()) {
                Text(
                    saveError,
                    color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SaveActionChip(
    candidate: BlueskyCandidate,
    saving: Boolean,
    onSave: () -> Unit,
    onOpenItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    when {
        candidate.savedState == "failed_saved" ->
            FilterChip(
                selected = false,
                onClick = onSave,
                label = { Text("Retry") },
                modifier = modifier,
                border = BorderStroke(1.dp, if (isV1) mColors.danger.copy(alpha = 0.58f) else MaterialTheme.colorScheme.error),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (isV1) mColors.danger.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
                    labelColor = if (isV1) mColors.danger else MaterialTheme.colorScheme.onErrorContainer,
                ),
            )
        candidate.saved && candidate.itemId != null ->
            FilterChip(
                selected = true,
                onClick = { onOpenItem(candidate.itemId) },
                label = { Text("Read") },
                modifier = modifier,
                border = BorderStroke(1.dp, if (isV1) mColors.accent.copy(alpha = 0.62f) else MaterialTheme.colorScheme.primary),
                colors = if (isV1) FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mColors.accentDim,
                    selectedLabelColor = mColors.accent,
                ) else FilterChipDefaults.filterChipColors(),
            )
        else ->
            FilterChip(
                selected = false,
                enabled = !saving,
                onClick = onSave,
                label = { Text(if (saving) "Saving…" else "Save") },
                modifier = modifier,
                border = BorderStroke(
                    1.dp,
                    if (isV1) mColors.fg4.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline,
                ),
                colors = if (isV1) FilterChipDefaults.filterChipColors(
                    containerColor = mColors.surfaceHi,
                    labelColor = mColors.fg2,
                    disabledContainerColor = mColors.surfaceHi.copy(alpha = 0.58f),
                    disabledLabelColor = mColors.fg3,
                ) else FilterChipDefaults.filterChipColors(),
            )
    }
}

internal fun blueskyScanStatsCopy(scan: BlueskyCandidateScanResponse): String =
    "Checked ${scan.scan.postsScanned} posts, found ${scan.candidates.size} links"

internal fun blueskyPinShortcutCopy(): String =
    "Pinning only stores the shortcut; it does not save links."

internal fun blueskyCandidateSourceLine(sourceLabel: String, sourceType: String?): String {
    val label = cleanSourceLabel(sourceLabel, sourceType)
    val type = formatSourceType(sourceType)
    return if (label.equals(type, ignoreCase = true)) label else "$label · $type"
}

private data class SourceDropdownOption(
    val label: String,
    val selection: BlueskyCandidateSourceSelection,
)

private fun sourcePickerConnectionCopy(
    picker: BlueskyPickerResponse?,
    pickerError: String?,
    connected: Boolean,
    connectedHandle: String,
): String = when {
    picker == null && !pickerError.isNullOrBlank() ->
        "Could not load Bluesky sources. Account scans may still work if you enter a handle."
    connected ->
        "Connected as @$connectedHandle"
    else ->
        "No connected Bluesky account. Account scans can still use public author feeds; timeline and lists require a connection."
}

private fun BlueskyPickerResponse?.pinnedSourceOptions(): List<SourceDropdownOption> {
    if (this == null) return emptyList()
    return buildList {
        if (timeline.available) {
            add(
                SourceDropdownOption(
                    label = "Home Timeline",
                    selection = BlueskyCandidateSourceSelection(
                        sourceKind = "home_timeline",
                        displayLabel = "Bluesky Home Timeline",
                    ),
                ),
            )
        }
        pins.forEach { pin ->
            val label = cleanSourceLabel(pin.resolvedLabel, pin.kind)
            add(
                SourceDropdownOption(
                    label = label,
                    selection = BlueskyCandidateSourceSelection(
                        sourceKind = pin.kind,
                        displayLabel = label,
                        actor = pin.handle,
                        uri = pin.uri,
                        sourceId = pin.sourceId,
                    ),
                ),
            )
        }
    }
}

private fun BlueskyPickerResponse?.browseSourceOptions(): List<SourceDropdownOption> {
    if (this == null) return emptyList()
    return buildList {
        lists.forEach { list ->
            add(
                SourceDropdownOption(
                    label = list.name,
                    selection = BlueskyCandidateSourceSelection(
                        sourceKind = "list_feed",
                        displayLabel = list.name,
                        uri = list.uri,
                    ),
                ),
            )
        }
        feeds.forEach { feed ->
            add(
                SourceDropdownOption(
                    label = feed.displayName,
                    selection = BlueskyCandidateSourceSelection(
                        sourceKind = "feed_generator",
                        displayLabel = feed.displayName,
                        uri = feed.uri,
                    ),
                ),
            )
        }
    }
}

internal fun findPinnedSourceId(
    scan: BlueskyCandidateScanResponse?,
    selected: BlueskyCandidateSourceSelection?,
    pins: List<BlueskyPickerPinItem>,
): Int? {
    val scanSourceId = scan?.source?.sourceId
    if (scanSourceId != null && pins.any { it.sourceId == scanSourceId }) return scanSourceId
    val selectedSourceId = selected?.sourceId
    if (selectedSourceId != null && pins.any { it.sourceId == selectedSourceId }) return selectedSourceId
    val sourceType = scan?.source?.sourceType ?: selected?.sourceKind
    val identifier = scan?.source?.identifier
    return when (sourceType) {
        "author_feed", "account" -> {
            val actor = (identifier ?: selected?.actor).orEmpty().trimStart('@')
            pins.firstOrNull { it.kind == "author_feed" && it.handle.equals(actor, ignoreCase = true) }?.sourceId
        }
        "list_feed" -> {
            val uri = identifier ?: selected?.uri
            pins.firstOrNull { it.kind == "list_feed" && it.uri == uri }?.sourceId
        }
        else -> null
    }
}
