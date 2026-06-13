package com.mimeo.android.ui.bluesky

import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyOperatorStatusResponse
import com.mimeo.android.model.BlueskySourceDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyHealthTest {

    @Test
    fun noDataIsUnknownWithTryAgain() {
        val health = resolveBlueskyHealth(account = null, operatorStatus = null)
        assertEquals(BlueskyHealthState.UNKNOWN, health.state)
        assertEquals(BlueskyRecoveryAction.TRY_AGAIN, health.action)
    }

    @Test
    fun notConnectedOffersConnect() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(connected = false),
            operatorStatus = null,
        )
        assertEquals(BlueskyHealthState.NOT_CONNECTED, health.state)
        assertEquals(BlueskyRecoveryAction.CONNECT, health.action)
        assertNull(health.handle)
    }

    @Test
    fun healthyConnectionShowsHandleAndNoAction() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(
                connected = true,
                handle = "alice.bsky.social",
                validationState = "ok",
            ),
            operatorStatus = BlueskyOperatorStatusResponse(lastRunStatus = "ok"),
        )
        assertEquals(BlueskyHealthState.CONNECTED, health.state)
        assertEquals(BlueskyRecoveryAction.NONE, health.action)
        assertEquals("Connected as @alice.bsky.social", health.title)
    }

    @Test
    fun authErrorValidationNeedsReconnect() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(
                connected = true,
                handle = "alice.bsky.social",
                validationState = "auth_error",
            ),
            operatorStatus = null,
        )
        assertEquals(BlueskyHealthState.ACTION_NEEDED, health.state)
        assertEquals(BlueskyRecoveryAction.RECONNECT, health.action)
    }

    @Test
    fun reconnectRequiredSourceNeedsReconnect() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(connected = true, handle = "a.bsky.social"),
            operatorStatus = BlueskyOperatorStatusResponse(
                lastRunStatus = "ok",
                sources = listOf(BlueskySourceDiagnostic(reconnectRequired = true)),
            ),
        )
        assertEquals(BlueskyHealthState.ACTION_NEEDED, health.state)
        assertEquals(BlueskyRecoveryAction.RECONNECT, health.action)
    }

    @Test
    fun rateLimitedIsTemporaryTrouble() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(connected = true, handle = "a.bsky.social"),
            operatorStatus = BlueskyOperatorStatusResponse(lastRunStatus = "rate_limited"),
        )
        assertEquals(BlueskyHealthState.TEMPORARY_TROUBLE, health.state)
        assertEquals(BlueskyRecoveryAction.TRY_AGAIN, health.action)
    }

    @Test
    fun authTroubleWinsOverTemporaryTrouble() {
        val health = resolveBlueskyHealth(
            account = BlueskyAccountConnectionResponse(connected = true, handle = "a.bsky.social"),
            operatorStatus = BlueskyOperatorStatusResponse(
                lastRunStatus = "rate_limited",
                sources = listOf(BlueskySourceDiagnostic(lastStatus = "auth_error")),
            ),
        )
        assertEquals(BlueskyHealthState.ACTION_NEEDED, health.state)
    }

    @Test
    fun plainStatusHidesRawCodes() {
        assertEquals("Working normally", blueskyPlainStatus("ok"))
        assertEquals("Sign-in needs attention", blueskyPlainStatus("auth_error"))
        assertEquals("Busy (rate limited)", blueskyPlainStatus("rate_limited"))
        assertEquals("Unknown", blueskyPlainStatus(null))
        // An unmapped code is humanized, never shown raw with underscores.
        assertTrue(!blueskyPlainStatus("weird_state").contains("_"))
    }
}
