package com.mimeo.android.ui.bluesky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun BlueskyBrowseScreen(
    vm: AppViewModel,
    onOpenItem: (Int) -> Unit,
) {
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

    LaunchedEffect(Unit) {
        vm.loadBlueskyCandidatePicker()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Bluesky", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Live candidate links. Saving creates normal Mimeo items.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScanDefaults(picker: BlueskyPickerResponse?) {
    val caps = picker?.caps
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "Scan defaults: ${caps?.maxAgeHours ?: 24} h, ${caps?.maxPosts ?: 30} posts, ${caps?.maxLinks ?: 15} links",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var handleDraft by remember { mutableStateOf("") }
    var listDraft by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val sourceOptions = picker.sourceDropdownOptions()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Source picker", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                TextButton(onClick = onReload, enabled = !loading) { Text("Refresh") }
            }
            if (!error.isNullOrBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            val connected = picker?.connection?.connected == true
            val connectedHandle = picker?.connection?.handle ?: "Bluesky"
            Text(
                text = if (connected) {
                    "Connected as @$connectedHandle"
                } else {
                    "No connected Bluesky account. Account scans can still use public author feeds; timeline and lists require a connection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    enabled = sourceOptions.isNotEmpty() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selected?.let { cleanSourceLabel(it.displayLabel, it.sourceKind) }
                            ?: "Choose Home Timeline, pinned source, or list",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    sourceOptions.forEach { option ->
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
                label = { Text("List URL or AT-URI") },
                placeholder = { Text("https://bsky.app/profile/.../lists/... or at://...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val parsed = parseBlueskyListIdentifierInput(listDraft)
                    if (parsed.ok) {
                        inputError = null
                        listDraft = parsed.uri.orEmpty()
                        onScan(BlueskyCandidateSourceSelection("list_feed", "Bluesky List", uri = parsed.uri))
                    } else {
                        inputError = parsed.error
                    }
                }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val parsed = parseBlueskyListIdentifierInput(listDraft)
                        if (parsed.ok) {
                            inputError = null
                            listDraft = parsed.uri.orEmpty()
                            onScan(BlueskyCandidateSourceSelection("list_feed", "Bluesky List", uri = parsed.uri))
                        } else {
                            inputError = parsed.error
                        }
                    },
                    enabled = !loading,
                ) {
                    Text("Scan list")
                }
                Text(
                    text = "Feed generators are not supported yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!inputError.isNullOrBlank()) {
                Text(inputError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
    if (scan == null && selected == null && error.isNullOrBlank() && !scanning) {
        Text(
            text = "Choose a source to scan for candidate article links.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val label = cleanSourceLabel(
                label = scan?.source?.displayLabel ?: selected?.displayLabel ?: "Selected source",
                sourceType = scan?.source?.sourceType ?: selected?.sourceKind,
            )
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (scan != null) {
                Text(
                    text = "Scanned ${scan.scan.postsScanned} posts, ${scan.candidates.size} links. Stop: ${scan.scan.stoppedReason}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!error.isNullOrBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.title?.takeIf { it.isNotBlank() } ?: "Untitled link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = candidate.domain?.takeIf { it.isNotBlank() } ?: "External link",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!candidate.bluesky.textSnippet.isNullOrBlank()) {
                Text(
                    text = candidate.bluesky.textSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${cleanSourceLabel(candidate.sourceLabel, candidate.sourceType)} · ${formatSourceType(candidate.sourceType)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
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
                Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SavedBadge(candidate: BlueskyCandidate) {
    val (label, color) = when {
        candidate.savedState == "failed_saved" -> "Saved failed" to MaterialTheme.colorScheme.error
        candidate.saved -> "Saved" to MaterialTheme.colorScheme.primary
        else -> "Unsaved" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 2.dp),
    )
}

private data class SourceDropdownOption(
    val label: String,
    val selection: BlueskyCandidateSourceSelection,
)

private fun BlueskyPickerResponse?.sourceDropdownOptions(): List<SourceDropdownOption> {
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
            val label = "Pinned: ${cleanSourceLabel(pin.resolvedLabel, pin.kind)}"
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
        lists.forEach { list ->
            add(
                SourceDropdownOption(
                    label = list.name,
                    selection = BlueskyCandidateSourceSelection(
                        sourceKind = "list_feed",
                        displayLabel = "Bluesky List: ${list.name}",
                        uri = list.uri,
                    ),
                ),
            )
        }
    }
}

private fun cleanSourceLabel(label: String, sourceType: String?): String {
    val cleaned = label.trim()
    if (sourceType == "list_feed") {
        val withoutPrefix = cleaned.removePrefix("Pinned:").trim()
        val hasRawAddress = withoutPrefix.contains("at://", ignoreCase = true) ||
            withoutPrefix.contains("://", ignoreCase = true)
        if (hasRawAddress) return "Bluesky List"
    }
    return cleaned.takeIf { it.isNotBlank() } ?: formatSourceType(sourceType)
}

private fun formatSourceType(sourceType: String?): String = when (sourceType) {
    "home_timeline" -> "Home Timeline"
    "list_feed" -> "List"
    "author_feed", "account" -> "Account"
    else -> "Bluesky"
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

private fun formatCandidateTimestamp(iso: String): String {
    return runCatching {
        val normalized = iso.replace("Z", "+00:00")
        val dt = java.time.OffsetDateTime.parse(normalized)
        val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
        val hours = java.time.Duration.between(dt, now).toHours()
        when {
            hours < 1 -> "just now"
            hours < 24 -> "${hours}h ago"
            hours < 24 * 7 -> "${hours / 24}d ago"
            else -> dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    }.getOrDefault(iso.take(10))
}
