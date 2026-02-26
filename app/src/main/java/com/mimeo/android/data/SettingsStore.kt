package com.mimeo.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimeo.android.model.AppSettings
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
    private val selectedPlaylistIdKey: Preferences.Key<Int> =
        intPreferencesKey("selected_playlist_id")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[baseUrlKey] ?: "http://10.0.2.2:8000",
            apiToken = prefs[tokenKey] ?: "",
            autoAdvanceOnCompletion = prefs[autoAdvanceOnCompletionKey] ?: false,
            autoScrollWhileListening = prefs[autoScrollWhileListeningKey] ?: true,
            selectedPlaylistId = decodeSelectedPlaylistId(prefs[selectedPlaylistIdKey]),
        )
    }

    suspend fun save(
        baseUrl: String,
        apiToken: String,
        autoAdvanceOnCompletion: Boolean,
        autoScrollWhileListening: Boolean,
        selectedPlaylistId: Int?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[tokenKey] = apiToken.trim()
            prefs[autoAdvanceOnCompletionKey] = autoAdvanceOnCompletion
            prefs[autoScrollWhileListeningKey] = autoScrollWhileListening
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
        }
    }

    suspend fun saveSelectedPlaylistId(selectedPlaylistId: Int?) {
        context.dataStore.edit { prefs ->
            prefs[selectedPlaylistIdKey] = encodeSelectedPlaylistId(selectedPlaylistId)
        }
    }
}
