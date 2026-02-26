package com.mimeo.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mimeo.android.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mimeo_settings")

class SettingsStore(private val context: Context) {
    private val baseUrlKey: Preferences.Key<String> = stringPreferencesKey("base_url")
    private val tokenKey: Preferences.Key<String> = stringPreferencesKey("api_token")
    private val autoAdvanceOnCompletionKey: Preferences.Key<Boolean> =
        booleanPreferencesKey("auto_advance_on_completion")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[baseUrlKey] ?: "http://10.0.2.2:8000",
            apiToken = prefs[tokenKey] ?: "",
            autoAdvanceOnCompletion = prefs[autoAdvanceOnCompletionKey] ?: false,
        )
    }

    suspend fun save(baseUrl: String, apiToken: String, autoAdvanceOnCompletion: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[tokenKey] = apiToken.trim()
            prefs[autoAdvanceOnCompletionKey] = autoAdvanceOnCompletion
        }
    }
}
