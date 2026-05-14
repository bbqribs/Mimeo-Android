package com.mimeo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupAuthStateTest {

    @Test
    fun `initial state is loading`() {
        val state: StartupAuthState = StartupAuthState.Loading
        assertFalse(state is StartupAuthState.Ready)
    }

    @Test
    fun `ready state requires sign-in when token is blank`() {
        val state = StartupAuthState.Ready(requiresSignIn = "".isBlank())
        assertTrue(state.requiresSignIn)
    }

    @Test
    fun `ready state requires sign-in when token is whitespace only`() {
        val state = StartupAuthState.Ready(requiresSignIn = "   ".isBlank())
        assertTrue(state.requiresSignIn)
    }

    @Test
    fun `ready state does not require sign-in when token is present`() {
        val state = StartupAuthState.Ready(requiresSignIn = "tok_abc123".isBlank())
        assertFalse(state.requiresSignIn)
    }

    @Test
    fun `loading state is not a ready state`() {
        val loading: StartupAuthState = StartupAuthState.Loading
        assertFalse(loading is StartupAuthState.Ready)
    }

    @Test
    fun `startup nav route is sign-in when session is absent`() {
        val ready = StartupAuthState.Ready(requiresSignIn = true)
        val route = if (ready.requiresSignIn) ROUTE_SIGN_IN else ROUTE_UP_NEXT
        assertEquals(ROUTE_SIGN_IN, route)
    }

    @Test
    fun `startup nav route is up-next when session is restored`() {
        val ready = StartupAuthState.Ready(requiresSignIn = false)
        val route = if (ready.requiresSignIn) ROUTE_SIGN_IN else ROUTE_UP_NEXT
        assertEquals(ROUTE_UP_NEXT, route)
    }
}
