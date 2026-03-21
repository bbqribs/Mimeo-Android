package com.mimeo.android.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.ContextCompat

class PlaybackMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        val action = when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlaybackService.ACTION_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackService.ACTION_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> PlaybackService.ACTION_TOGGLE_PLAY_PAUSE
            else -> null
        } ?: return

        val serviceIntent = Intent(context, PlaybackService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

