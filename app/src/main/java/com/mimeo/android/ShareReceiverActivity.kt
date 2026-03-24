package com.mimeo.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.mimeo.android.share.appendOriginalArticleFooter
import com.mimeo.android.share.buildManualTextSourcePayload
import com.mimeo.android.share.buildPlainTextShareSyntheticUrl
import com.mimeo.android.share.derivePlainTextShareTitle
import com.mimeo.android.share.derivePlainTextSourceUrl
import com.mimeo.android.share.extractFirstHttpUrl
import com.mimeo.android.share.extractPlainTextShareBody
import com.mimeo.android.share.isAutoRetryEligiblePendingSaveResult
import com.mimeo.android.share.isRetryablePendingSaveResult
import com.mimeo.android.share.removeSharedUrlFromText
import com.mimeo.android.share.shouldTreatShareAsUrlCapture
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
            val normalizedUrl = extractFirstHttpUrl(sharedText)
            val useUrlCapture = shouldTreatShareAsUrlCapture(sharedText = sharedText, extractedUrl = normalizedUrl)
            val plainTextSourceUrl = if (!useUrlCapture) {
                derivePlainTextSourceUrl(sharedText = sharedText, extractedUrl = normalizedUrl)
            } else {
                null
            }
            val plainTextBody = if (!useUrlCapture) {
                extractPlainTextShareBody(sharedText)
                    ?.let { body ->
                        val withoutInlineUrl = if (normalizedUrl != null) removeSharedUrlFromText(body, normalizedUrl) else body
                        appendOriginalArticleFooter(withoutInlineUrl, plainTextSourceUrl)
                    }
            } else {
                null
            }
            val plainTextTitle = plainTextBody?.let { derivePlainTextShareTitle(sharedTitle = sharedTitle, plainTextBody = it) }
            val plainTextUrlInput = if (plainTextBody != null && plainTextTitle != null) {
                plainTextSourceUrl ?: buildPlainTextShareSyntheticUrl(
                    title = plainTextTitle,
                    plainTextBody = plainTextBody,
                )
            } else {
                null
            }
            val sourceAppPackage = deriveSourceAppPackage(incomingIntent)
            val sourceAppLabel = deriveSourceAppLabel(sourceAppPackage)
            val forceAppSource = sourceAppPackage != null && !isLikelyBrowserPackage(sourceAppPackage)
            val plainTextSourceMetadata = if (plainTextBody != null && plainTextUrlInput != null) {
                buildManualTextSourcePayload(
                    urlInput = plainTextUrlInput,
                    explicitSourceUrl = plainTextSourceUrl,
                    sourceAppPackage = sourceAppPackage,
                    sourceAppLabel = sourceAppLabel,
                    forceAppSource = forceAppSource,
                    captureKind = "shared_excerpt",
                )
            } else {
                null
            }
            if (useUrlCapture && normalizedUrl != null) {
                settingsStore.enqueuePendingManualSave(
                    source = PendingSaveSource.SHARE,
                    type = PendingManualSaveType.URL,
                    urlInput = normalizedUrl,
                    titleInput = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                    bodyInput = null,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                    lastFailureMessage = "Saving...",
                    autoRetryEligible = false,
                    incrementRetryCount = false,
                )
            } else if (plainTextBody != null && plainTextUrlInput != null) {
                settingsStore.enqueuePendingManualSave(
                    source = PendingSaveSource.SHARE,
                    type = PendingManualSaveType.TEXT,
                    urlInput = plainTextUrlInput,
                    titleInput = plainTextTitle,
                    bodyInput = plainTextBody,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                    lastFailureMessage = "Saving...",
                    autoRetryEligible = false,
                    incrementRetryCount = false,
                )
            }
            val result = when {
                useUrlCapture && normalizedUrl != null -> ShareSaveCoordinator(applicationContext).saveSharedText(
                    sharedText = sharedText,
                    sharedTitle = sharedTitle,
                )
                plainTextBody != null && plainTextUrlInput != null -> ShareSaveCoordinator(applicationContext).saveManualText(
                    urlInput = plainTextUrlInput,
                    titleInput = plainTextTitle,
                    bodyInput = plainTextBody,
                    sourceMetadata = plainTextSourceMetadata,
                )
                else -> ShareSaveResult.SaveFailed
            }
            var surfacedResult: ShareSaveResult? = result
            if (result is ShareSaveResult.Saved && result.itemId != null && useUrlCapture && normalizedUrl != null) {
                settingsStore.markMatchingPendingManualSaveResolved(
                    source = PendingSaveSource.SHARE,
                    type = PendingManualSaveType.URL,
                    urlInput = normalizedUrl,
                    titleInput = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                    bodyInput = null,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                    resolvedItemId = result.itemId,
                    statusMessage = "Processing...",
                )
            } else if (result is ShareSaveResult.Saved && result.itemId != null && plainTextBody != null && plainTextUrlInput != null) {
                settingsStore.markMatchingPendingManualSaveResolved(
                    source = PendingSaveSource.SHARE,
                    type = PendingManualSaveType.TEXT,
                    urlInput = plainTextUrlInput,
                    titleInput = plainTextTitle,
                    bodyInput = plainTextBody,
                    destinationPlaylistId = settings.defaultSavePlaylistId,
                    resolvedItemId = result.itemId,
                    statusMessage = "Processing...",
                )
            }
            if (isRetryablePendingSaveResult(result)) {
                if (useUrlCapture && normalizedUrl != null) {
                    settingsStore.enqueuePendingManualSave(
                        source = PendingSaveSource.SHARE,
                        type = PendingManualSaveType.URL,
                        urlInput = normalizedUrl,
                        titleInput = sharedTitle?.trim()?.takeIf { it.isNotEmpty() },
                        bodyInput = null,
                        destinationPlaylistId = settings.defaultSavePlaylistId,
                        lastFailureMessage = result.notificationText,
                        autoRetryEligible = isAutoRetryEligiblePendingSaveResult(result),
                        incrementRetryCount = false,
                    )
                    surfacedResult = null
                } else if (plainTextBody != null && plainTextUrlInput != null) {
                    settingsStore.enqueuePendingManualSave(
                        source = PendingSaveSource.SHARE,
                        type = PendingManualSaveType.TEXT,
                        urlInput = plainTextUrlInput,
                        titleInput = plainTextTitle,
                        bodyInput = plainTextBody,
                        destinationPlaylistId = settings.defaultSavePlaylistId,
                        lastFailureMessage = result.notificationText,
                        autoRetryEligible = isAutoRetryEligiblePendingSaveResult(result),
                        incrementRetryCount = false,
                    )
                    surfacedResult = null
                }
            }
            if (surfacedResult != null) {
                notifications.post(surfacedResult, notificationId)
            }
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

private fun deriveSourceAppPackage(intent: Intent): String? {
    val referrerUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
            ?: runCatching {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_REFERRER) as? Uri
            }.getOrNull()
    } else {
        runCatching {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_REFERRER) as? Uri
        }.getOrNull()
    }
    val fromReferrerUri = referrerUri
        ?.takeIf { it.scheme.equals("android-app", ignoreCase = true) }
        ?.host
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (fromReferrerUri != null) return fromReferrerUri
    val fromReferrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { name ->
            if (name.startsWith("android-app://", ignoreCase = true)) {
                runCatching { Uri.parse(name).host }.getOrNull()
            } else {
                name
            }
        }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return fromReferrerName
}

private fun ShareReceiverActivity.deriveSourceAppLabel(sourceAppPackage: String?): String? {
    val packageName = sourceAppPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo)?.toString()?.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

private fun isLikelyBrowserPackage(packageName: String): Boolean {
    val normalized = packageName.lowercase()
    if (normalized.contains("chrome")) return true
    if (normalized.contains("firefox")) return true
    if (normalized.contains("brave")) return true
    if (normalized.contains("opera")) return true
    if (normalized.contains("edge")) return true
    if (normalized.contains("browser")) return true
    if (normalized.contains("samsung.android.app.sbrowser")) return true
    return false
}
