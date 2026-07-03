package com.mimeo.android.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

class PlaybackMediaButtonReceiver : BroadcastReceiver() {
    private val tag = "MimeoMediaButton"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        Log.d(tag, "receiver key=${keyEvent.keyCode} action=${keyEvent.action}")
        val action = playbackServiceActionForMediaButtonEvent(
            keyAction = keyEvent.action,
            keyCode = keyEvent.keyCode,
        ) ?: return
        Log.d(tag, "receiver dispatch serviceAction=$action")
        val serviceIntent = Intent(context, PlaybackService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

internal fun playbackServiceActionForMediaButtonEvent(
    keyAction: Int,
    keyCode: Int,
): String? {
    if (keyAction != KeyEvent.ACTION_DOWN) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        -> PlaybackService.ACTION_TOGGLE_PLAY_PAUSE
        else -> null
    }
}
