package com.mimeo.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.share.ShareSaveCoordinator
import com.mimeo.android.share.ShareSaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != Intent.ACTION_SEND) {
            finishSilently()
            return
        }
        val mimeType = incomingIntent.type.orEmpty()
        if (!mimeType.startsWith("text/")) {
            finishSilently()
            return
        }

        val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedTitle = incomingIntent.getStringExtra(Intent.EXTRA_SUBJECT)
        backgroundScope.launch {
            val result = ShareSaveCoordinator(applicationContext).saveSharedText(
                sharedText = sharedText,
                sharedTitle = sharedTitle,
            )
            ShareResultNotifications(applicationContext).post(result)
        }

        setIntent(
            Intent(incomingIntent).apply {
                action = null
                type = null
                removeExtra(Intent.EXTRA_TEXT)
                removeExtra(Intent.EXTRA_SUBJECT)
            }
        )
        finishSilently()
    }

    private fun finishSilently() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.mimeo.android.action.OPEN_SETTINGS"

        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private class ShareResultNotifications(
    private val context: Context,
) {
    suspend fun post(result: ShareSaveResult) {
        if (!canPostNotifications()) return

        val settings = SettingsStore(context.applicationContext).settingsFlow.first()
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.msr_check_circle_24)
            .setContentTitle(result.notificationTitle)
            .setContentText(result.notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)

        if (!settings.keepShareResultNotifications) {
            builder.setTimeoutAfter(4_000L)
        }

        if (result.opensSettings) {
            builder.addAction(
                0,
                "Open Settings",
                PendingIntent.getActivity(
                    context,
                    1001,
                    Intent(context, MainActivity::class.java).apply {
                        action = ShareReceiverActivity.ACTION_OPEN_SETTINGS
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutableFlag(),
                ),
            )
        }

        NotificationManagerCompat.from(context).notify(nextNotificationId(), builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Saving",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Share-sheet save results"
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntentMutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun nextNotificationId(): Int = notificationIds.incrementAndGet()

    companion object {
        private const val CHANNEL_ID = "share_saving_heads_up"
        private val notificationIds = AtomicInteger(2400)
    }
}
