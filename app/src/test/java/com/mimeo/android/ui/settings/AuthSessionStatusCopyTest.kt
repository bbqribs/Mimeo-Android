package com.mimeo.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionStatusCopyTest {

    @Test
    fun sessionStatusNotSignedInWhenNoSavedToken() {
        val summary = formatAuthSessionStatusSummary(
            savedToken = "   ",
            editedToken = "",
        )

        assertEquals(
            "Session: not signed in. Use Sign In to create a user-linked device session token.",
            summary,
        )
    }

    @Test
    fun sessionStatusShowsUnsavedAdvancedTokenEdits() {
        val summary = formatAuthSessionStatusSummary(
            savedToken = "token-a",
            editedToken = "token-b",
        )

        assertEquals(
            "Session: signed in. Advanced token field has unsaved edits; Save applies token replacement.",
            summary,
        )
    }

    @Test
    fun sessionStatusShowsSignedInWhenTokenMatches() {
        val summary = formatAuthSessionStatusSummary(
            savedToken = " token-a ",
            editedToken = "token-a",
        )

        assertEquals(
            "Session: signed in with saved device session token.",
            summary,
        )
    }

    @Test
    fun authConsequencesSummaryMentionsStaleSignOutAndPasswordChange() {
        val summary = authSessionConsequenceSummary()

        assertTrue(summary.contains("returns to Sign In"))
        assertTrue(summary.contains("Sign out clears only this device session token"))
        assertTrue(summary.contains("Change password keeps this device signed in"))
    }
}

