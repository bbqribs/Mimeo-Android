package com.mimeo.android.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimeo.android.AppViewModel
import com.mimeo.android.BuildConfig
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.ui.theme.ReaderLiterataFontFamily

private const val PREVIEW_PARAGRAPH = "Mimeo now remembers your reading layout so Locus feels like a calm, bookish surface instead of a raw text dump."

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenDiagnostics: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val testingConnection by vm.testingConnection.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val configuration = LocalConfiguration.current
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }
    var autoAdvance by remember(settings.autoAdvanceOnCompletion) {
        mutableStateOf(settings.autoAdvanceOnCompletion)
    }
    var persistentPlayerEnabled by remember(settings.persistentPlayerEnabled) {
        mutableStateOf(settings.persistentPlayerEnabled)
    }
    var autoScrollWhileListening by remember(settings.autoScrollWhileListening) {
        mutableStateOf(settings.autoScrollWhileListening)
    }
    var continuousNowPlayingMarquee by remember(settings.continuousNowPlayingMarquee) {
        mutableStateOf(settings.continuousNowPlayingMarquee)
    }
    var forceSentenceHighlightFallback by remember(settings.forceSentenceHighlightFallback) {
        mutableStateOf(settings.forceSentenceHighlightFallback)
    }
    var keepShareResultNotifications by remember(settings.keepShareResultNotifications) {
        mutableStateOf(settings.keepShareResultNotifications)
    }
    var readingFontSizeSp by remember(settings.readingFontSizeSp) {
        mutableIntStateOf(settings.readingFontSizeSp)
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

    fun saveCurrent() {
        vm.saveSettings(
            baseUrl = baseUrl,
            token = token,
            autoAdvanceOnCompletion = autoAdvance,
            persistentPlayerEnabled = persistentPlayerEnabled,
            autoScrollWhileListening = autoScrollWhileListening,
            continuousNowPlayingMarquee = continuousNowPlayingMarquee,
            forceSentenceHighlightFallback = forceSentenceHighlightFallback,
            keepShareResultNotifications = keepShareResultNotifications,
        )
    }

    fun saveReading(
        fontSizeSp: Int = readingFontSizeSp,
        lineHeightPercent: Int = readingLineHeightPercent,
        maxWidthDp: Int = readingMaxWidthDp,
        paragraphSpacing: ParagraphSpacingOption = readingParagraphSpacing,
    ) {
        vm.saveReadingPreferences(
            readingFontSizeSp = fontSizeSp,
            readingLineHeightPercent = lineHeightPercent,
            readingMaxWidthDp = maxWidthDp,
            readingParagraphSpacing = paragraphSpacing,
        )
    }

    LaunchedEffect(testRequested, testingConnection, statusMessage) {
        if (!testRequested || testingConnection) return@LaunchedEffect
        val message = statusMessage.orEmpty()
        if (message.equals("Settings saved", ignoreCase = true)) return@LaunchedEffect
        testRequested = false
        when {
            message.startsWith("Connected") -> {
                vm.showSnackbar("Connected")
            }
            message.contains("Token required", ignoreCase = true) -> {
                vm.showSnackbar("Token required")
            }
            else -> {
                vm.showSnackbar(
                    message = "Can't reach server",
                    actionLabel = "Diagnostics",
                    actionKey = "open_diagnostics",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshPlaylists()
    }

    val defaultSavePlaylistName = playlists.firstOrNull { it.id == settings.defaultSavePlaylistId }?.name ?: "Smart Queue"

    val previewTextStyle = TextStyle(
        fontFamily = if (ReaderLiterataFontFamily == FontFamily.Default) FontFamily.Serif else ReaderLiterataFontFamily,
        fontSize = readingFontSizeSp.sp,
        lineHeight = (readingFontSizeSp * (readingLineHeightPercent / 100f)).sp,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    )
    val isWideReadingSurface = configuration.screenWidthDp >= 700

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Connection")
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { saveCurrent() }) { Text("Save") }
                    Button(
                        enabled = !testingConnection,
                        onClick = {
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
            }
        }

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
            }
        }

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
                        onCheckedChange = { autoAdvance = it },
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
                    Text("Continuous now-playing marquee")
                    Switch(
                        checked = continuousNowPlayingMarquee,
                        onCheckedChange = {
                            continuousNowPlayingMarquee = it
                            vm.saveContinuousNowPlayingMarquee(it)
                        },
                    )
                }
                Text("Emulator default: http://10.0.2.2:8000")
            }
        }

        if (BuildConfig.DEBUG) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Debug")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Force sentence highlight fallback")
                            Text(
                                text = "Disables TTS range-level highlighting so you can verify the sentence-level fallback path.",
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
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Reading")
                Text("Font: Literata")

                Text("Font size: ${readingFontSizeSp}sp")
                Slider(
                    value = readingFontSizeSp.toFloat(),
                    onValueChange = {
                        readingFontSizeSp = it.toInt()
                        saveReading(fontSizeSp = readingFontSizeSp)
                    },
                    valueRange = 16f..24f,
                    steps = 7,
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

                if (isWideReadingSurface) {
                    Text("Max width: ${readingMaxWidthDp}dp")
                    Slider(
                        value = readingMaxWidthDp.toFloat(),
                        onValueChange = {
                            readingMaxWidthDp = it.toInt()
                            saveReading(maxWidthDp = readingMaxWidthDp)
                        },
                        valueRange = 600f..900f,
                        steps = 5,
                    )
                } else {
                    Text(
                        text = "Max width applies on wider screens such as tablets.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }

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
                            text = PREVIEW_PARAGRAPH,
                            style = previewTextStyle,
                        )
                        Text(
                            text = "This sample reflects your current reading settings immediately.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
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
