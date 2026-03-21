package com.mimeo.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media.session.MediaButtonReceiver.handleIntent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.mimeo.android.MainActivity

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    inner class LocalBinder : Binder() {
        fun updateSnapshot(snapshot: PlaybackServiceSnapshot) {
            this@PlaybackService.updateSnapshot(snapshot)
        }
    }

    private val binder = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private val notificationId = 49021
    private var isForeground = false
    private var snapshot = PlaybackServiceSnapshot()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ensureNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MimeoPlayback").apply {
            isActive = true
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setMediaButtonReceiver(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@PlaybackService,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE,
                ),
            )
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = dispatchPlay()
                    override fun onPause() = dispatchPause()
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                        handleIntent(this@apply, mediaButtonEvent)
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                },
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE,
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                    .build(),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> dispatchPlay()
            ACTION_PAUSE -> dispatchPause()
            ACTION_TOGGLE_PLAY_PAUSE -> dispatchToggle()
            ACTION_SYNC_FROM_BRIDGE -> {
                PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
            }
        }
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> dispatchPause()
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        mediaSession.release()
        super.onDestroy()
    }

    private fun dispatchPlay() {
        requestAudioFocus()
        PlaybackServiceBridge.onPlay?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
    }

    private fun dispatchPause() {
        PlaybackServiceBridge.onPause?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        abandonAudioFocus()
    }

    private fun dispatchToggle() {
        val toggled = PlaybackServiceBridge.onTogglePlayPause
        if (toggled != null) {
            toggled.invoke()
            PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        } else {
            if (snapshot.isPlaying) dispatchPause() else dispatchPlay()
        }
    }

    private fun updateSnapshot(next: PlaybackServiceSnapshot) {
        snapshot = next
        val state = if (next.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        mediaSession.setMetadata(null)

        val notification = buildNotification(next)
        if (next.itemId != null) {
            if (!isForeground) {
                startForeground(notificationId, notification)
                isForeground = true
            } else {
                NotificationManagerCompat.from(this).notify(notificationId, notification)
            }
        } else if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        }
    }

    private fun buildNotification(current: PlaybackServiceSnapshot): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPauseAction = if (current.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                servicePendingIntent(ACTION_PAUSE),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                servicePendingIntent(ACTION_PLAY),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(current.title.ifBlank { "Mimeo playback" })
            .setContentText(if (current.isPlaying) "Playing" else "Paused")
            .setOngoing(current.isPlaying)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(playPauseAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0),
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val manager = audioManager ?: return
        hasAudioFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(this)
        }
        hasAudioFocus = false
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mimeo playback controls"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "mimeo_playback"
        const val ACTION_PLAY = "com.mimeo.android.player.PLAY"
        const val ACTION_PAUSE = "com.mimeo.android.player.PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.mimeo.android.player.TOGGLE_PLAY_PAUSE"
        const val ACTION_SYNC_FROM_BRIDGE = "com.mimeo.android.player.SYNC_FROM_BRIDGE"
    }
}
