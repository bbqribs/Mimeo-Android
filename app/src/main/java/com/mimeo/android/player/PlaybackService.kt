package com.mimeo.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media.session.MediaButtonReceiver.handleIntent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.mimeo.android.MainActivity

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    private val mediaButtonLogTag = "MimeoMediaButton"

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
    private var mediaButtonAnchorTrack: AudioTrack? = null

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
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            setMediaButtonReceiver(
                mediaButtonServicePendingIntent(),
            )
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() = dispatchPlay()
                    override fun onPause() = dispatchPause()
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                        val handled = handleMediaButtonIntent(mediaButtonEvent)
                        Log.d(mediaButtonLogTag, "sessionCallback onMediaButtonEvent handled=$handled")
                        return handled
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
        Log.d(mediaButtonLogTag, "service onStart action=${intent?.action}")
        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> {
                if (!handleMediaButtonIntent(intent)) {
                    Log.d(mediaButtonLogTag, "service onStart fallback->MediaButtonReceiver.handleIntent")
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
            }
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
        releaseMediaButtonAnchor()
        mediaSession.release()
        super.onDestroy()
    }

    private fun dispatchPlay() {
        Log.d(mediaButtonLogTag, "dispatchPlay")
        requestAudioFocus()
        PlaybackServiceBridge.onPlay?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
    }

    private fun dispatchPause() {
        Log.d(mediaButtonLogTag, "dispatchPause")
        PlaybackServiceBridge.onPause?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        abandonAudioFocus()
    }

    private fun dispatchToggle() {
        Log.d(mediaButtonLogTag, "dispatchToggle isPlaying=${snapshot.isPlaying}")
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
        mediaSession.isActive = next.itemId != null
        // Keep media-button ownership stable for the currently loaded item.
        // Relying on per-utterance speaking state causes focus/register churn and
        // allows other media apps to reclaim headset button handling mid-playback.
        if (next.itemId != null && next.isPlaying) {
            requestAudioFocus()
        } else if (next.itemId == null) {
            abandonAudioFocus()
        }
        val state = if (next.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE,
                )
                .setActiveQueueItemId(next.itemId?.toLong() ?: -1L)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, next.title.ifBlank { "Mimeo playback" })
                .build(),
        )

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
            abandonAudioFocus()
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
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
        Log.d(mediaButtonLogTag, "requestAudioFocus hasAudioFocus=$hasAudioFocus")
        if (hasAudioFocus) {
            startMediaButtonAnchor()
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
        Log.d(mediaButtonLogTag, "abandonAudioFocus")
        hasAudioFocus = false
        stopMediaButtonAnchor()
    }

    private fun startMediaButtonAnchor() {
        val track = mediaButtonAnchorTrack ?: createMediaButtonAnchorTrack()?.also {
            mediaButtonAnchorTrack = it
        } ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) return
        try {
            track.play()
            Log.d(mediaButtonLogTag, "mediaButtonAnchor play")
        } catch (_: IllegalStateException) {
            releaseMediaButtonAnchor()
        }
    }

    private fun stopMediaButtonAnchor() {
        val track = mediaButtonAnchorTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
        try {
            track.pause()
            track.flush()
            Log.d(mediaButtonLogTag, "mediaButtonAnchor stop")
        } catch (_: IllegalStateException) {
            releaseMediaButtonAnchor()
        }
    }

    private fun releaseMediaButtonAnchor() {
        mediaButtonAnchorTrack?.let { track ->
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
        mediaButtonAnchorTrack = null
    }

    private fun createMediaButtonAnchorTrack(): AudioTrack? {
        return try {
            val sampleRate = 8_000
            val pcm = ByteArray(sampleRate * 2) // 1 second, 16-bit mono silence
            val frameCount = pcm.size / 2
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                pcm.size,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            val written = track.write(pcm, 0, pcm.size)
            if (written > 0) {
                val writtenFrames = written / 2
                if (writtenFrames > 1) {
                    track.setLoopPoints(0, writtenFrames, -1)
                }
            }
            track.setVolume(0f)
            track
        } catch (_: Throwable) {
            null
        }
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

    private fun mediaButtonServicePendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(this, PlaybackMediaButtonReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0x4D42, // "MB"
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun handleMediaButtonIntent(intent: Intent?): Boolean {
        val keyEvent = intent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return true
        Log.d(mediaButtonLogTag, "handleMediaButtonIntent key=${keyEvent.keyCode}")
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PAUSE -> dispatchPause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> dispatchPlay()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> dispatchToggle()
            else -> return false
        }
        Log.d(mediaButtonLogTag, "handleMediaButtonIntent dispatched")
        return true
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
