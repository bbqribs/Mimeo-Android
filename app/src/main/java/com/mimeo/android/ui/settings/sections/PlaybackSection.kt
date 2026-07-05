package com.mimeo.android.ui.settings.sections

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mimeo.android.AppViewModel
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.DEFAULT_LAN_BASE_URL
import com.mimeo.android.model.DEFAULT_LOCAL_BASE_URL
import com.mimeo.android.model.DEFAULT_PLAYBACK_SPEED_PRESETS
import com.mimeo.android.model.DEFAULT_REMOTE_BASE_URL
import com.mimeo.android.model.DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL
import com.mimeo.android.model.DrawerPanelSide
import com.mimeo.android.model.PLAYBACK_SPEED_PRESET_MAX
import com.mimeo.android.model.PLAYBACK_SPEED_PRESET_MIN
import com.mimeo.android.model.formatPlaybackSpeedPresetValue
import com.mimeo.android.model.isPlaybackSpeedPresetEntryValid
import com.mimeo.android.model.sanitizePlaybackSpeedPresets
import com.mimeo.android.ui.settings.SettingsDescribedRow
import com.mimeo.android.ui.settings.SettingsSectionHeader
import com.mimeo.android.ui.settings.TTS_PREVIEW_PHRASE
import com.mimeo.android.ui.settings.TtsLocaleOption
import com.mimeo.android.ui.settings.TtsVoiceOption
import com.mimeo.android.ui.theme.LocalMimeoColorTokens
import com.mimeo.android.ui.theme.LocalMimeoTypographyTokens
import com.mimeo.android.ui.theme.LocalMimeoV1Active

/**
 * The Playback settings spoke: listening behavior toggles, speed presets, and TTS voice
 * selection. All state here stays owned by `SettingsScreen` (passed in as value + onChange
 * pairs): the toggles feed `saveCurrent`'s combined settings payload, and the TTS engine
 * (`ttsEngine`/`ttsEngineReady`) is created once for the whole Settings screen lifecycle, not
 * per-spoke, so it cannot be owned by this conditionally-composed section.
 */
@Composable
internal fun PlaybackSection(
    vm: AppViewModel,
    settings: AppSettings,
    autoAdvance: Boolean,
    onAutoAdvanceChange: (Boolean) -> Unit,
    speedPresetBoxes: List<String>,
    onSpeedPresetBoxChange: (index: Int, value: String) -> Unit,
    autoArchiveAtArticleEnd: Boolean,
    onAutoArchiveAtArticleEndChange: (Boolean) -> Unit,
    speakTitleBeforeArticle: Boolean,
    onSpeakTitleBeforeArticleChange: (Boolean) -> Unit,
    skipDuplicateOpeningAfterTitleIntro: Boolean,
    onSkipDuplicateOpeningAfterTitleIntroChange: (Boolean) -> Unit,
    keepScreenOnDuringSession: Boolean,
    onKeepScreenOnDuringSessionChange: (Boolean) -> Unit,
    persistentPlayerEnabled: Boolean,
    onPersistentPlayerEnabledChange: (Boolean) -> Unit,
    drawerPanelSide: DrawerPanelSide,
    onDrawerPanelSideCheckedChange: (Boolean) -> Unit,
    chevronOnRightSide: Boolean,
    onChevronOnRightSideCheckedChange: (Boolean) -> Unit,
    autoScrollWhileListening: Boolean,
    onAutoScrollWhileListeningChange: (Boolean) -> Unit,
    locusTabReturnsToPlaybackPosition: Boolean,
    onLocusTabReturnsToPlaybackPositionChange: (Boolean) -> Unit,
    continuousNowPlayingMarquee: Boolean,
    onContinuousNowPlayingMarqueeChange: (Boolean) -> Unit,
    playCompletionCueAtArticleEnd: Boolean,
    onPlayCompletionCueAtArticleEndChange: (Boolean) -> Unit,
    ttsEngineReady: Boolean,
    ttsEngine: TextToSpeech?,
    ttsVoiceName: String,
    ttsVoiceOptions: List<TtsVoiceOption>,
    ttsLocaleOptions: List<TtsLocaleOption>,
    selectedTtsLocaleTag: String,
    showTtsLocaleMenu: Boolean,
    onShowTtsLocaleMenuChange: (Boolean) -> Unit,
    onLocaleSelected: (TtsLocaleOption) -> Unit,
    showTtsVoiceMenu: Boolean,
    onShowTtsVoiceMenuChange: (Boolean) -> Unit,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    onUseSystemDefaultVoice: () -> Unit,
    engineDefaultTtsVoiceLabel: String,
    ttsFallbackMessage: String?,
    ttsPreviewing: Boolean,
    onTtsPreviewingChange: (Boolean) -> Unit,
    applyCurrentTtsVoice: (TextToSpeech) -> Unit,
) {
    val isV1 = LocalMimeoV1Active.current
    val mColors = LocalMimeoColorTokens.current
    val mTypography = LocalMimeoTypographyTokens.current

    SettingsSectionHeader(
        title = "Playback",
        subtitle = "Session and listening behavior across tabs and reading mode.",
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Playback",
                style = if (isV1) mTypography.row else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (isV1) mColors.fg else Color.Unspecified,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto-advance on completion")
                Switch(
                    checked = autoAdvance,
                    onCheckedChange = onAutoAdvanceChange,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val minLabel = formatPlaybackSpeedPresetValue(PLAYBACK_SPEED_PRESET_MIN)
                val maxLabel = formatPlaybackSpeedPresetValue(PLAYBACK_SPEED_PRESET_MAX)
                val presetBoxesValid =
                    speedPresetBoxes.all { isPlaybackSpeedPresetEntryValid(it) }
                val presetBoxesAllBlank = speedPresetBoxes.all { it.isBlank() }
                // The preset list Apply would persist, compared against the
                // operative list so Apply stays disabled with no real change.
                val wouldBePresets = sanitizePlaybackSpeedPresets(
                    speedPresetBoxes.mapNotNull { it.trim().toFloatOrNull() },
                ).ifEmpty { DEFAULT_PLAYBACK_SPEED_PRESETS }
                val presetBoxesDirty =
                    wouldBePresets.map { formatPlaybackSpeedPresetValue(it) } !=
                        settings.playbackSpeedPresets.map { formatPlaybackSpeedPresetValue(it) }
                Text("Speed presets")
                Text(
                    text = "One box per quick-tap speed in the player panel. Each box takes " +
                        "a number from ${minLabel}× to ${maxLabel}×, or leave it blank. " +
                        "Duplicates are merged and the list is sorted ascending.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    speedPresetBoxes.forEachIndexed { index, boxValue ->
                        OutlinedTextField(
                            value = boxValue,
                            onValueChange = { entered -> onSpeedPresetBoxChange(index, entered) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            isError = !isPlaybackSpeedPresetEntryValid(boxValue),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                        )
                    }
                }
                Text(
                    text = when {
                        !presetBoxesValid ->
                            "Each box must be empty or a number from ${minLabel}× to ${maxLabel}×."
                        presetBoxesAllBlank -> "Enter at least one preset speed."
                        !presetBoxesDirty -> "These speeds are already saved."
                        else -> "Apply saves these speeds to the player; Reset restores the defaults."
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (!presetBoxesValid || presetBoxesAllBlank) {
                        androidx.compose.material3.MaterialTheme.colorScheme.error
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        enabled = presetBoxesValid && !presetBoxesAllBlank && presetBoxesDirty,
                        onClick = {
                            val values = speedPresetBoxes
                                .mapNotNull { it.trim().toFloatOrNull() }
                            vm.savePlaybackSpeedPresets(values)
                        },
                    ) {
                        Text("Apply")
                    }
                    TextButton(
                        onClick = {
                            vm.savePlaybackSpeedPresets(DEFAULT_PLAYBACK_SPEED_PRESETS)
                        },
                    ) {
                        Text("Reset to defaults")
                    }
                }
            }
            SettingsDescribedRow(
                title = "Auto-archive at end of article",
                description = "When playback reaches a real end-of-article, move that item to Archive.",
                trailing = {
                    Switch(
                        checked = autoArchiveAtArticleEnd,
                        onCheckedChange = onAutoArchiveAtArticleEndChange,
                    )
                },
            )
            SettingsDescribedRow(
                title = "Speak title before article",
                description = "When enabled, playback speaks the title before body text (duplicate intros are skipped).",
                trailing = {
                    Switch(
                        checked = speakTitleBeforeArticle,
                        onCheckedChange = onSpeakTitleBeforeArticleChange,
                    )
                },
            )
            SettingsDescribedRow(
                title = "Skip duplicate intro",
                description = "When title intro is on, skip matching opening words in body playback.",
                trailing = {
                    Switch(
                        checked = skipDuplicateOpeningAfterTitleIntro,
                        onCheckedChange = onSkipDuplicateOpeningAfterTitleIntroChange,
                    )
                },
            )
            SettingsDescribedRow(
                title = "Keep screen on",
                description = "Keeps screen awake while speaking, or while manually reading in Reader mode on Locus.",
                trailing = {
                    Switch(
                        checked = keepScreenOnDuringSession,
                        onCheckedChange = onKeepScreenOnDuringSessionChange,
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
                    onCheckedChange = onPersistentPlayerEnabledChange,
                )
            }
            SettingsDescribedRow(
                title = "Drawer on right side",
                description = "Move the navigation drawer to the right edge instead of the left edge.",
                trailing = {
                    Switch(
                        checked = drawerPanelSide == DrawerPanelSide.RIGHT,
                        onCheckedChange = onDrawerPanelSideCheckedChange,
                    )
                },
            )
            SettingsDescribedRow(
                title = "Chevron on right side",
                description = "Snap the player chevron button to the right edge instead of the left edge.",
                trailing = {
                    Switch(
                        checked = chevronOnRightSide,
                        onCheckedChange = onChevronOnRightSideCheckedChange,
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
                    onCheckedChange = onAutoScrollWhileListeningChange,
                )
            }
            SettingsDescribedRow(
                title = "Locus follows now-playing",
                description = "While previewing another item, tapping Locus returns to the now-playing item. On: jump to live playback line. Off: keep the last reader position.",
                trailing = {
                    Switch(
                        checked = locusTabReturnsToPlaybackPosition,
                        onCheckedChange = onLocusTabReturnsToPlaybackPositionChange,
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
                    onCheckedChange = onContinuousNowPlayingMarqueeChange,
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
                        onCheckedChange = onPlayCompletionCueAtArticleEndChange,
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
                        onClick = { onShowTtsLocaleMenuChange(true) },
                    ) {
                        val selectedLocaleLabel = ttsLocaleOptions.firstOrNull { it.tag == selectedTtsLocaleTag }?.label
                        Text("Language: ${selectedLocaleLabel ?: "Any"} ▼")
                    }
                    DropdownMenu(
                        expanded = showTtsLocaleMenu,
                        onDismissRequest = { onShowTtsLocaleMenuChange(false) },
                    ) {
                        if (ttsLocaleOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No language options") },
                                onClick = { onShowTtsLocaleMenuChange(false) },
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
                                    onShowTtsLocaleMenuChange(false)
                                    onLocaleSelected(option)
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
                        onClick = { onShowTtsVoiceMenuChange(true) },
                    ) {
                        val selectedVoiceLabel = selected?.voiceLabel ?: "System default"
                        Text("Voice: $selectedVoiceLabel ▼")
                    }
                    TextButton(
                        enabled = ttsEngineReady && ttsVoiceName.trim().isNotBlank(),
                        onClick = onUseSystemDefaultVoice,
                    ) {
                        Text("Use system default")
                    }
                    DropdownMenu(
                        expanded = showTtsVoiceMenu,
                        onDismissRequest = { onShowTtsVoiceMenuChange(false) },
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
                                    onShowTtsVoiceMenuChange(false)
                                    onVoiceSelected(option)
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
                        onTtsPreviewingChange(true)
                        engine.speak(
                            TTS_PREVIEW_PHRASE,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "mimeo-tts-preview-${System.currentTimeMillis()}",
                        )
                        onTtsPreviewingChange(false)
                    },
                ) { Text(if (ttsPreviewing) "Playing..." else "Preview") }
            }
            Text("Defaults: Local=$DEFAULT_LOCAL_BASE_URL, LAN=$DEFAULT_LAN_BASE_URL, Remote=$DEFAULT_REMOTE_BASE_URL (fallback HTTP: $DEFAULT_REMOTE_HTTP_FALLBACK_BASE_URL)")
        }
    }
}
