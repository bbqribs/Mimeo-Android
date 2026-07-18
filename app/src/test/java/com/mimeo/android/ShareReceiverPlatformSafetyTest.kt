package com.mimeo.android

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.player.postPlaybackNotificationIfAllowed
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ShareReceiverPlatformSafetyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [32])
    fun `referrer package uses compatible parcelable path before API 33`() {
        val intent = Intent().putExtra(
            Intent.EXTRA_REFERRER,
            Uri.parse("android-app://com.example.legacy"),
        )

        assertEquals("com.example.legacy", deriveSourceAppPackage(intent))
    }

    @Test
    @Config(sdk = [33])
    fun `referrer package uses typed parcelable path from API 33`() {
        val intent = Intent().putExtra(
            Intent.EXTRA_REFERRER,
            Uri.parse("android-app://com.example.modern"),
        )

        assertEquals("com.example.modern", deriveSourceAppPackage(intent))
    }

    @Test
    @Config(sdk = [32])
    fun `share notification remains available before runtime permission exists`() {
        shadowOf(context.applicationContext as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            true,
            ShareResultNotifications(context).postNotificationIfAllowed(2401, Notification()),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `share notification is skipped when runtime permission is denied`() {
        shadowOf(context.applicationContext as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            false,
            ShareResultNotifications(context).postNotificationIfAllowed(2402, Notification()),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `share notification posts when runtime permission is granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            true,
            ShareResultNotifications(context).postNotificationIfAllowed(2403, Notification()),
        )
    }

    @Test
    @Config(sdk = [32])
    fun `playback notification update remains available before runtime permission exists`() {
        shadowOf(context.applicationContext as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            true,
            postPlaybackNotificationIfAllowed(context, 49021, Notification()),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `playback notification update is skipped when runtime permission is denied`() {
        shadowOf(context.applicationContext as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            false,
            postPlaybackNotificationIfAllowed(context, 49021, Notification()),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `playback notification update posts when runtime permission is granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(
            true,
            postPlaybackNotificationIfAllowed(context, 49021, Notification()),
        )
    }
}
