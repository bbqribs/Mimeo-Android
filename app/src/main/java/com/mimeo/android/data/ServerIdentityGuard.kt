package com.mimeo.android.data

fun normalizeServerIdentity(baseUrl: String): String =
    baseUrl.trim().trimEnd('/').lowercase()

fun detectServerIdentityMismatch(storedIdentity: String, newIdentity: String): Boolean {
    if (storedIdentity.isBlank()) return false
    return storedIdentity != newIdentity
}

sealed class ServerIdentityGuardState {
    object Idle : ServerIdentityGuardState()
    data class AwaitingConfirmation(
        val pendingServerUrl: String,
        val pendingUsername: String,
        val pendingPassword: String,
    ) : ServerIdentityGuardState()
}
