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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.BlueskyCandidate
import com.mimeo.android.model.BlueskyCandidateScanResponse
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.model.BlueskyPickerPinItem
import com.mimeo.android.model.BlueskyPickerResponse
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoShapeTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

@Composable
fun BlueskyBrowseScreen(
    vm: AppViewModel,
    onOpenItem: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isV1) Modifier.background(mColors.bg) else Modifier)
            .passiveVerticalScrollIndicator(
                listState = listState,
                color = if (isV1) mColors.line else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Header()
            }
            item {
                ScanDefaults(picker = picker)
            }
            item {
                SourcePicker(
                    picker = picker,
                    loading = pickerLoading,
                    error = pickerError,
                    selected = selection,
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
                        text = "No candidate links found for this source in the current scan window.",
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
    }
}

@Composable
private fun Header() {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Bluesky",
            style = if (isV1) mTypography.title else MaterialTheme.typography.titleLarge,
            color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Live candidate links. Saving creates normal Mimeo items.",
            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanDefaults(picker: BlueskyPickerResponse?) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    val caps = picker?.caps
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isV1) 0.dp else 1.dp,
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "Scan defaults: ${caps?.maxAgeHours ?: 24} h, ${caps?.maxPosts ?: 30} posts, ${caps?.maxLinks ?: 15} links",
            modifier = Modifier.padding(12.dp),
            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourcePicker(
    picker: BlueskyPickerResponse?,
    loading: Boolean,
    error: String?,
    selected: BlueskyCandidateSourceSelection?,
    onReload: () -> Unit,
    onScan: (BlueskyCandidateSourceSelection) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val mShapes = LocalMimeoShapeTokens.current
    var expanded by remember { mutableStateOf(false) }
    var handleDraft by remember { mutableStateOf("") }
    var listDraft by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
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
                Text(
                    "Source picker",
                    style = if (isV1) mTypography.row else MaterialTheme.typography.titleMedium,
                    color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                TextButton(onClick = onReload, enabled = !loading) { Text("Refresh") }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                        selectedContainerColor = mColors.accentDim,
                                        selectedLabelColor = mColors.accent,
                                    ) else FilterChipDefaults.filterChipColors(),
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
                                            inputError = null
                                            onScan(option.selection)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = handleDraft,
                        onValueChange = { handleDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Account by handle") },
                        placeholder = { Text("alice.bsky.social") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val handle = normalizeBlueskyHandleInput(handleDraft)
                            if (handle != null) {
                                inputError = null
                                onScan(BlueskyCandidateSourceSelection("account", "@$handle", actor = handle))
                            } else {
                                inputError = "Handle is required."
                            }
                        }),
                    )
                    Button(
                        onClick = {
                            val handle = normalizeBlueskyHandleInput(handleDraft)
                            if (handle != null) {
                                inputError = null
                                onScan(BlueskyCandidateSourceSelection("account", "@$handle", actor = handle))
                            } else {
                                inputError = "Handle is required."
                            }
                        },
                        enabled = !loading,
                    ) {
                        Text("Scan account")
                    }

                    OutlinedTextField(
                        value = listDraft,
                        onValueChange = { listDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("List URL") },
                        placeholder = { Text("https://bsky.app/profile/.../lists/...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val parsed = parseBlueskyListIdentifierInput(listDraft)
                            if (parsed.ok) {
                                inputError = null
                                onScan(BlueskyCandidateSourceSelection("list_feed", "Bluesky List", uri = parsed.uri))
                            } else {
                                inputError = parsed.error
                            }
                        }),
                    )
                    Button(
                        onClick = {
                            val parsed = parseBlueskyListIdentifierInput(listDraft)
                            if (parsed.ok) {
                                inputError = null
                                onScan(BlueskyCandidateSourceSelection("list_feed", "Bluesky List", uri = parsed.uri))
                            } else {
                                inputError = parsed.error
                            }
                        },
                        enabled = !loading,
                    ) {
                        Text("Scan list")
                    }
                    if (!inputError.isNullOrBlank()) {
                        Text(
                            inputError.orEmpty(),
                            color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                            style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                        )
                    }
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
            text = "Choose a source to scan for candidate article links.",
            color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
            style = if (isV1) mTypography.body else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isV1) mShapes.card else MaterialTheme.shapes.medium,
        color = if (isV1) mColors.surface else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isV1) 0.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val label = cleanSourceLabel(
                label = scan?.source?.displayLabel ?: selected?.displayLabel ?: "Selected source",
                sourceType = scan?.source?.sourceType ?: selected?.sourceKind,
            )
            Text(
                label,
                style = if (isV1) mTypography.row else MaterialTheme.typography.titleSmall,
                color = if (isV1) mColors.fg else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (scan != null) {
                Text(
                    text = "Scanned ${scan.scan.postsScanned} posts, ${scan.candidates.size} links. Stop: ${scan.scan.stoppedReason}.",
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                    color = if (isV1) mColors.fg2 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!error.isNullOrBlank()) {
                Text(
                    error,
                    color = if (isV1) mColors.danger else MaterialTheme.colorScheme.error,
                    style = if (isV1) mTypography.meta else MaterialTheme.typography.bodySmall,
                )
            }
            val pinnedSourceId = findPinnedSourceId(scan, selected, pins)
            val pinSupported = scan?.source?.sourceType == "author_feed" || scan?.source?.sourceType == "list_feed"
            if (scan != null && pinSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pinnedSourceId != null) {
                        OutlinedButton(onClick = { onUnpin(pinnedSourceId) }, enabled = !pinning) {
                            Text(if (pinning) "Updating..." else "Unpin source")
                        }
                    } else {
                        OutlinedButton(onClick = onPin, enabled = !pinning) {
                            Text(if (pinning) "Pinning..." else "Pin source")
                        }
                    }
                    Text(
                        text = "Pinning only stores the picker shortcut; it does not save or harvest links.",
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
                Column(modifier = Modifier.weight(1f)) {
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
                SavedBadge(candidate = candidate)
            }
            val postMeta = buildList {
                candidate.bluesky.authorDisplayName?.takeIf { it.isNotBlank() }?.let { add(it) }
                candidate.bluesky.authorHandle?.takeIf { it.isNotBlank() }?.let { add("@$it") }
                candidate.bluesky.indexedAt?.takeIf { it.isNotBlank() }?.let { add(formatCandidateTimestamp(it)) }
            }
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${cleanSourceLabel(candidate.sourceLabel, candidate.sourceType)} · ${formatSourceType(candidate.sourceType)}",
                style = if (isV1) mTypography.meta else MaterialTheme.typography.labelSmall,
                color = if (isV1) mColors.fg3 else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = if (isV1) mColors.line else MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { uriHandler.openUri(candidate.articleUrl) }) { Text("Open link") }
                if (!candidate.bluesky.postUrl.isNullOrBlank()) {
                    TextButton(onClick = { uriHandler.openUri(candidate.bluesky.postUrl) }) { Text("Open post") }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (candidate.saved && candidate.itemId != null) {
                    Button(onClick = { onOpenItem(candidate.itemId) }) { Text("Read") }
                } else {
                    Button(onClick = onSave, enabled = !saving) { Text(if (saving) "Saving..." else "Save") }
                }
            }
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
private fun SavedBadge(candidate: BlueskyCandidate) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current
    val (label, color) = when {
        candidate.savedState == "failed_saved" ->
            "Saved failed" to if (isV1) mColors.danger else MaterialTheme.colorScheme.error
        candidate.saved ->
            "Saved" to if (isV1) mColors.success else MaterialTheme.colorScheme.primary
        else ->
            "Unsaved" to if (isV1) mColors.fg4 else MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        color = color,
        style = if (isV1) mTypography.caption else MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 2.dp),
    )
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

private fun findPinnedSourceId(
    scan: BlueskyCandidateScanResponse?,
    selected: BlueskyCandidateSourceSelection?,
    pins: List<BlueskyPickerPinItem>,
): Int? {
    val scanSourceId = scan?.source?.sourceId
    if (scanSourceId != null && pins.any { it.sourceId == scanSourceId }) return scanSourceId
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
