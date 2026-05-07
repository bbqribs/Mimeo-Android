package com.mimeo.android.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented smoke tests for [PlaybackService].
 *
 * These tests verify that the service binds, accepts snapshots through its binder,
 * and does not crash on state transitions. They intentionally avoid triggering
 * foreground service startup (by using itemId=null for idle snapshots) to stay
 * portable across emulator configurations.
 *
 * --------------------------------------------------------------------------
 * MANUAL ADB VERIFICATION — Headset / media button routing
 * --------------------------------------------------------------------------
 * Full headset-control simulation (physical earphone button) is not feasible
 * in standard Android instrumentation. To verify that Mimeo owns media button
 * events and headset controls do not fall through to YouTube or other apps:
 *
 * 1. Start playback in Mimeo (at least one article queued).
 * 2. Connect wired headset or Bluetooth headphones.
 * 3. Confirm Mimeo is the active media session:
 *      adb shell dumpsys media_session | grep -A 10 "MimeoPlayback"
 *    Expected: session flagged "active", state PLAYING.
 *
 * 4. Press the headset play/pause button once.
 *    Expected: Mimeo pauses (not YouTube or another player).
 *
 * 5. Press again — Mimeo resumes.
 *
 * 6. Open YouTube, start a video, then return to Mimeo and resume playback.
 *    Expected: Mimeo reclaims audio focus; headset button controls Mimeo.
 *
 * 7. Verify the anchor AudioTrack is active (keeps media-button routing):
 *      adb shell dumpsys audio | grep -A 5 "com.mimeo.android"
 *    Expected: STREAM_MUSIC output registered while Mimeo holds audio focus.
 * --------------------------------------------------------------------------
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceSmokeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun service_binds_and_returns_non_null_binder() {
        val latch = CountDownLatch(1)
        var receivedBinder: IBinder? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                receivedBinder = service
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        val bound = context.bindService(
            Intent(context, PlaybackService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        assertTrue("bindService must return true for a declared service", bound)
        assertTrue("service must connect within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("binder must be non-null after connection", receivedBinder)

        context.unbindService(connection)
    }

    @Test
    fun service_accepts_idle_snapshot_without_crash() {
        // itemId=null means no foreground service is started, keeping the test self-contained.
        val latch = CountDownLatch(1)
        var localBinder: PlaybackService.LocalBinder? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                localBinder = service as PlaybackService.LocalBinder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        context.bindService(
            Intent(context, PlaybackService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("service bound within 5 s", latch.await(5, TimeUnit.SECONDS))

        try {
            localBinder?.updateSnapshot(PlaybackServiceSnapshot(itemId = null, title = "", isPlaying = false))
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun service_accepts_paused_active_snapshot_without_crash() {
        // itemId non-null, isPlaying=false — service creates notification and session,
        // but does not request audio focus (no anchor started for paused-on-bind path).
        val latch = CountDownLatch(1)
        var localBinder: PlaybackService.LocalBinder? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                localBinder = service as PlaybackService.LocalBinder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }

        context.bindService(
            Intent(context, PlaybackService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("service bound within 5 s", latch.await(5, TimeUnit.SECONDS))

        try {
            localBinder?.updateSnapshot(
                PlaybackServiceSnapshot(itemId = 1, title = "Smoke Test Article", isPlaying = false),
            )
        } finally {
            // Reset to idle before unbinding so the service stops cleanly.
            localBinder?.updateSnapshot(PlaybackServiceSnapshot(itemId = null))
            context.unbindService(connection)
        }
    }
}
