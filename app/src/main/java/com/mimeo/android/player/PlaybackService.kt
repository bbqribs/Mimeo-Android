package com.mimeo.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
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
    private val interruptionPolicy = AudioInterruptionPolicy()
    private val auditTrail = PlaybackServiceAuditTrail()

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
    private var noisyReceiverRegistered = false
    private val becomingNoisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
                Log.d(mediaButtonLogTag, "becomingNoisy")
                handleInterruptionAction(interruptionPolicy.onBecomingNoisy(snapshot.isPlaying))
            }
        }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ensureNotificationChannel()
        registerNoisyReceiverIfNeeded()
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
        emitAudit("serviceCreate")
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
            ACTION_PLAY, ACTION_PAUSE, ACTION_TOGGLE_PLAY_PAUSE -> {
                // Refresh snapshot before dispatching so we act on current playback state,
                // not the last-pushed snapshot (which may be stale for between-chunk gaps).
                PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
                when (intent.action) {
                    ACTION_PLAY -> dispatchPlay()
                    ACTION_PAUSE -> dispatchPause()
                    ACTION_TOGGLE_PLAY_PAUSE -> dispatchToggle()
                }
            }
            ACTION_SYNC_FROM_BRIDGE -> {
                PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
            }
        }
        emitAudit("serviceStart:${intent?.action ?: "none"}")
        return START_STICKY
    }

    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(mediaButtonLogTag, "onAudioFocusChange focusChange=$focusChange")
        val action =
            interruptionPolicy.onAudioFocusChange(
                focusChange = focusChange,
                isCurrentlyPlaying = snapshot.isPlaying,
                hasLoadedItem = snapshot.itemId != null,
            )
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (snapshot.itemId != null) {
                    startMediaButtonAnchor()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                // Both paths use PauseKeepFocus: we pause TTS but keep the OS focus
                // registration so AUDIOFOCUS_GAIN is delivered when the interruption ends
                // and auto-resume fires. hasAudioFocus remains true — a user-triggered
                // resume before GAIN re-anchors via requestAudioFocus() without creating
                // a second registered request.
                stopMediaButtonAnchor()
            }
        }
        handleInterruptionAction(action)
        emitAudit("audioFocus:$focusChange")
    }

    override fun onDestroy() {
        unregisterNoisyReceiverIfNeeded()
        interruptionPolicy.clearResumeExpectation()
        abandonAudioFocusNow()
        releaseMediaButtonAnchor()
        mediaSession.release()
        emitAudit("serviceDestroy")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        emitAudit("taskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    private fun dispatchPlay() {
        Log.d(mediaButtonLogTag, "dispatchPlay")
        interruptionPolicy.clearResumeExpectation()
        if (!requestAudioFocus()) {
            emitAudit("dispatchPlay:focusDenied")
            return
        }
        PlaybackServiceBridge.onPlay?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        emitAudit("dispatchPlay")
    }

    private fun dispatchPause(
        releaseAudioFocusImmediately: Boolean = false,
        clearResumeExpectation: Boolean = true,
    ) {
        Log.d(mediaButtonLogTag, "dispatchPause")
        if (clearResumeExpectation) {
            interruptionPolicy.clearResumeExpectation()
        }
        PlaybackServiceBridge.onPause?.invoke()
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        if (releaseAudioFocusImmediately) {
            abandonAudioFocusNow()
        }
        emitAudit("dispatchPause")
    }

    private fun dispatchToggle() {
        Log.d(mediaButtonLogTag, "dispatchToggle isPlaying=${snapshot.isPlaying}")
        // Use the snapshot state (refreshed immediately before dispatch) rather than re-reading
        // engine state via onTogglePlayPause. Engine state can briefly read isSpeaking=false
        // between TTS chunks while the user still considers playback active, causing play to
        // fire instead of pause on a single earphone press.
        if (snapshot.isPlaying) dispatchPause() else dispatchPlay()
        emitAudit("dispatchToggle")
    }

    private fun updateSnapshot(next: PlaybackServiceSnapshot) {
        snapshot = next
        mediaSession.isActive = next.itemId != null
        if (next.itemId != null && next.isPlaying) {
            requestAudioFocus()
        } else if (next.itemId != null && hasAudioFocus) {
            // Paused but still holding audio focus: keep anchor running so the OS continues
            // routing media button events (earphone controls) to this session. The anchor is
            // a no-op if already playing; explicit restart here guards against anchor stopping
            // due to an exception while paused (which would otherwise go undetected until play).
            startMediaButtonAnchor()
        } else if (next.itemId == null) {
            interruptionPolicy.clearResumeExpectation()
            abandonAudioFocusNow()
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
                emitAudit("foregroundStart")
            } else {
                NotificationManagerCompat.from(this).notify(notificationId, notification)
            }
        } else if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            emitAudit("foregroundStop")
            abandonAudioFocusNow()
            stopSelf()
        }
        emitAudit("snapshot")
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

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) {
            // Already hold OS focus (e.g. user-resumed during a transient interruption
            // before AUDIOFOCUS_GAIN arrived). Re-anchor so media buttons stay routed here.
            startMediaButtonAnchor()
            return true
        }
        val manager = audioManager ?: return false
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
                .setWillPauseWhenDucked(true)
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
        return hasAudioFocus
    }

    private fun abandonAudioFocusNow() {
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
        val staleBeforeRefresh = snapshot.isPlaying
        PlaybackServiceBridge.snapshotProvider?.invoke()?.let(::updateSnapshot)
        if (staleBeforeRefresh != snapshot.isPlaying) {
            emitAudit("mediaButtonRefresh:playing:$staleBeforeRefresh->${snapshot.isPlaying}")
        }
        Log.d(mediaButtonLogTag, "handleMediaButtonIntent key=${keyEvent.keyCode}")
        when (resolveMediaButtonDispatchAction(keyCode = keyEvent.keyCode, isCurrentlyPlaying = snapshot.isPlaying)) {
            MediaButtonDispatchAction.Play -> dispatchPlay()
            MediaButtonDispatchAction.Pause -> dispatchPause()
            MediaButtonDispatchAction.Toggle -> dispatchToggle()
            MediaButtonDispatchAction.None -> return false
        }
        Log.d(mediaButtonLogTag, "handleMediaButtonIntent dispatched")
        emitAudit("mediaButton:${keyEvent.keyCode}")
        return true
    }

    private fun emitAudit(event: String) {
        val deviceInteractive = isDeviceInteractive()
        val deviceLocked = isDeviceLocked()
        val appInBackground = isAppInBackground()
        val current = PlaybackAuditState(
            itemId = snapshot.itemId,
            isPlaying = snapshot.isPlaying,
            hasAudioFocus = hasAudioFocus,
            mediaSessionActive = if (::mediaSession.isInitialized) mediaSession.isActive else false,
            isForeground = isForeground,
            anchorPlaying = mediaButtonAnchorTrack?.playState == AudioTrack.PLAYSTATE_PLAYING,
            isDeviceInteractive = deviceInteractive,
            isDeviceLocked = deviceLocked,
            appInBackground = appInBackground,
        )
        val entry = auditTrail.capture(
            event = event,
            state = current,
            nowMs = SystemClock.elapsedRealtime(),
        ) ?: return
        Log.d(mediaButtonLogTag, formatPlaybackAuditEntry(entry))
    }

    private fun isDeviceInteractive(): Boolean {
        val manager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            manager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            manager.isScreenOn
        }
    }

    private fun isDeviceLocked(): Boolean {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return manager.isKeyguardLocked
    }

    private fun isAppInBackground(): Boolean {
        val processState = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processState)
        return processState.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
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

    private fun registerNoisyReceiverIfNeeded() {
        if (noisyReceiverRegistered) return
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiverIfNeeded() {
        if (!noisyReceiverRegistered) return
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
        }
        noisyReceiverRegistered = false
    }

    private fun handleInterruptionAction(action: AudioInterruptionAction) {
        when (action) {
            AudioInterruptionAction.None -> Unit
            AudioInterruptionAction.PauseReleaseFocus ->
                dispatchPause(releaseAudioFocusImmediately = true, clearResumeExpectation = false)
            AudioInterruptionAction.PauseKeepFocus ->
                dispatchPause(releaseAudioFocusImmediately = false, clearResumeExpectation = false)
            AudioInterruptionAction.ResumePlayback -> {
                Log.d(mediaButtonLogTag, "autoResumeAfterTransientGain")
                dispatchPlay()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "mimeo_playback"
        const val ACTION_PLAY = "com.mimeo.android.player.PLAY"
        const val ACTION_PAUSE = "com.mimeo.android.player.PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.mimeo.android.player.TOGGLE_PLAY_PAUSE"
        const val ACTION_SYNC_FROM_BRIDGE = "com.mimeo.android.player.SYNC_FROM_BRIDGE"
    }
}
