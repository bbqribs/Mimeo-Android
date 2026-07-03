package com.mimeo.android

import com.mimeo.android.model.StartupDestination
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
        assertEquals(
            ROUTE_SIGN_IN,
            startupStartDestination(
                startupInitialRequiresSignIn = true,
                startupDestination = StartupDestination.UP_NEXT,
            ),
        )
    }

    @Test
    fun `startup nav route is up-next when session is restored`() {
        assertEquals(
            ROUTE_UP_NEXT,
            startupStartDestination(
                startupInitialRequiresSignIn = false,
                startupDestination = StartupDestination.UP_NEXT,
            ),
        )
    }

    @Test
    fun `startup nav route honors stored destination after session restore`() {
        assertEquals(
            ROUTE_INBOX,
            startupStartDestination(
                startupInitialRequiresSignIn = false,
                startupDestination = StartupDestination.INBOX,
            ),
        )
        assertEquals(
            ROUTE_SMART_QUEUE,
            startupStartDestination(
                startupInitialRequiresSignIn = false,
                startupDestination = StartupDestination.SMART_QUEUE,
            ),
        )
    }

    @Test
    fun `startup nav route requires sign-in before stored destination`() {
        assertEquals(
            ROUTE_SIGN_IN,
            startupStartDestination(
                startupInitialRequiresSignIn = true,
                startupDestination = StartupDestination.SMART_QUEUE,
            ),
        )
    }
}
