package com.mimeo.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimeo.android.model.AppSettings
import com.mimeo.android.model.ParagraphSpacingOption
import com.mimeo.android.model.decodeSelectedPlaylistId
import com.mimeo.android.model.encodeSelectedPlaylistId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mimeo_settings")

class SettingsStore(private val context: Context) {
    private val baseUrlKey: Preferences.Key<String> = stringPreferencesKey("base_url")
    private val tokenKey: Preferences.Key<String> = stringPreferencesKey("api_token")
    private val autoAdvanceOnCompletionKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_advance_on_completion")
    private val autoScrollWhileListeningKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_scroll_while_listening")
    private val forceSentenceHighlightFallbackKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("force_sentence_highlight_fallback")
    private val keepShareResultNotificationsKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("keep_share_result_notifications")
    private val playbackSpeedKey: Preferences.Key<Float> =
        floatPreferencesKey("playback_speed")
    private val selectedPlaylistIdKey: Preferences.Key<Int> =
        intPreferencesKey("selected_playlist_id")
    private val defaultSavePlaylistIdKey: Preferences.Key<Int> =
        intPreferencesKey("default_save_playlist_id")
    private val readingFontSizeSpKey: Preferences.Key<Int> =
        intPreferencesKey("reading_font_size_sp")
    private val readingLineHeightPercentKey: Preferences.Key<Int> =
        intPreferencesKey("reading_line_height_percent")
    private val readingMaxWidthDpKey: Preferences.Key<Int> =
        intPreferencesKey("reading_max_width_dp")
    private val readingParagraphSpacingKey: Preferences.Key<String> =
        stringPreferencesKey("reading_paragraph_spacing")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[baseUrlKey] ?: "http://10.0.2.2:8000",
            apiToken = prefs[tokenKey] ?: "",
            autoAdvanceOnCompletion = prefs[autoAdvanceOnCompletionKey] ?: false,
            autoScrollWhileListening = prefs[autoScrollWhileListeningKey] ?: true,
            forceSentenceHighlightFallback = prefs[forceSentenceHighlightFallbackKey] ?: false,
            keepShareResultNotifications = prefs[keepShareResultNotificationsKey] ?: false,
            playbackSpeed = prefs[playbackSpeedKey] ?: 1.0f,
            selectedPlaylistId = decodeSelectedPlaylistId(prefs[selectedPlaylistIdKey]),
            defaultSavePlaylistId = decodeSelectedPlaylistId(prefs[defaultSavePlaylistIdKey]),
            readingFontSizeSp = prefs[readingFontSizeSpKey] ?: 18,
            readingLineHeightPercent = prefs[readingLineHeightPercentKey] ?: 160,
            readingMaxWidthDp = prefs[readingMaxWidthDpKey] ?: 720,
            readingParagraphSpacing = prefs[readingParagraphSpacingKey]
                ?.let { runCatching { ParagraphSpacingOption.valueOf(it) }.getOrNull() }
                ?: ParagraphSpacingOption.MEDIUM,
        )
    }

    suspend fun save(
        baseUrl: String,
        apiToken: String,
        autoAdvanceOnCompletion: Boolean,
        autoScrollWhileListening: Boolean,
        forceSentenceHighlightFallback: Boolean,
        keepShareResultNotifications: Boolean,
        playbackSpeed: Float,
        selectedPlaylistId: Int?,
        defaultSavePlaylistId: Int?,
        readingFontSizeSp: Int,
        readingLineHeightPercent: Int,
        readingMaxWidthDp: Int,
        readingParagraphSpacing: ParagraphSpacingOption,
    ) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[tokenKey] = apiToken.trim()
            prefs[autoAdvanceOnCompletionKey] = autoAdvanceOnCompletion
            prefs[autoScrollWhileListeningKey] = autoScrollWhileListening
            prefs[forceSentenceHighlightFallbackKey] = forceSentenceHighlightFallback
            prefs[keepShareResultNotificationsKey] = keepShareResultNotifications
            prefs[playbackSpeedKey] = playbackSpeed
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
            prefs[defaultSavePlaylistIdKey] = encodeSelectedPlaylistId(defaultSavePlaylistId)
            prefs[readingFontSizeSpKey] = readingFontSizeSp
            prefs[readingLineHeightPercentKey] = readingLineHeightPercent
            prefs[readingMaxWidthDpKey] = readingMaxWidthDp
            prefs[readingParagraphSpacingKey] = readingParagraphSpacing.name
        }
    }

    suspend fun saveSelectedPlaylistId(selectedPlaylistId: Int?) {
        context.dataStore.edit { prefs ->
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
        }
    }

    suspend fun saveDefaultSavePlaylistId(defaultSavePlaylistId: Int?) {
        context.dataStore.edit { prefs ->
            prefs[defaultSavePlaylistIdKey] = encodeSelectedPlaylistId(defaultSavePlaylistId)
        }
    }

    suspend fun saveReadingPreferences(
        readingFontSizeSp: Int,
        readingLineHeightPercent: Int,
        readingMaxWidthDp: Int,
        readingParagraphSpacing: ParagraphSpacingOption,
    ) {
        context.dataStore.edit { prefs ->
            prefs[readingFontSizeSpKey] = readingFontSizeSp
            prefs[readingLineHeightPercentKey] = readingLineHeightPercent
            prefs[readingMaxWidthDpKey] = readingMaxWidthDp
            prefs[readingParagraphSpacingKey] = readingParagraphSpacing.name
        }
    }

    suspend fun savePlaybackSpeed(playbackSpeed: Float) {
        context.dataStore.edit { prefs ->
            prefs[playbackSpeedKey] = playbackSpeed
        }
    }

    suspend fun saveForceSentenceHighlightFallback(forceSentenceHighlightFallback: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[forceSentenceHighlightFallbackKey] = forceSentenceHighlightFallback
        }
    }
}
