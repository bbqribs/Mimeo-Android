package com.mimeo.android.ui.reader.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Bottom sheet that translates [sourceText] on-device via ML Kit. Source
 * language is auto-detected; the user can pick the target language. The
 * translation runs whenever [sourceText] or the target language changes, with
 * loading / model-download / error / success states displayed inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(
    sourceText: String,
    translator: ReaderTranslator,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deviceLanguageTag = remember {
        val tag = Locale.getDefault().language.ifBlank { "en" }
        if (TranslateLanguage.fromLanguageTag(tag) != null) tag else "en"
    }
    var targetLanguageTag by rememberSaveable { mutableStateOf(deviceLanguageTag) }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var translating by remember { mutableStateOf(false) }

    LaunchedEffect(sourceText, targetLanguageTag) {
        translating = true
        result = translator.translate(sourceText, targetLanguageTag)
        translating = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Translate",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "From",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 12.dp),
                )
                val detectedTag = (result as? TranslationResult.Success)?.sourceLanguageTag
                    ?: (result as? TranslationResult.UnsupportedLanguagePair)?.sourceLanguageTag
                Text(
                    text = when {
                        translating && detectedTag == null -> "Detecting…"
                        detectedTag != null -> displayNameForLanguageTag(detectedTag)
                        else -> "Auto"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "To",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 24.dp),
                )
                LanguageDropdown(
                    selectedTag = targetLanguageTag,
                    onSelected = { targetLanguageTag = it },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Original",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Translation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 240.dp)
                        .padding(12.dp),
                ) {
                    when {
                        translating -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    text = "Translating…",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        result is TranslationResult.Success -> {
                            Text(
                                text = (result as TranslationResult.Success).translatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                        result is TranslationResult.UnknownSourceLanguage -> {
                            Text(
                                text = "Couldn't detect the source language.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        result is TranslationResult.UnsupportedLanguagePair -> {
                            val r = result as TranslationResult.UnsupportedLanguagePair
                            Text(
                                text = "No translation model available for " +
                                    displayNameForLanguageTag(r.sourceLanguageTag) +
                                    " → " +
                                    displayNameForLanguageTag(r.targetLanguageTag) +
                                    ".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        result is TranslationResult.Error -> {
                            Text(
                                text = (result as TranslationResult.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun LanguageDropdown(
    selectedTag: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
        ) {
            Text(
                text = displayNameForLanguageTag(selectedTag),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Choose target language",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            COMMON_TARGET_LANGUAGE_TAGS.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(displayNameForLanguageTag(tag)) },
                    onClick = {
                        onSelected(tag)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun displayNameForLanguageTag(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val display = locale.getDisplayLanguage(Locale.getDefault())
    return if (display.isNullOrBlank()) tag else display.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

// A pragmatic set of common ML Kit-supported target languages, sorted by
// English display name. Kept short to avoid overwhelming the dropdown; the
// device language is added automatically by the sheet when not in this list.
private val COMMON_TARGET_LANGUAGE_TAGS = listOf(
    "ar", "bn", "zh", "nl", "en", "fr", "de", "el", "hi", "id",
    "it", "ja", "ko", "pl", "pt", "ru", "es", "sv", "th", "tr", "vi",
)
