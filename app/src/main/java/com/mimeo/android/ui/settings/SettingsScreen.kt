package com.mimeo.android.ui.settings

import android.app.Application
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.Info
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.model.ConnectionTestSuccessSnapshot
import com.mimeo.android.model.DEFAULT_REMOTE_BASE_URL
import com.mimeo.android.model.DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL
import com.mimeo.android.model.DrawerPanelSide
import com.mimeo.android.model.LocusContentMode
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.PendingManualSaveItem
import com.mimeo.android.model.PlayerChevronSnapEdge
import com.mimeo.android.model.ReaderFontOption
import com.mimeo.android.model.BlueskySourceDiagnostic
import com.mimeo.android.model.SmartPlaylistSummary
import com.mimeo.android.ui.common.passiveVerticalScrollIndicator
import com.mimeo.android.ui.queue.autoDownloadStatusLines
import com.mimeo.android.ui.theme.toFontFamily
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val PREVIEW_PARAGRAPH_1 = "Mimeo now remembers your reading layout so Locus feels like a calm, bookish surface instead of a raw text dump."
private const val PREVIEW_PARAGRAPH_2 = "Use this preview to check rhythm, paragraph spacing, and readability before returning to long-form listening sessions."
private const val TTS_PREVIEW_PHRASE = "The quick brown fox jumps over the lazy dog."

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenDiagnostics: () -> Unit,
    onCreateBlueskySmartPlaylist: () -> Unit,
    onOpenSmartPlaylist: (Int) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onClearPasswordChangeState: () -> Unit,
    onSignOut: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val pendingManualSaves by vm.pendingManualSaves.collectAsState()
    val settingsScrollOffset by vm.settingsScrollOffset.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val testingConnection by vm.testingConnection.collectAsState()
    val connectionTestSuccessByMode by vm.connectionTestSuccessByMode.collectAsState()
    val blueskyStatusLoading by vm.blueskyStatusLoading.collectAsState()
    val blueskyStatusError by vm.blueskyStatusError.collectAsState()
    val blueskyAccountConnection by vm.blueskyAccountConnection.collectAsState()
    val blueskyOperatorStatus by vm.blueskyOperatorStatus.collectAsState()
    val blueskyConnecting by vm.blueskyConnecting.collectAsState()
    val blueskyConnectError by vm.blueskyConnectError.collectAsState()
    val blueskyConnectIsReadOnlyScope by vm.blueskyConnectIsReadOnlyScope.collectAsState()
    val blueskyDisconnecting by vm.blueskyDisconnecting.collectAsState()
    val autoDownloadDiagnostics by vm.autoDownloadDiagnostics.collectAsState()
    val passwordChangeState by vm.passwordChangeState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val smartPlaylists by vm.smartPlaylists.collectAsState()
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
    var drawerPanelSide by remember(settings.drawerPanelSide) {
        mutableStateOf(settings.drawerPanelSide)
    }
    var chevronOnRightSide by remember(settings.playerChevronSnapEdge) {
        mutableStateOf(settings.playerChevronSnapEdge != PlayerChevronSnapEdge.LEFT)
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
    var lastConnectionTestResult by remember { mutableStateOf<String?>(null) }
    var lastConnectionTestedAtMs by remember { mutableStateOf<Long?>(null) }
    val blueskySmartPlaylist = remember(smartPlaylists) { smartPlaylists.firstOrNull { it.isBlueskyHarvestPlaylist() } }
    var blueskyHandle by remember { mutableStateOf("") }
    var blueskyAppPassword by remember { mutableStateOf("") }

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
        lastConnectionTestResult = message.ifBlank { "Can't reach server" }
        lastConnectionTestedAtMs = System.currentTimeMillis()
        vm.consumeStatusMessage(message)
    }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
        vm.refreshBlueskyStatus()
    }

    LaunchedEffect(blueskyAccountConnection?.connected) {
        if (blueskyAccountConnection?.connected == true) {
            blueskyHandle = ""
            blueskyAppPassword = ""
        }
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
            .passiveVerticalScrollIndicator(
                scrollState = scrollState,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
            )
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSectionHeader(
            title = "Connection / Server",
            subtitle = "Choose Local, LAN, or Remote mode. Sign In is recommended; manual token entry replaces this device token.",
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
                    text = deviceTokenScopeHint(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
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
                        selectedBaseUrl = selectedModeBaseUrl(),
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
                                lastConnectionTestResult = endpointValidation.blockingError
                                lastConnectionTestedAtMs = System.currentTimeMillis()
                                return@Button
                            }
                            saveCurrent()
                            if (token.isBlank()) {
                                testRequested = false
                                lastConnectionTestResult = ConnectionTestMessageResolver.tokenRequired(
                                    mode = connectionMode,
                                    baseUrl = selectedModeBaseUrl(),
                                )
                                lastConnectionTestedAtMs = System.currentTimeMillis()
                            } else {
                                testRequested = true
                                vm.testConnection()
                            }
                        },
                    ) { Text(if (testingConnection) "Testing..." else "Test") }
                    Button(onClick = onOpenDiagnostics) { Text("Diagnostics") }
                }
                val testResultTimestamp = lastConnectionTestedAtMs?.let { millis ->
                    runCatching {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
                    }.getOrDefault(null)
                }
                val pendingSummary = formatPendingSaveTestSummary(
                    pendingItems = pendingManualSaves,
                    selectedPlaylistId = settings.selectedPlaylistId,
                )
                Text(
                    text = "Test results",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                val fullTestResultText = buildConnectionTestResultText(
                    baseResult = lastConnectionTestResult,
                    pendingSummary = pendingSummary,
                    timestamp = testResultTimestamp,
                )
                Text(
                    text = fullTestResultText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsActionIconButton(
                        enabled = fullTestResultText.isNotBlank(),
                        icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy test result") },
                        tooltip = "Copy test result",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(fullTestResultText))
                            vm.showSnackbar("Test result copied")
                        },
                    )
                    SettingsActionIconButton(
                        enabled = fullTestResultText.isNotBlank(),
                        icon = { Icon(Icons.Outlined.Share, contentDescription = "Share test result") },
                        tooltip = "Share test result",
                        onClick = { sharePlainText(context, "Mimeo connection test", fullTestResultText) },
                    )
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
                                SettingsActionIconButton(
                                    icon = { Icon(Icons.Outlined.Check, contentDescription = "Use this URL") },
                                    tooltip = "Use this URL",
                                    onClick = { applyConnectionSnapshot(snapshot) },
                                )
                                SettingsActionIconButton(
                                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL") },
                                    tooltip = "Copy URL",
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(snapshot.baseUrl))
                                        vm.showSnackbar("${snapshot.mode.displayName()} URL copied")
                                    },
                                )
                                SettingsActionIconButton(
                                    icon = { Icon(Icons.Outlined.Share, contentDescription = "Share URL") },
                                    tooltip = "Share URL",
                                    onClick = {
                                        val shareText = "${snapshot.mode.displayName()} URL: ${snapshot.baseUrl}"
                                        sharePlainText(context, "Mimeo connection URL", shareText)
                                    },
                                )
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
            title = "Bluesky",
            subtitle = "Connect a Bluesky account for authenticated harvesting, or view scheduler status.",
        )

        var blueskyExplainerExpanded by remember { mutableStateOf(false) }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "About Bluesky app passwords",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { blueskyExplainerExpanded = !blueskyExplainerExpanded }) {
                        Text(if (blueskyExplainerExpanded) "Hide" else "Show")
                    }
                }
                AnimatedVisibility(visible = blueskyExplainerExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Mimeo uses a Bluesky app password for authenticated read access — not your main Bluesky password.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "App passwords grant limited access and can be created or revoked at any time in your Bluesky account settings (Settings → App Passwords).",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Bluesky status")
                if (blueskyStatusLoading) {
                    Text(
                        text = "Loading Bluesky status...",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!blueskyStatusError.isNullOrBlank()) {
                    Text(
                        text = blueskyStatusError.orEmpty(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
                val account = blueskyAccountConnection
                val scheduler = blueskyOperatorStatus
                if (account != null) {
                    if (account.connected == true) {
                        SettingsKeyValueLine("Handle", account.handle ?: "Unknown")
                        if (!account.did.isNullOrBlank()) {
                            SettingsKeyValueLine("DID", account.did.orEmpty())
                        }
                        SettingsKeyValueLine("Mode", formatBlueskyModeLabel(account.mode))
                        SettingsKeyValueLine("Last validation", account.resolvedValidationState ?: "Unavailable")
                        if (!account.message.isNullOrBlank()) {
                            Text(
                                text = account.message.orEmpty(),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Connected for authenticated Bluesky harvesting. Home timeline and list-feed sources are managed from the Mimeo web or operator panel — not from this app.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Harvested posts are not added to Up Next automatically. Use smart playlists or tap “Use as Up Next” on a playlist to listen.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (account.disconnectAvailable == true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Button(
                                    enabled = !blueskyDisconnecting && !blueskyStatusLoading,
                                    onClick = { vm.disconnectBluesky() },
                                ) {
                                    Text(if (blueskyDisconnecting) "Disconnecting..." else "Disconnect Bluesky account")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No Bluesky account is connected.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Mimeo is currently using public author-feed harvesting. Connect an account for authenticated home-timeline and list-feed access.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 1.dp,
                        )
                        Text(
                            text = "Connect Bluesky account",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                        OutlinedTextField(
                            value = blueskyHandle,
                            onValueChange = { blueskyHandle = it },
                            label = { Text("Bluesky handle") },
                            placeholder = { Text("alice.bsky.social") },
                            singleLine = true,
                            enabled = !blueskyConnecting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = blueskyAppPassword,
                            onValueChange = { blueskyAppPassword = it },
                            label = { Text("Bluesky app password") },
                            placeholder = { Text("App password — not your main Bluesky password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !blueskyConnecting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!blueskyConnectError.isNullOrBlank()) {
                            Text(
                                text = blueskyConnectError!!,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                            if (blueskyConnectIsReadOnlyScope) {
                                TextButton(onClick = onSignOut) {
                                    Text("Sign out")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                enabled = blueskyHandle.isNotBlank() && blueskyAppPassword.isNotBlank() && !blueskyConnecting,
                                onClick = { vm.connectBluesky(blueskyHandle.trim(), blueskyAppPassword) },
                            ) {
                                Text(if (blueskyConnecting) "Connecting..." else "Connect")
                            }
                        }
                    }
                }
                if (scheduler != null) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                    SettingsKeyValueLine("Scheduler enabled", formatBlueskyBool(scheduler.resolvedSchedulerEnabled))
                    SettingsKeyValueLine("State", scheduler.state ?: "Unknown")
                    SettingsKeyValueLine("Enabled source count", scheduler.enabledSourceCount?.toString() ?: "0")
                    SettingsKeyValueLine("Due source count", scheduler.dueSourceCount?.toString() ?: "0")
                    SettingsKeyValueLine("Next due", scheduler.resolvedNextDue ?: "Not scheduled")
                    SettingsKeyValueLine("Last run status", scheduler.lastRunStatus ?: "Unknown")
                    SettingsKeyValueLine("Last error", scheduler.resolvedLastErrorMessage?.takeIf { it.isNotBlank() } ?: "None")
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                    Text(
                        text = "Bluesky sources",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                    val visibleSources = scheduler.sources.filterNot { it.hidden == true || it.archived == true }
                    if (visibleSources.isEmpty()) {
                        Text(
                            text = "No visible Bluesky sources.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visibleSources.forEach { source ->
                            BlueskySourceRow(source = source)
                        }
                    }
                }
                if (!blueskyStatusLoading && blueskyStatusError.isNullOrBlank() && account == null && scheduler == null) {
                    Text(
                        text = "No Bluesky status data available.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (blueskySmartPlaylist != null) {
                        TextButton(
                            enabled = !blueskyStatusLoading,
                            onClick = { onOpenSmartPlaylist(blueskySmartPlaylist.id) },
                        ) {
                            Text("Open Bluesky smart playlist")
                        }
                    }
                    TextButton(
                        enabled = !blueskyStatusLoading,
                        onClick = onCreateBlueskySmartPlaylist,
                    ) {
                        Text("Create Bluesky smart playlist")
                    }
                    TextButton(
                        enabled = !blueskyStatusLoading,
                        onClick = { vm.refreshBlueskyStatus() },
                    ) {
                        Text(if (blueskyStatusLoading) "Refreshing..." else "Refresh")
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
                SettingsDescribedRow(
                    title = "Default playlist",
                    description = "Shared links save to Smart Queue unless you choose a playlist here.",
                    trailing = {
                        TextButton(onClick = { showDefaultSavePlaylistDialog = true }) {
                            Text("$defaultSavePlaylistName ▼")
                        }
                    },
                )
                SettingsDescribedRow(
                    title = "Keep share notifications",
                    description = "Off: share results drop away after about 4 seconds. On: results stay in the notification tray.",
                    trailing = {
                        Switch(
                            checked = keepShareResultNotifications,
                            onCheckedChange = { keepShareResultNotifications = it },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Auto-download for offline",
                    description = "When on, successful share-saves also fetch and cache article text for offline use.",
                    trailing = {
                        Switch(
                            checked = autoDownloadSavedArticles,
                            onCheckedChange = {
                                autoDownloadSavedArticles = it
                                vm.saveAutoDownloadSavedArticles(it)
                            },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Default Locus view",
                    description = "Sets how Locus opens by default. You can still cycle views by tapping article text.",
                    trailing = {
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
                    },
                )
                SettingsDescribedRow(
                    title = "Auto-cache favourited items",
                    description = "When on, favourited items (including in Archive) are cached for offline reading. Bin items stay excluded.",
                    trailing = {
                        Switch(
                            checked = autoCacheFavoritedItems,
                            onCheckedChange = {
                                autoCacheFavoritedItems = it
                                vm.saveAutoCacheFavoritedItems(it)
                            },
                        )
                    },
                )
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
                SettingsDescribedRow(
                    title = "Auto-archive at end of article",
                    description = "When playback reaches a real end-of-article, move that item to Archive.",
                    trailing = {
                        Switch(
                            checked = autoArchiveAtArticleEnd,
                            onCheckedChange = {
                                autoArchiveAtArticleEnd = it
                                vm.saveAutoArchiveAtArticleEnd(it)
                            },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Speak title before article",
                    description = "When enabled, playback speaks the title before body text (duplicate intros are skipped).",
                    trailing = {
                        Switch(
                            checked = speakTitleBeforeArticle,
                            onCheckedChange = {
                                speakTitleBeforeArticle = it
                                vm.saveSpeakTitleBeforeArticle(it)
                            },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Skip duplicate intro",
                    description = "When title intro is on, skip matching opening words in body playback.",
                    trailing = {
                        Switch(
                            checked = skipDuplicateOpeningAfterTitleIntro,
                            onCheckedChange = {
                                skipDuplicateOpeningAfterTitleIntro = it
                                vm.saveSkipDuplicateOpeningAfterTitleIntro(it)
                            },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Keep screen on",
                    description = "Keeps screen awake while speaking, or while manually reading in Reader mode on Locus.",
                    trailing = {
                        Switch(
                            checked = keepScreenOnDuringSession,
                            onCheckedChange = {
                                keepScreenOnDuringSession = it
                                vm.saveKeepScreenOnDuringSession(it)
                            },
                        )
                    },
                )
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
                SettingsDescribedRow(
                    title = "Drawer on right side",
                    description = "Move the navigation drawer to the right edge instead of the left edge.",
                    trailing = {
                        Switch(
                            checked = drawerPanelSide == DrawerPanelSide.RIGHT,
                            onCheckedChange = { checked ->
                                drawerPanelSide = if (checked) DrawerPanelSide.RIGHT else DrawerPanelSide.LEFT
                                vm.saveDrawerPanelSide(drawerPanelSide)
                            },
                        )
                    },
                )
                SettingsDescribedRow(
                    title = "Chevron on right side",
                    description = "Snap the player chevron button to the right edge instead of the left edge.",
                    trailing = {
                        Switch(
                            checked = chevronOnRightSide,
                            onCheckedChange = { checked ->
                                chevronOnRightSide = checked
                                vm.savePlayerChevronSnap(
                                    edge = if (checked) PlayerChevronSnapEdge.RIGHT else PlayerChevronSnapEdge.LEFT,
                                    edgeOffset = settings.playerChevronEdgeOffset,
                                )
                            },
                        )
                    },
                )
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
                SettingsDescribedRow(
                    title = "Locus follows now-playing",
                    description = "While previewing another item, tapping Locus returns to the now-playing item. On: jump to live playback line. Off: keep the last reader position.",
                    trailing = {
                        Switch(
                            checked = locusTabReturnsToPlaybackPosition,
                            onCheckedChange = {
                                locusTabReturnsToPlaybackPosition = it
                                vm.saveLocusTabReturnsToPlaybackPosition(it)
                            },
                        )
                    },
                )
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
                SettingsDescribedRow(
                    title = "End-of-article completion cue",
                    description = "Play a short tone when an article finishes.",
                    trailing = {
                        Switch(
                            checked = playCompletionCueAtArticleEnd,
                            onCheckedChange = {
                                playCompletionCueAtArticleEnd = it
                                vm.savePlayCompletionCueAtArticleEnd(it)
                            },
                        )
                    },
                )
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
                Text("Defaults: Local=http://10.0.2.2:8000, LAN=http://192.168.68.124:8000, Remote=$DEFAULT_REMOTE_BASE_URL (fallback HTTP: $DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL)")
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
private fun SettingsKeyValueLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BlueskySourceRow(source: BlueskySourceDiagnostic) {
    val enabled = source.enabled != false
    val stateLabel = if (enabled) "Enabled" else "Paused"
    val stateColor = if (enabled) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.resolvedName,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = stateLabel,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = stateColor,
            )
        }
        source.typeLabel?.let {
            val isAuthenticated = it.equals("Home timeline", ignoreCase = true) ||
                it.equals("List feed", ignoreCase = true)
            val displayLabel = if (isAuthenticated) "$it (authenticated)" else it
            SettingsKeyValueLine("Type", displayLabel)
        }
        SettingsKeyValueLine("Poll interval", source.pollIntervalMinutes?.let { "$it min" } ?: "Default")
        SettingsKeyValueLine(
            "Next due",
            if (enabled) source.resolvedNextDue ?: "Not scheduled" else "Paused",
        )
        SettingsKeyValueLine("Last harvested", source.lastHarvestedAt ?: "Never")
        SettingsKeyValueLine("Last status", source.lastStatus ?: "Unknown")
        val savedCount = source.lastRunSaved
        val dupCount = source.lastRunDuplicate
        val failedCount = source.lastRunFailed
        if (savedCount != null || dupCount != null || failedCount != null) {
            SettingsKeyValueLine(
                "Last run",
                buildString {
                    append("${savedCount ?: 0} saved")
                    append(", ${dupCount ?: 0} duplicate")
                    append(", ${failedCount ?: 0} failed")
                },
            )
        }
        SettingsKeyValueLine("Last attempted", source.lastAttemptedAt ?: "Never")
        SettingsKeyValueLine("Last error", source.resolvedLastErrorMessage?.takeIf { it.isNotBlank() } ?: "None")
        if (source.reconnectRequired == true) {
            Text(
                text = "Reconnect required",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
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
    ConnectionMode.LOCAL -> "Local emulator/dev mode (emulator host loopback via 10.0.2.2)."
    ConnectionMode.LAN -> "Same-network mode (phone + host on the same LAN/Wi-Fi)."
    ConnectionMode.REMOTE -> "Off-LAN mode (Tailscale/VPN or hosted endpoint). HTTPS-first: $DEFAULT_REMOTE_BASE_URL."
}

internal fun connectionModeBaseUrlGuidance(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.LOCAL ->
        "Use http://10.0.2.2:8000 for Android emulator access to your host machine. On physical phones, 10.0.2.2/localhost/127.0.0.1 points to the phone itself; use LAN or Remote."
    ConnectionMode.LAN ->
        "Use http://<LAN-IP>:8000 when phone and host are on the same LAN. Use HTTPS only if you explicitly configured LAN TLS."
    ConnectionMode.REMOTE ->
        "Use HTTPS-first Tailscale remote URL: https://<machine>.<tailnet>.ts.net (canonical $DEFAULT_REMOTE_BASE_URL). Raw Tailscale IP HTTP is fallback-only when TLS is unavailable: $DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL. If using 192.168.x.y or 10.x, use LAN mode instead."
}

private fun connectionModeTokenAuthHelp(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.REMOTE ->
        "Use a per-device token for this remote host. Sign In creates one for this device; manual paste replaces the saved token."
    else ->
        "Use a per-device token for this server. Sign In creates one for this device; manual paste is advanced and replaces the saved token."
}

internal fun connectionModeTokenGuidance(mode: ConnectionMode): String = connectionModeTokenAuthHelp(mode)

internal fun deviceTokenScopeHint(): String =
    "Read-only tokens can view status and content. Read-write tokens are required for saves, playlist changes, and Bluesky connection."

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

private fun buildConnectionTestResultText(
    baseResult: String?,
    pendingSummary: String?,
    timestamp: String?,
): String {
    return buildString {
        append(baseResult ?: "No test run in this session.")
        val hasInlinePending = (baseResult ?: "").contains("\nPending saves:")
        if (!pendingSummary.isNullOrBlank() && !hasInlinePending) {
            append("\n")
            append("Pending saves: ")
            append(pendingSummary)
        }
        if (!timestamp.isNullOrBlank()) {
            append("\n")
            append("Checked at: ")
            append(timestamp)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsActionIconButton(
    icon: @Composable () -> Unit,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            icon()
        }
    }
}

@Composable
private fun SettingsDescribedRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    var descriptionVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title)
                IconButton(onClick = { descriptionVisible = !descriptionVisible }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = if (descriptionVisible) "Hide description" else "Show description",
                        modifier = Modifier.size(16.dp),
                        tint = if (descriptionVisible) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                }
            }
            trailing?.invoke()
        }
        AnimatedVisibility(visible = descriptionVisible) {
            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

private fun sharePlainText(context: android.content.Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(context, chooser, null)
}

private fun formatPendingSaveTestSummary(
    pendingItems: List<PendingManualSaveItem>,
    selectedPlaylistId: Int?,
): String? {
    val messages = pendingItems
        .asSequence()
        .filter { it.destinationPlaylistId == selectedPlaylistId }
        .map { it.lastFailureMessage.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (messages.isEmpty()) return null
    val terminal = messages.filterNot { isPendingSaveProcessingMessage(it) }
    return when {
        terminal.isNotEmpty() -> terminal.take(2).joinToString(" | ")
        else -> "Processing..."
    }
}

private fun isPendingSaveProcessingMessage(message: String): Boolean {
    val normalized = message.trim().lowercase(Locale.getDefault())
    return normalized.contains("processing") ||
        normalized.contains("saving") ||
        normalized.contains("queued")
}

private fun formatBlueskyModeLabel(mode: String?): String {
    val normalized = mode?.trim().orEmpty()
    if (normalized.isBlank()) return "Unknown"
    return when (normalized.lowercase(Locale.ROOT)) {
        "public_author_feed" -> "Public author feed"
        else -> normalized.replace('_', ' ')
    }
}

private fun formatBlueskyBool(value: Boolean?): String {
    return when (value) {
        true -> "Yes"
        false -> "No"
        null -> "Unknown"
    }
}

private fun SmartPlaylistSummary.isBlueskyHarvestPlaylist(): Boolean {
    val captureKinds = filterDefinition.stringList("capture_kinds")
    return captureKinds.any { it.equals("bluesky_harvest", ignoreCase = true) }
}

private fun JsonObject.stringList(key: String): List<String> {
    val values = this[key] as? JsonArray ?: return emptyList()
    return values.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it.isNotEmpty() }
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
