package com.mimeo.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.mimeo.android.model.PendingManualSaveType
import com.mimeo.android.model.PendingSaveSource
import com.mimeo.android.share.ShareSaveCoordinator
import com.mimeo.android.share.ShareSaveResult
import com.mimeo.android.share.extractFirstHttpUrl
import com.mimeo.android.share.isAutoRetryEligiblePendingSaveResult
import com.mimeo.android.share.isRetryablePendingSaveResult
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
            val notifications = ShareResultNotifications(applicationContext)
            val notificationId = notifications.postAccepted()
            val settingsStore = SettingsStore(applicationContext)
            val settings = settingsStore.settingsFlow.first()
            val result = ShareSaveCoordinator(applicationContext).saveSharedText(
                sharedText = sharedText,
                sharedTitle = sharedTitle,
            )
            var surfacedResult = result
            if (isRetryablePendingSaveResult(result)) {
                val normalizedUrl = extractFirstHttpUrl(sharedText)
                if (normalizedUrl != null) {
                    settingsStore.enqueuePendingManualSave(
                        source = PendingSaveSource.SHARE,
                        type = PendingManualSaveType.URL,
                        urlInput = normalizedUrl,
                        titleInput = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                        bodyInput = null,
                        destinationPlaylistId = settings.defaultSavePlaylistId,
                        lastFailureMessage = result.notificationText,
                        autoRetryEligible = isAutoRetryEligiblePendingSaveResult(result),
                    )
                    surfacedResult = ShareSaveResult.PendingQueued
                }
            }
            notifications.post(surfacedResult, notificationId)
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
    suspend fun postAccepted(): Int? {
        if (!canPostNotifications()) return null
        ensureChannel()
        val notificationId = nextNotificationId()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.msr_view_list_24)
            .setLargeIcon(buildBrandLargeIcon())
            .setContentTitle("Mimeo")
            .setContentText("Received. Saving...")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Received. Saving..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(3_000L)
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return notificationId
    }

    suspend fun post(result: ShareSaveResult, notificationId: Int? = null) {
        if (!canPostNotifications()) return

        val settings = SettingsStore(context.applicationContext).settingsFlow.first()
        ensureChannel()
        val largeIcon = buildBrandLargeIcon()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.msr_view_list_24)
            .setLargeIcon(largeIcon)
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

        NotificationManagerCompat.from(context).notify(notificationId ?: nextNotificationId(), builder.build())
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

    private fun buildBrandLargeIcon(): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.drawable.msr_view_list_24) ?: return null
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 72
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 72
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val CHANNEL_ID = "share_saving_heads_up"
        private val notificationIds = AtomicInteger(2400)
    }
}
