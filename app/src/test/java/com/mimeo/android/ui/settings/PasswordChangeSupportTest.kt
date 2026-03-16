package com.mimeo.android.ui.settings

import com.mimeo.android.data.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PasswordChangeSupportTest {

    @Test
    fun `validates required fields and confirmation`() {
        assertEquals(
            "Enter your current password",
            validatePasswordChangeInput("", "new-password", "new-password"),
        )
        assertEquals(
            "New password and confirmation must match",
            validatePasswordChangeInput("current-password", "new-password", "different-password"),
        )
    }

    @Test
    fun `validates minimum length guidance`() {
        assertEquals(
            "New password must be at least 12 characters",
            validatePasswordChangeInput("current-password", "short", "short"),
        )
        assertEquals(
            null,
            validatePasswordChangeInput("current-password", "long-enough12", "long-enough12"),
        )
    }

    @Test
    fun `maps wrong current password distinctly from stale auth`() {
        val wrongPassword = resolvePasswordChangeError(
            ApiException(401, """HTTP 401: {"detail":"Current password is incorrect"}"""),
        )
        assertEquals("Current password is incorrect", wrongPassword.message)
        assertFalse(wrongPassword.staleAuth)

        val genericWrongPassword = resolvePasswordChangeError(
            ApiException(401, """HTTP 401: {"detail":"Invalid credentials or password"}"""),
        )
        assertEquals("Current password is incorrect", genericWrongPassword.message)
        assertFalse(genericWrongPassword.staleAuth)

        val staleAuth = resolvePasswordChangeError(
            ApiException(401, """HTTP 401: {"detail":"Invalid token"}"""),
        )
        assertEquals("Session expired. Please sign in again.", staleAuth.message)
        assertTrue(staleAuth.staleAuth)
    }

    @Test
    fun `maps validation network and success copy to bounded messages`() {
        assertEquals(
            "Password too short",
            resolvePasswordChangeError(
                ApiException(422, """HTTP 422: {"detail":"Password too short"}"""),
            ).message,
        )
        assertEquals(
            "Password change needs a user sign-in token. Sign out, then sign in.",
            resolvePasswordChangeError(
                ApiException(
                    403,
                    """HTTP 403: {"detail":"Password change is not available for operator tokens"}""",
                ),
            ).message,
        )
        assertEquals(
            "Password change is not available on this server yet.",
            resolvePasswordChangeError(
                ApiException(404, """HTTP 404: {"detail":"Not Found"}"""),
            ).message,
        )
        assertEquals(
            "Couldn't change password. Check your connection and try again.",
            resolvePasswordChangeError(IOException("timeout")).message,
        )
        assertTrue(passwordChangeSuccessMessage().contains("Other sessions were signed out"))
        assertTrue(passwordChangeSuccessMessage().contains("This device stays signed in"))
    }
}
