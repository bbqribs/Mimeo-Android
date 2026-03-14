package com.mimeo.android.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutTest {

    @Test
    fun `sign out confirmation message mentions session ending`() {
        val message = signOutConfirmationMessage()
        assertTrue(
            "message should mention sign-in or session ending",
            message.contains("sign", ignoreCase = true) || message.contains("session", ignoreCase = true),
        )
    }

    @Test
    fun `sign out confirmation message mentions settings are preserved`() {
        val message = signOutConfirmationMessage()
        // URL and settings must not be described as lost
        assertTrue(
            "message should mention that URL or settings are kept",
            message.contains("kept", ignoreCase = true) ||
                message.contains("preserved", ignoreCase = true) ||
                message.contains("settings", ignoreCase = true),
        )
    }

    @Test
    fun `sign out confirmation message does not mention data deletion`() {
        val message = signOutConfirmationMessage()
        assertFalse(
            "message should not imply data deletion",
            message.contains("delet", ignoreCase = true) || message.contains("lost", ignoreCase = true),
        )
    }
}
