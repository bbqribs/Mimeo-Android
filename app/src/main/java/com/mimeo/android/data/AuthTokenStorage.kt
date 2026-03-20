package com.mimeo.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal interface AuthTokenSlot {
    fun readToken(): String
    fun writeToken(token: String)
    fun clearToken()
}

internal data class AuthTokenWriteResult(
    val usedLegacyFallback: Boolean,
)

internal class AuthTokenStorageController(
    private val secureSlotProvider: () -> AuthTokenSlot?,
    private val legacySlot: AuthTokenSlot,
) {
    fun readToken(): String {
        val secureToken = secureSlotProvider()?.readToken()?.trim().orEmpty()
        if (secureToken.isNotBlank()) {
            return secureToken
        }
        return legacySlot.readToken().trim()
    }

    fun writeToken(token: String): AuthTokenWriteResult {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            clearToken()
            return AuthTokenWriteResult(usedLegacyFallback = false)
        }

        val secureSlot = secureSlotProvider()
        if (secureSlot != null) {
            secureSlot.writeToken(trimmedToken)
            legacySlot.clearToken()
            return AuthTokenWriteResult(usedLegacyFallback = false)
        }

        legacySlot.writeToken(trimmedToken)
        return AuthTokenWriteResult(usedLegacyFallback = true)
    }

    fun clearToken() {
        secureSlotProvider()?.clearToken()
        legacySlot.clearToken()
    }

    fun migrateLegacyToken(legacyToken: String): AuthTokenWriteResult {
        return writeToken(legacyToken.trim())
    }
}

internal class AuthTokenStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val legacyPrefs: SharedPreferences =
        appContext.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
    private val controller = AuthTokenStorageController(
        secureSlotProvider = { secureSlotOrNull() },
        legacySlot = prefsSlot(legacyPrefs),
    )

    fun readToken(): String = runCatching {
        controller.readToken()
    }.getOrElse { error ->
        Log.w(TAG, "Token read failed", error)
        legacyPrefs.getString(TOKEN_KEY, "")?.trim().orEmpty()
    }

    fun writeToken(token: String): AuthTokenWriteResult = runCatching {
        controller.writeToken(token)
    }.getOrElse { error ->
        Log.w(TAG, "Token write failed; using legacy fallback", error)
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            legacyPrefs.edit().remove(TOKEN_KEY).apply()
            AuthTokenWriteResult(usedLegacyFallback = false)
        } else {
            legacyPrefs.edit().putString(TOKEN_KEY, trimmed).apply()
            AuthTokenWriteResult(usedLegacyFallback = true)
        }
    }

    fun clearToken() {
        runCatching { controller.clearToken() }
            .onFailure { error ->
                Log.w(TAG, "Token clear failed; clearing legacy fallback", error)
                legacyPrefs.edit().remove(TOKEN_KEY).apply()
            }
    }

    fun migrateLegacyToken(legacyToken: String): AuthTokenWriteResult = runCatching {
        controller.migrateLegacyToken(legacyToken)
    }.getOrElse { error ->
        Log.w(TAG, "Legacy token migration failed; using legacy fallback", error)
        writeToken(legacyToken)
    }

    private fun prefsSlot(prefs: SharedPreferences): AuthTokenSlot {
        return object : AuthTokenSlot {
            override fun readToken(): String = prefs.getString(TOKEN_KEY, "")?.trim().orEmpty()
            override fun writeToken(token: String) {
                prefs.edit().putString(TOKEN_KEY, token.trim()).apply()
            }

            override fun clearToken() {
                prefs.edit().remove(TOKEN_KEY).apply()
            }
        }
    }

    private fun secureSlotOrNull(): AuthTokenSlot? {
        val securePrefs = securePrefsOrNull() ?: return null
        return prefsSlot(securePrefs)
    }

    private fun securePrefsOrNull(): SharedPreferences? {
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { error ->
            Log.w(TAG, "Secure token storage unavailable; falling back to legacy storage", error)
            null
        }
    }

    companion object {
        private const val TAG = "MimeoAuthTokenStorage"
        private const val TOKEN_KEY = "api_token"
        private const val SECURE_PREFS_FILE = "mimeo_secure_auth"
        private const val LEGACY_PREFS_FILE = "mimeo_auth_fallback"
    }
}
