package com.mimeo.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScopedRequestContextTest {

    @Test
    fun sameBaseTokenAndOwner_isCurrent() {
        val expected = AccountScopedRequestContext(
            baseUrl = "https://reader.example.com/",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )
        val current = AccountScopedRequestContext(
            baseUrl = "https://reader.example.com",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )

        assertTrue(accountScopedRequestStillCurrent(expected, current))
    }

    @Test
    fun changedOwner_isStale() {
        val expected = AccountScopedRequestContext(
            baseUrl = "https://reader.example.com",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )
        val current = expected.copy(localStateOwner = "owner-b")

        assertFalse(accountScopedRequestStillCurrent(expected, current))
    }

    @Test
    fun changedToken_isStale() {
        val expected = AccountScopedRequestContext(
            baseUrl = "https://reader.example.com",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )
        val current = expected.copy(apiToken = "token-b")

        assertFalse(accountScopedRequestStillCurrent(expected, current))
    }

    @Test
    fun blankOwner_isStale() {
        val expected = AccountScopedRequestContext(
            baseUrl = "https://reader.example.com",
            apiToken = "token-a",
            localStateOwner = "",
        )
        val current = expected

        assertFalse(accountScopedRequestStillCurrent(expected, current))
    }

    @Test
    fun sameAccountBaseUrlSwitch_isStillCurrentWhenOwnerUnchanged() {
        // A LAN/Tailscale/Remote connection-mode switch for the same signed-in account changes
        // baseUrl but must not itself look like a different owner (see saveSettings' H1 fix):
        // this only asserts accountScopedRequestStillCurrent's own baseUrl comparison is
        // normalization-only (trailing slash), not a behavior change trigger by itself.
        val expected = AccountScopedRequestContext(
            baseUrl = "https://lan.example.com",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )
        val current = AccountScopedRequestContext(
            baseUrl = "https://lan.example.com/",
            apiToken = "token-a",
            localStateOwner = "owner-a",
        )

        assertTrue(accountScopedRequestStillCurrent(expected, current))
    }
}

class SettingsScreenAuthTargetChangedTest {

    @Test
    fun sameTokenAfterConnectionModeSwitch_isNotAnAuthTargetChange() {
        // The Settings screen bundles base URL + connection mode + token into one save call.
        // Switching LAN -> Tailscale/Remote for the same account keeps the token identical, so
        // this must report false or the switch would wipe the account's offline cache/pending
        // actions (the H1 regression the local-state-owner fix must not reintroduce).
        assertFalse(settingsScreenAuthTargetChanged(previousToken = "same-token", nextToken = "same-token"))
        assertFalse(settingsScreenAuthTargetChanged(previousToken = " same-token ", nextToken = "same-token"))
    }

    @Test
    fun differentToken_isAnAuthTargetChange() {
        assertTrue(settingsScreenAuthTargetChanged(previousToken = "old-token", nextToken = "new-token"))
    }

    @Test
    fun blankToNonBlankToken_isAnAuthTargetChange() {
        assertTrue(settingsScreenAuthTargetChanged(previousToken = "", nextToken = "new-token"))
    }

    @Test
    fun nonBlankToBlankToken_isAnAuthTargetChange() {
        assertTrue(settingsScreenAuthTargetChanged(previousToken = "old-token", nextToken = ""))
    }

    @Test
    fun blankToBlankToken_isNotAnAuthTargetChange() {
        assertFalse(settingsScreenAuthTargetChanged(previousToken = "", nextToken = " "))
    }
}
