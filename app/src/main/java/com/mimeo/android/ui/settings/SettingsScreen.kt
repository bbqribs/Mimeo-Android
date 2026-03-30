package com.mimeo.android.ui.settings

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.model.LocusContentMode
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.ui.queue.autoDownloadStatusLines
import com.mimeo.android.ui.theme.toFontFamily
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREVIEW_PARAGRAPH_1 = "Mimeo now remembers your reading layout so Locus feels like a calm, bookish surface instead of a raw text dump."
private const val PREVIEW_PARAGRAPH_2 = "Use this preview to check rhythm, paragraph spacing, and readability before returning to long-form listening sessions."
private const val TTS_PREVIEW_PHRASE = "The quick brown fox jumps over the lazy dog."

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenDiagnostics: () -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onClearPasswordChangeState: () -> Unit,
    onSignOut: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val settings by vm.settings.collectAsState()
    val settingsScrollOffset by vm.settingsScrollOffset.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val testingConnection by vm.testingConnection.collectAsState()
    val connectionTestSuccessByMode by vm.connectionTestSuccessByMode.collectAsState()
    val autoDownloadDiagnostics by vm.autoDownloadDiagnostics.collectAsState()
    val passwordChangeState by vm.passwordChangeState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val scrollState = rememberScrollState()
    var restoredScrollOffset by remember { mutableStateOf(false) }
    var connectionMode by remember(settings.connectionMode) { mutableStateOf(settings.connectionMode) }
    var localBaseUrl by remember(settings.localBaseUrl) { mutableStateOf(settings.localBaseUrl) }
    var lanBaseUrl by remember(settings.lanBaseUrl) { mutableStateOf(settings.lanBaseUrl) }
    var remoteBaseUrl by remember(settings.remoteBaseUrl) { mutableStateOf(settings.remoteBaseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }
    var autoAdvance by remember(settings.autoAdvanceOnCompletion) {
        mutableStateOf(settings.autoAdvanceOnCompletion)
    }
    var autoArchiveAtArticleEnd by remember(settings.autoArchiveAtArticleEnd) {
        mutableStateOf(settings.autoArchiveAtArticleEnd)
    }
    var speakTitleBeforeArticle by remember(settings.speakTitleBeforeArticle) {
        mutableStateOf(settings.speakTitleBeforeArticle)
    }
    var skipDuplicateOpeningAfterTitleIntro by remember(settings.skipDuplicateOpeningAfterTitleIntro) {
        mutableStateOf(settings.skipDuplicateOpeningAfterTitleIntro)
    }
    var playCompletionCueAtArticleEnd by remember(settings.playCompletionCueAtArticleEnd) {
        mutableStateOf(settings.playCompletionCueAtArticleEnd)
    }
    var keepScreenOnDuringSession by remember(settings.keepScreenOnDuringSession) {
        mutableStateOf(settings.keepScreenOnDuringSession)
    }
    var persistentPlayerEnabled by remember(settings.persistentPlayerEnabled) {
        mutableStateOf(settings.persistentPlayerEnabled)
    }
    var autoScrollWhileListening by remember(settings.autoScrollWhileListening) {
        mutableStateOf(settings.autoScrollWhileListening)
    }
    var locusTabReturnsToPlaybackPosition by remember(settings.locusTabReturnsToPlaybackPosition) {
        mutableStateOf(settings.locusTabReturnsToPlaybackPosition)
    }
    var locusContentMode by remember(settings.locusContentMode) {
        mutableStateOf(settings.locusContentMode)
    }
    var showLocusContentModeMenu by remember { mutableStateOf(false) }
    var continuousNowPlayingMarquee by remember(settings.continuousNowPlayingMarquee) {
        mutableStateOf(settings.continuousNowPlayingMarquee)
    }
    var forceSentenceHighlightFallback by remember(settings.forceSentenceHighlightFallback) {
        mutableStateOf(settings.forceSentenceHighlightFallback)
    }
    var showPlaybackDiagnostics by remember(settings.showPlaybackDiagnostics) {
        mutableStateOf(settings.showPlaybackDiagnostics)
    }
    var showAutoDownloadDiagnostics by remember(settings.showAutoDownloadDiagnostics) {
        mutableStateOf(settings.showAutoDownloadDiagnostics)
    }
    var showQueueCaptureMetadata by remember(settings.showQueueCaptureMetadata) {
        mutableStateOf(settings.showQueueCaptureMetadata)
    }
    var showPendingOutcomeSimulator by remember(settings.showPendingOutcomeSimulator) {
        mutableStateOf(settings.showPendingOutcomeSimulator)
    }
    var ttsVoiceName by remember(settings.ttsVoiceName) {
        mutableStateOf(settings.ttsVoiceName)
    }
    var ttsEngineReady by remember { mutableStateOf(false) }
    var ttsVoiceOptions by remember { mutableStateOf<List<TtsVoiceOption>>(emptyList()) }
    var ttsLocaleOptions by remember { mutableStateOf<List<TtsLocaleOption>>(emptyList()) }
    var selectedTtsLocaleTag by remember { mutableStateOf("") }
    var showTtsLocaleMenu by remember { mutableStateOf(false) }
    var showTtsVoiceMenu by remember { mutableStateOf(false) }
    var engineDefaultTtsVoiceName by remember { mutableStateOf("") }
    var engineDefaultTtsVoiceLabel by remember { mutableStateOf("System default") }
    var ttsFallbackMessage by remember { mutableStateOf<String?>(null) }
    var ttsPreviewing by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var keepShareResultNotifications by remember(settings.keepShareResultNotifications) {
        mutableStateOf(settings.keepShareResultNotifications)
    }
    var autoDownloadSavedArticles by remember(settings.autoDownloadSavedArticles) {
        mutableStateOf(settings.autoDownloadSavedArticles)
    }
    var autoCacheFavoritedItems by remember(settings.autoCacheFavoritedItems) {
        mutableStateOf(settings.autoCacheFavoritedItems)
    }
    var readingFontSizeSp by remember(settings.readingFontSizeSp) {
        mutableIntStateOf(settings.readingFontSizeSp)
    }
    var readingFontOption by remember(settings.readingFontOption) {
        mutableStateOf(settings.readingFontOption)
    }
    var readingLineHeightPercent by remember(settings.readingLineHeightPercent) {
        mutableIntStateOf(settings.readingLineHeightPercent)
    }
    var readingMaxWidthDp by remember(settings.readingMaxWidthDp) {
        mutableIntStateOf(settings.readingMaxWidthDp)
    }
    var readingParagraphSpacing by remember(settings.readingParagraphSpacing) {
        mutableStateOf(settings.readingParagraphSpacing)
    }
    var testRequested by remember { mutableStateOf(false) }
    var showDefaultSavePlaylistDialog by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    fun selectedModeBaseUrl(): String {
        return when (connectionMode) {
            ConnectionMode.LOCAL -> localBaseUrl
            ConnectionMode.LAN -> lanBaseUrl
            ConnectionMode.REMOTE -> remoteBaseUrl
        }
    }

    fun savedModeBaseUrl(): String {
        return when (connectionMode) {
            ConnectionMode.LOCAL -> settings.localBaseUrl
            ConnectionMode.LAN -> settings.lanBaseUrl
            ConnectionMode.REMOTE -> settings.remoteBaseUrl
        }
    }

    fun saveCurrent() {
        vm.saveSettings(
            baseUrl = selectedModeBaseUrl(),
            connectionMode = connectionMode,
            localBaseUrl = localBaseUrl,
            lanBaseUrl = lanBaseUrl,
            remoteBaseUrl = remoteBaseUrl,
            token = token,
            autoAdvanceOnCompletion = autoAdvance,
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
        )
    }

    fun applyConnectionSnapshot(snapshot: ConnectionTestSuccessSnapshot) {
        when (snapshot.mode) {
            ConnectionMode.LOCAL -> {
                connectionMode = ConnectionMode.LOCAL
                localBaseUrl = snapshot.baseUrl
            }
            ConnectionMode.LAN -> {
                connectionMode = ConnectionMode.LAN
                lanBaseUrl = snapshot.baseUrl
            }
            ConnectionMode.REMOTE -> {
                connectionMode = ConnectionMode.REMOTE
                remoteBaseUrl = snapshot.baseUrl
            }
        }
        saveCurrent()
        vm.showSnackbar("${snapshot.mode.displayName()} URL applied")
    }

    fun saveReading(
        fontSizeSp: Int = readingFontSizeSp,
        fontOption: ReaderFontOption = readingFontOption,
        lineHeightPercent: Int = readingLineHeightPercent,
        maxWidthDp: Int = readingMaxWidthDp,
        paragraphSpacing: ParagraphSpacingOption = readingParagraphSpacing,
    ) {
        vm.saveReadingPreferences(
            readingFontSizeSp = fontSizeSp,
            readingFontOption = fontOption,
            readingLineHeightPercent = lineHeightPercent,
            readingMaxWidthDp = maxWidthDp,
            readingParagraphSpacing = paragraphSpacing,
        )
    }

    fun refreshTtsVoices(engine: TextToSpeech) {
        val descriptors = engine.voices
            ?.map { voice ->
                voice.toDescriptor()
            }
            .orEmpty()
        val options = mapTtsVoiceOptions(descriptors)
        val localeOptions = mapTtsLocaleOptions(options)
        val defaultVoiceName = engine.defaultVoice?.name?.trim().orEmpty()
        val defaultVoiceLabel = options.firstOrNull { it.name == defaultVoiceName }?.let { option ->
            "System default: ${option.localeLabel} / ${option.voiceLabel}"
        } ?: "System default"
        ttsVoiceOptions = options
        ttsLocaleOptions = localeOptions
        engineDefaultTtsVoiceName = defaultVoiceName
        engineDefaultTtsVoiceLabel = defaultVoiceLabel
        val resolution = resolveConfiguredTtsVoiceSelection(ttsVoiceName, options)
        ttsFallbackMessage = resolution.message
        if (resolution.resolvedVoiceName != ttsVoiceName) {
            ttsVoiceName = resolution.resolvedVoiceName
            vm.saveTtsVoiceName(resolution.resolvedVoiceName)
        }
        val resolvedLocaleTag = options
            .firstOrNull { it.name == resolution.resolvedVoiceName }
            ?.localeTag
            .orEmpty()
        selectedTtsLocaleTag = if (resolvedLocaleTag.isNotBlank()) {
            resolvedLocaleTag
        } else {
            localeOptions.firstOrNull()?.tag.orEmpty()
        }
    }

    fun applyCurrentTtsVoice(engine: TextToSpeech) {
        val selectedName = ttsVoiceName.trim()
        if (selectedName.isBlank()) return
        engine.voices?.firstOrNull { it.name == selectedName }?.let { selected ->
            engine.voice = selected
        }
    }

    LaunchedEffect(testRequested, testingConnection, statusMessage) {
        if (!testRequested || testingConnection) return@LaunchedEffect
        val message = statusMessage.orEmpty()
        if (message.equals("Settings saved", ignoreCase = true)) return@LaunchedEffect
        testRequested = false
        when {
            message.startsWith("Connected") -> {
                vm.showSnackbar(message)
            }
            message.contains("Token required", ignoreCase = true) -> {
                vm.showSnackbar("Token required")
            }
            else -> {
                vm.showSnackbar(
                    message = message.ifBlank { "Can't reach server" },
                    actionLabel = "Diagnostics",
                    actionKey = "open_diagnostics",
                )
            }
        }
        vm.consumeStatusMessage(message)
    }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
    }

    LaunchedEffect(settingsScrollOffset) {
        if (restoredScrollOffset) return@LaunchedEffect
        scrollState.scrollTo(settingsScrollOffset)
        restoredScrollOffset = true
    }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { vm.setSettingsScrollOffset(it) }
    }

    LaunchedEffect(passwordChangeState) {
        if (passwordChangeState is PasswordChangeState.Success) {
            showPasswordChangeDialog = false
            currentPassword = ""
            newPassword = ""
            confirmNewPassword = ""
            onClearPasswordChangeState()
        }
    }

    DisposableEffect(Unit) {
        lateinit var createdEngine: TextToSpeech
        createdEngine = TextToSpeech(vm.getApplication<Application>().applicationContext) { status ->
            ttsEngineReady = status == TextToSpeech.SUCCESS
            if (ttsEngineReady) {
                refreshTtsVoices(createdEngine)
                applyCurrentTtsVoice(createdEngine)
            } else {
                ttsVoiceOptions = emptyList()
            }
        }
        ttsEngine = createdEngine
        onDispose {
            ttsPreviewing = false
            ttsEngine = null
            createdEngine.stop()
            createdEngine.shutdown()
        }
    }

    LaunchedEffect(ttsVoiceName, ttsEngineReady) {
        val engine = ttsEngine ?: return@LaunchedEffect
        if (!ttsEngineReady) return@LaunchedEffect
        applyCurrentTtsVoice(engine)
    }

    val defaultSavePlaylistName = playlists.firstOrNull { it.id == settings.defaultSavePlaylistId }?.name ?: "Smart Queue"

    val previewTextStyle = TextStyle(
        fontFamily = readingFontOption.toFontFamily(),
        fontSize = readingFontSizeSp.sp,
        lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    )

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSectionHeader(
            title = "Connection / Server",
            subtitle = "Choose Local, LAN, or Remote mode. Sign-in is primary; manual token entry is for advanced use.",
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Connection")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = connectionMode == mode,
                            onClick = { connectionMode = mode },
                            label = { Text(mode.displayName()) },
                        )
                    }
                }
                Text(
                    text = connectionMode.description(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = selectedModeBaseUrl(),
                    onValueChange = {
                        when (connectionMode) {
                            ConnectionMode.LOCAL -> localBaseUrl = it
                            ConnectionMode.LAN -> lanBaseUrl = it
                            ConnectionMode.REMOTE -> remoteBaseUrl = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${connectionMode.displayName()} Base URL") },
                    singleLine = true,
                )
                val endpointValidation = validateConnectionEndpoint(connectionMode, selectedModeBaseUrl())
                endpointValidation.blockingError?.let { message ->
                    Text(
                        text = message,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
                endpointValidation.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = connectionModeBaseUrlGuidance(connectionMode),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Device token (advanced)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text(
                    text = connectionModeTokenAuthHelp(connectionMode),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatAuthSessionStatusSummary(
                        savedToken = settings.apiToken,
                        editedToken = token,
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = authSessionConsequenceSummary(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val currentModeSnapshot = connectionTestSuccessByMode[connectionMode]
                val hasUnsavedModeUrlEdit =
                    normalizeConnectionBaseUrl(selectedModeBaseUrl()) != normalizeConnectionBaseUrl(savedModeBaseUrl())
                Text(
                    text = formatCurrentConnectionStatusSummary(
                        mode = connectionMode,
                        selectedBaseUrl = savedModeBaseUrl(),
                        snapshot = currentModeSnapshot,
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasUnsavedModeUrlEdit) {
                    Text(
                        text = "${connectionMode.displayName()}: unsaved URL edits in the field.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = endpointValidation.blockingError == null,
                        onClick = {
                            if (endpointValidation.blockingError != null) {
                                vm.showSnackbar(endpointValidation.blockingError)
                            } else {
                                saveCurrent()
                            }
                        },
                    ) { Text("Save") }
                    Button(
                        enabled = !testingConnection && endpointValidation.blockingError == null,
                        onClick = {
                            if (endpointValidation.blockingError != null) {
                                vm.showSnackbar(endpointValidation.blockingError)
                                return@Button
                            }
                            saveCurrent()
                            if (token.isBlank()) {
                                testRequested = false
                                vm.showSnackbar("Token required")
                            } else {
                                testRequested = true
                                vm.testConnection()
                            }
                        },
                    ) { Text(if (testingConnection) "Testing..." else "Test") }
                    Button(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                }
                val lastSuccessItems = ConnectionMode.entries.mapNotNull { mode ->
                    connectionTestSuccessByMode[mode]?.let { snapshot -> mode to snapshot }
                }
                if (lastSuccessItems.isNotEmpty()) {
                    Text(
                        text = "Last successful test",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                    lastSuccessItems.forEach { (_, snapshot) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = formatConnectionTestSuccessSummary(snapshot),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { applyConnectionSnapshot(snapshot) },
                                ) { Text("Use") }
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(snapshot.baseUrl))
                                        vm.showSnackbar("${snapshot.mode.displayName()} URL copied")
                                    },
                                ) { Text("Copy") }
                            }
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        showPasswordChangeDialog = true
                        onClearPasswordChangeState()
                    }) {
                        Text("Change password")
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showSignOutDialog = true }) {
                        Text(
                            text = "Sign out",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        SettingsSectionSeparator()

        SettingsSectionHeader(
            title = "Saving / Share-sheet",
            subtitle = "Control where shared links go and how share results are shown.",
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Saving")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Default playlist")
                    TextButton(onClick = { showDefaultSavePlaylistDialog = true }) {
                        Text("$defaultSavePlaylistName ▼")
                    }
                }
                Text(
                    text = "Shared links save to Smart Queue unless you choose a playlist here.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Keep share result notifications")
                        Text(
                            text = "Off: share results drop away after about 4 seconds. On: results stay in the notification tray.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = keepShareResultNotifications,
                        onCheckedChange = { keepShareResultNotifications = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Auto-download saved articles for offline reading")
                        Text(
                            text = "When on, successful share-saves also fetch and cache article text for offline use.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoDownloadSavedArticles,
                        onCheckedChange = {
                            autoDownloadSavedArticles = it
                            vm.saveAutoDownloadSavedArticles(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Default Locus view")
                        Text(
                            text = "Sets how Locus opens by default. You can still cycle views by tapping article text.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { showLocusContentModeMenu = true }) {
                            Text("${locusContentMode.displayName()} ▼")
                        }
                        DropdownMenu(
                            expanded = showLocusContentModeMenu,
                            onDismissRequest = { showLocusContentModeMenu = false },
                        ) {
                            LocusContentMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName()) },
                                    onClick = {
                                        showLocusContentModeMenu = false
                                        locusContentMode = mode
                                        vm.saveLocusContentMode(mode)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Auto-cache favourited items")
                        Text(
                            text = "When on, favourited items (including in Archive) are cached for offline reading. Bin items stay excluded.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCacheFavoritedItems,
                        onCheckedChange = {
                            autoCacheFavoritedItems = it
                            vm.saveAutoCacheFavoritedItems(it)
                        },
                    )
                }
            }
        }
        SettingsSectionSeparator()

        SettingsSectionHeader(
            title = "Playback",
            subtitle = "Session and listening behavior across tabs and reading mode.",
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Playback")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Auto-advance on completion")
                    Switch(
                        checked = autoAdvance,
                        onCheckedChange = {
                            autoAdvance = it
                            vm.saveAutoAdvanceOnCompletion(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Auto-archive at end of article")
                        Text(
                            text = "When playback reaches a real end-of-article, move that item to Archive.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoArchiveAtArticleEnd,
                        onCheckedChange = {
                            autoArchiveAtArticleEnd = it
                            vm.saveAutoArchiveAtArticleEnd(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Speak title before article")
                        Text(
                            text = "When enabled, playback speaks the title before body text (duplicate intros are skipped).",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = speakTitleBeforeArticle,
                        onCheckedChange = {
                            speakTitleBeforeArticle = it
                            vm.saveSpeakTitleBeforeArticle(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Skip duplicated opening after title")
                        Text(
                            text = "When title intro is on, skip matching opening words in body playback.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = skipDuplicateOpeningAfterTitleIntro,
                        onCheckedChange = {
                            skipDuplicateOpeningAfterTitleIntro = it
                            vm.saveSkipDuplicateOpeningAfterTitleIntro(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Keep screen on during active session")
                        Text(
                            text = "Keeps screen awake while speaking, or while manually reading in Reader mode on Locus.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = keepScreenOnDuringSession,
                        onCheckedChange = {
                            keepScreenOnDuringSession = it
                            vm.saveKeepScreenOnDuringSession(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Persistent player across tabs")
                    Switch(
                        checked = persistentPlayerEnabled,
                        onCheckedChange = {
                            persistentPlayerEnabled = it
                            vm.savePersistentPlayerEnabled(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Auto-scroll while listening")
                    Switch(
                        checked = autoScrollWhileListening,
                        onCheckedChange = { autoScrollWhileListening = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Locus return follows now-playing")
                        Text(
                            text = "While previewing another item, tapping Locus returns to the now-playing item. On: jump to live playback line. Off: keep the last reader position.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = locusTabReturnsToPlaybackPosition,
                        onCheckedChange = {
                            locusTabReturnsToPlaybackPosition = it
                            vm.saveLocusTabReturnsToPlaybackPosition(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Continuous now-playing marquee")
                    Switch(
                        checked = continuousNowPlayingMarquee,
                        onCheckedChange = {
                            continuousNowPlayingMarquee = it
                            vm.saveContinuousNowPlayingMarquee(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("TTS voice")
                        Text(
                            text = if (!ttsEngineReady) {
                                "TTS engine not ready."
                            } else {
                                val selected = ttsVoiceOptions.firstOrNull { it.name == ttsVoiceName.trim() }
                                if (selected == null) {
                                    "Selected: $engineDefaultTtsVoiceLabel"
                                } else {
                                    "Selected: ${selected.localeLabel} / ${selected.voiceLabel}"
                                }
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!ttsFallbackMessage.isNullOrBlank()) {
                            Text(
                                text = ttsFallbackMessage.orEmpty(),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("End-of-article completion cue")
                        Text(
                            text = "Play a short tone when an article finishes.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = playCompletionCueAtArticleEnd,
                        onCheckedChange = {
                            playCompletionCueAtArticleEnd = it
                            vm.savePlayCompletionCueAtArticleEnd(it)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (selectedTtsLocaleTag.isBlank()) {
                                "Language/accent: Any"
                            } else {
                                val selectedLocaleLabel = ttsLocaleOptions.firstOrNull { it.tag == selectedTtsLocaleTag }?.label
                                "Language/accent: ${selectedLocaleLabel ?: selectedTtsLocaleTag}"
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            enabled = ttsEngineReady,
                            onClick = { showTtsLocaleMenu = true },
                        ) {
                            val selectedLocaleLabel = ttsLocaleOptions.firstOrNull { it.tag == selectedTtsLocaleTag }?.label
                            Text("Language: ${selectedLocaleLabel ?: "Any"} ▼")
                        }
                        DropdownMenu(
                            expanded = showTtsLocaleMenu,
                            onDismissRequest = { showTtsLocaleMenu = false },
                        ) {
                            if (ttsLocaleOptions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No language options") },
                                    onClick = { showTtsLocaleMenu = false },
                                )
                            }
                            ttsLocaleOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option.tag == selectedTtsLocaleTag) {
                                                "✓ ${option.label}"
                                            } else {
                                                option.label
                                            },
                                        )
                                    },
                                    onClick = {
                                        showTtsLocaleMenu = false
                                        selectedTtsLocaleTag = option.tag
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        val filteredVoiceOptions = ttsVoiceOptions.filter { option ->
                            selectedTtsLocaleTag.isBlank() || option.localeTag == selectedTtsLocaleTag
                        }
                        val selected = ttsVoiceOptions.firstOrNull { it.name == ttsVoiceName.trim() }
                        Text(
                            text = if (selected == null) {
                                "Voice: $engineDefaultTtsVoiceLabel"
                            } else {
                                "Voice: ${selected.voiceLabel}"
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            enabled = ttsEngineReady,
                            onClick = { showTtsVoiceMenu = true },
                        ) {
                            val selectedVoiceLabel = selected?.voiceLabel ?: "System default"
                            Text("Voice: $selectedVoiceLabel ▼")
                        }
                        TextButton(
                            enabled = ttsEngineReady && ttsVoiceName.trim().isNotBlank(),
                            onClick = {
                                ttsVoiceName = ""
                                selectedTtsLocaleTag = ttsVoiceOptions
                                    .firstOrNull { it.name == engineDefaultTtsVoiceName }
                                    ?.localeTag
                                    .orEmpty()
                                vm.saveTtsVoiceName("")
                            },
                        ) {
                            Text("Use system default")
                        }
                        DropdownMenu(
                            expanded = showTtsVoiceMenu,
                            onDismissRequest = { showTtsVoiceMenu = false },
                        ) {
                            filteredVoiceOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option.name.equals(ttsVoiceName.trim(), ignoreCase = true)) {
                                                "✓ ${option.voiceLabel}"
                                            } else {
                                                option.voiceLabel
                                            },
                                        )
                                    },
                                    onClick = {
                                        showTtsVoiceMenu = false
                                        ttsVoiceName = option.name
                                        selectedTtsLocaleTag = option.localeTag
                                        vm.saveTtsVoiceName(option.name)
                                    },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (ttsEngineReady && ttsVoiceOptions.size <= 1) {
                            "No alternate voices available on this TTS engine."
                        } else {
                            "Preview phrase: \"$TTS_PREVIEW_PHRASE\". Online voices fall back to system default while offline."
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        enabled = ttsEngineReady,
                        onClick = {
                            val engine = ttsEngine
                            if (engine == null || !ttsEngineReady) {
                                vm.showSnackbar("TTS preview unavailable")
                                return@Button
                            }
                            applyCurrentTtsVoice(engine)
                            ttsPreviewing = true
                            engine.speak(
                                TTS_PREVIEW_PHRASE,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "mimeo-tts-preview-${System.currentTimeMillis()}",
                            )
                            ttsPreviewing = false
                        },
                    ) { Text(if (ttsPreviewing) "Playing..." else "Preview") }
                }
                Text("Emulator default: http://10.0.2.2:8000")
            }
        }
        SettingsSectionSeparator()

        SettingsSectionHeader(
            title = "Reader / Appearance",
            subtitle = "Tune typography and layout for reading comfort.",
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Reading")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Font")
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { showFontMenu = true }) {
                            Text("${readingFontOption.displayName()} ▼")
                        }
                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false },
                        ) {
                            ReaderFontOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName()) },
                                    onClick = {
                                        showFontMenu = false
                                        readingFontOption = option
                                        saveReading(fontOption = option)
                                    },
                                )
                            }
                        }
                    }
                }

                Text("Font size: ${readingFontSizeSp}sp")
                Slider(
                    value = readingFontSizeSp.toFloat(),
                    onValueChange = {
                        readingFontSizeSp = it.toInt()
                        saveReading(fontSizeSp = readingFontSizeSp)
                    },
                    valueRange = 6f..40f,
                    steps = 33,
                )

                Text("Line height: ${readingLineHeightPercent}%")
                Slider(
                    value = readingLineHeightPercent.toFloat(),
                    onValueChange = {
                        readingLineHeightPercent = it.toInt()
                        saveReading(lineHeightPercent = readingLineHeightPercent)
                    },
                    valueRange = 120f..180f,
                    steps = 5,
                )

                Text("Margin width: ${readingMaxWidthDp}dp")
                Slider(
                    value = readingMaxWidthDp.toFloat(),
                    onValueChange = {
                        readingMaxWidthDp = it.toInt()
                        saveReading(maxWidthDp = readingMaxWidthDp)
                    },
                    valueRange = 320f..1000f,
                    steps = 16,
                )

                Text("Paragraph spacing")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParagraphSpacingOption.entries.forEach { option ->
                        FilterChip(
                            selected = readingParagraphSpacing == option,
                            onClick = {
                                readingParagraphSpacing = option
                                saveReading(paragraphSpacing = option)
                            },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            when (readingParagraphSpacing) {
                                ParagraphSpacingOption.SMALL -> 8.dp
                                ParagraphSpacingOption.MEDIUM -> 14.dp
                                ParagraphSpacingOption.LARGE -> 20.dp
                            },
                        ),
                    ) {
                        Text("Preview", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        Text(
                            text = PREVIEW_PARAGRAPH_1,
                            style = previewTextStyle,
                        )
                        Text(
                            text = PREVIEW_PARAGRAPH_2,
                            style = previewTextStyle,
                        )
                    }
                }
            }
        }
        SettingsSectionSeparator()

        if (BuildConfig.DEBUG) {
            SettingsSectionHeader(
                title = "Developer",
                subtitle = "Debug-only diagnostics and playback instrumentation controls.",
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Developer")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Playback diagnostics strip")
                            Text(
                                text = "Show open/playback handoff diagnostics at the bottom of Locus.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showPlaybackDiagnostics,
                            onCheckedChange = {
                                showPlaybackDiagnostics = it
                                vm.saveShowPlaybackDiagnostics(it)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Pending outcome simulator")
                            Text(
                                text = "Show Up Next debug controls to simulate pending resolution outcomes.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showPendingOutcomeSimulator,
                            onCheckedChange = {
                                showPendingOutcomeSimulator = it
                                vm.saveShowPendingOutcomeSimulator(it)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Autodownload diagnostics panel")
                            Text(
                                text = "Show autodownload status block in Up Next and details here.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showAutoDownloadDiagnostics,
                            onCheckedChange = {
                                showAutoDownloadDiagnostics = it
                                vm.saveShowAutoDownloadDiagnostics(it)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Queue capture metadata")
                            Text(
                                text = "Show strategy/capture metadata on queue rows.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = showQueueCaptureMetadata,
                            onCheckedChange = {
                                showQueueCaptureMetadata = it
                                vm.saveShowQueueCaptureMetadata(it)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Sentence highlight fallback")
                            Text(
                                text = "Disable range-level highlighting and verify sentence-level fallback.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = forceSentenceHighlightFallback,
                            onCheckedChange = {
                                forceSentenceHighlightFallback = it
                                vm.saveForceSentenceHighlightFallback(it)
                            },
                        )
                    }
                    if (showAutoDownloadDiagnostics) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Autodownload diagnostics",
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            )
                            autoDownloadStatusLines(autoDownloadDiagnostics).forEach { line ->
                                Text(
                                    text = line,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            SettingsSectionSeparator()
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text(signOutConfirmationMessage()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = "Sign out",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showPasswordChangeDialog) {
        val passwordChangeError = (passwordChangeState as? PasswordChangeState.Error)?.message
        val isSubmittingPasswordChange = passwordChangeState is PasswordChangeState.Submitting
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingPasswordChange) {
                    showPasswordChangeDialog = false
                    onClearPasswordChangeState()
                }
            },
            title = { Text("Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Current password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isSubmittingPasswordChange,
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isSubmittingPasswordChange,
                    )
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = {
                            confirmNewPassword = it
                            if (passwordChangeState is PasswordChangeState.Error) onClearPasswordChangeState()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm new password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !isSubmittingPasswordChange,
                    )
                    Text(
                        text = "Use at least $PASSWORD_CHANGE_MIN_LENGTH characters.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!passwordChangeError.isNullOrBlank()) {
                        Text(
                            text = passwordChangeError,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmittingPasswordChange,
                    onClick = {
                        onChangePassword(currentPassword, newPassword, confirmNewPassword)
                    },
                ) {
                    Text(if (isSubmittingPasswordChange) "Changing..." else "Change password")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingPasswordChange,
                    onClick = {
                        showPasswordChangeDialog = false
                        onClearPasswordChangeState()
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDefaultSavePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultSavePlaylistDialog = false },
            title = { Text("Default playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            vm.saveDefaultSavePlaylistId(null)
                            showDefaultSavePlaylistDialog = false
                        },
                    ) {
                        Text("Smart Queue")
                    }
                    playlists.forEach { playlist ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                vm.saveDefaultSavePlaylistId(playlist.id)
                                showDefaultSavePlaylistDialog = false
                            },
                        ) {
                            Text(playlist.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultSavePlaylistDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ReaderFontOption.displayName(): String = when (this) {
    ReaderFontOption.LITERATA -> "Literata"
    ReaderFontOption.SERIF -> "Serif"
    ReaderFontOption.SANS_SERIF -> "Sans Serif"
    ReaderFontOption.MONOSPACE -> "Monospace"
}

private fun LocusContentMode.displayName(): String = when (this) {
    LocusContentMode.FULL_TEXT -> "Full text"
    LocusContentMode.FULL_TEXT_WITH_PLAYER -> "Full text + player"
    LocusContentMode.PLAYBACK_FOCUSED -> "Playback focused"
}

private fun ConnectionMode.displayName(): String = when (this) {
    ConnectionMode.LOCAL -> "Local"
    ConnectionMode.LAN -> "LAN"
    ConnectionMode.REMOTE -> "Remote"
}

private fun ConnectionMode.description(): String = when (this) {
    ConnectionMode.LOCAL -> "Local loopback/dev-host mode (for emulator-style local access)."
    ConnectionMode.LAN -> "Home LAN mode (when phone and server are on same local network)."
    ConnectionMode.REMOTE -> "Off-LAN remote mode (for secure access over Tailscale/VPN)."
}

internal fun connectionModeBaseUrlGuidance(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.LOCAL ->
        "Use local/emulator host URL (typically http://10.0.2.2:8000). Base URL should be http(s)://host[:port] with no path."
    ConnectionMode.LAN ->
        "Use your server LAN URL (for example http://192.168.x.y:8000) when phone and server share the same network."
    ConnectionMode.REMOTE ->
        "Use your Tailscale/VPN URL (for example http://100.x.y.z:8000 or your secure remote host). HTTP over trusted Tailscale/VPN is supported. If using 192.168.x.y, use LAN mode instead."
}

private fun connectionModeTokenAuthHelp(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.REMOTE ->
        "Remote device tokens can expire. If token is rejected, create a new device token and update this field."
    else ->
        "Use a valid API token for this server target."
}

internal fun connectionModeTokenGuidance(mode: ConnectionMode): String = connectionModeTokenAuthHelp(mode)

internal fun formatConnectionTestSuccessSummary(snapshot: ConnectionTestSuccessSnapshot): String {
    val timestamp = formatConnectionSnapshotTimestamp(snapshot.succeededAtMs)
    val host = runCatching {
        URI(snapshot.baseUrl.trim()).host
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.baseUrl.trim()
    }.getOrDefault(snapshot.baseUrl.trim())
    val shaSuffix = snapshot.gitSha?.takeIf { it.isNotBlank() }?.let { " git_sha=$it" }.orEmpty()
    return "${snapshot.mode.displayName()}: $host $timestamp$shaSuffix"
}

internal fun formatCurrentConnectionStatusSummary(
    mode: ConnectionMode,
    selectedBaseUrl: String,
    snapshot: ConnectionTestSuccessSnapshot?,
): String {
    if (snapshot == null) {
        return "${mode.displayName()}: no successful test saved yet."
    }
    val snapshotTime = formatConnectionSnapshotTimestamp(snapshot.succeededAtMs)
    val sameTarget = normalizeConnectionBaseUrl(selectedBaseUrl) == normalizeConnectionBaseUrl(snapshot.baseUrl)
    return if (sameTarget) {
        val shaSuffix = snapshot.gitSha?.takeIf { it.isNotBlank() }?.let { " (git_sha=$it)" }.orEmpty()
        "${mode.displayName()}: matches last successful target at $snapshotTime$shaSuffix"
    } else {
        "${mode.displayName()}: differs from last successful target (${snapshot.baseUrl}) at $snapshotTime"
    }
}

internal fun normalizeConnectionBaseUrl(url: String): String {
    return url.trim().trimEnd('/').lowercase(Locale.US)
}

private fun formatConnectionSnapshotTimestamp(succeededAtMs: Long): String {
    return if (succeededAtMs > 0L) {
        runCatching {
            SimpleDateFormat("yy-MM-dd HH:mm", Locale.US).format(Date(succeededAtMs))
        }.getOrDefault("unknown time")
    } else {
        "unknown time"
    }
}

@Composable
private fun SettingsSectionSeparator() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        thickness = 1.dp,
    )
}

internal fun signOutConfirmationMessage(): String =
    "Your sign-in session will end. Server URL and other settings are kept."

internal fun formatAuthSessionStatusSummary(savedToken: String, editedToken: String): String {
    val savedPresent = savedToken.trim().isNotBlank()
    val editedNormalized = editedToken.trim()
    val savedNormalized = savedToken.trim()
    return when {
        !savedPresent ->
            "Session: not signed in. Use Sign In to create a user-linked device session token."
        editedNormalized != savedNormalized ->
            "Session: signed in. Advanced token field has unsaved edits; Save applies token replacement."
        else ->
            "Session: signed in with saved device session token."
    }
}

internal fun authSessionConsequenceSummary(): String =
    "If auth is stale/invalid, the app returns to Sign In. " +
        "Sign out clears only this device session token (URL/mode stay). " +
        "Change password keeps this device signed in and revokes other sessions."

internal data class TtsVoiceDescriptor(
    val name: String,
    val localeTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
)

internal data class TtsVoiceOption(
    val name: String,
    val localeTag: String,
    val localeLabel: String,
    val voiceLabel: String,
)

internal data class TtsLocaleOption(
    val tag: String,
    val label: String,
)

internal data class TtsVoiceSelectionResolution(
    val resolvedVoiceName: String,
    val message: String? = null,
)

private fun Voice.toDescriptor(): TtsVoiceDescriptor {
    return TtsVoiceDescriptor(
        name = name.orEmpty(),
        localeTag = locale?.toLanguageTag().orEmpty(),
        quality = quality,
        latency = latency,
        requiresNetwork = isNetworkConnectionRequired,
    )
}

internal fun mapTtsVoiceOptions(descriptors: List<TtsVoiceDescriptor>): List<TtsVoiceOption> {
    val preferred = descriptors
        .asSequence()
        .filter { it.name.isNotBlank() }
        .sortedWith(
            compareBy<TtsVoiceDescriptor> { it.requiresNetwork }
                .thenByDescending { it.quality }
                .thenBy { it.latency }
                .thenBy { it.localeTag.lowercase(Locale.US) }
                .thenBy { it.name.lowercase(Locale.US) },
        )
        .map { descriptor ->
            val localeLabel = humanReadableLocaleLabel(descriptor.localeTag)
            val qualityLabel = when (descriptor.quality) {
                Voice.QUALITY_VERY_HIGH -> "very high"
                Voice.QUALITY_HIGH -> "high"
                Voice.QUALITY_NORMAL -> "normal"
                Voice.QUALITY_LOW -> "low"
                Voice.QUALITY_VERY_LOW -> "very low"
                else -> null
            }
            val networkLabel = if (descriptor.requiresNetwork) "online" else "offline"
            val qualitySuffix = qualityLabel?.let { " - $it" }.orEmpty()
            val compactVoiceName = compactVoiceNameLabel(descriptor.name.trim(), descriptor.localeTag)
            TtsVoiceOption(
                name = descriptor.name.trim(),
                localeTag = descriptor.localeTag,
                localeLabel = localeLabel,
                voiceLabel = "$compactVoiceName - $networkLabel$qualitySuffix",
            )
        }
        .distinctBy { it.name.lowercase(Locale.US) }
        .toList()
    return preferred
}

internal fun mapTtsLocaleOptions(options: List<TtsVoiceOption>): List<TtsLocaleOption> {
    return options
        .asSequence()
        .map { option -> option.localeTag to option.localeLabel }
        .distinctBy { (tag, _) -> tag.lowercase(Locale.US) }
        .sortedWith(
            compareBy<Pair<String, String>> { (tag, _) ->
                !tag.equals("en-GB", ignoreCase = true)
            }.thenBy { (_, label) -> label.lowercase(Locale.US) },
        )
        .map { (tag, label) -> TtsLocaleOption(tag = tag, label = label) }
        .toList()
}

private fun humanReadableLocaleLabel(localeTag: String): String {
    val normalized = localeTag.trim()
    if (normalized.isBlank()) return "Unknown locale"
    val locale = Locale.forLanguageTag(normalized)
    val language = locale.getDisplayLanguage(Locale.US).ifBlank { normalized }
    val country = locale.getDisplayCountry(Locale.US)
    return if (country.isBlank()) language else "$language ($country)"
}

private fun compactVoiceNameLabel(rawVoiceName: String, localeTag: String): String {
    val raw = rawVoiceName.trim()
    if (raw.isBlank()) return "Unnamed voice"
    val locale = Locale.forLanguageTag(localeTag)
    val language = locale.language.trim().lowercase(Locale.US)
    val country = locale.country.trim().lowercase(Locale.US)
    var compact = raw
    if (language.isNotBlank() && country.isNotBlank()) {
        val localePrefixPattern = Regex("^${Regex.escape(language)}[-_]${Regex.escape(country)}[-_]", RegexOption.IGNORE_CASE)
        compact = compact.replace(localePrefixPattern, "")
    }
    if (compact == raw && language.isNotBlank()) {
        val languagePrefixPattern = Regex("^${Regex.escape(language)}[-_]", RegexOption.IGNORE_CASE)
        compact = compact.replace(languagePrefixPattern, "")
    }
    compact = compact.trim().replace('_', ' ')
    return compact.ifBlank { raw }
}

internal fun resolveConfiguredTtsVoiceName(
    configuredVoiceName: String,
    availableOptions: List<TtsVoiceOption>,
): String {
    return resolveConfiguredTtsVoiceSelection(configuredVoiceName, availableOptions).resolvedVoiceName
}

internal fun resolveConfiguredTtsVoiceSelection(
    configuredVoiceName: String,
    availableOptions: List<TtsVoiceOption>,
): TtsVoiceSelectionResolution {
    val normalizedConfigured = configuredVoiceName.trim()
    if (normalizedConfigured.isBlank()) {
        return TtsVoiceSelectionResolution(resolvedVoiceName = "")
    }
    val found = availableOptions.firstOrNull { option ->
        option.name.equals(normalizedConfigured, ignoreCase = true)
    }
    if (found != null) {
        return TtsVoiceSelectionResolution(resolvedVoiceName = normalizedConfigured)
    }
    return TtsVoiceSelectionResolution(
        resolvedVoiceName = "",
        message = "Saved voice unavailable. Using engine default voice.",
    )
}
