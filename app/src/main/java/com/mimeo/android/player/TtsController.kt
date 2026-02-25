package com.mimeo.android.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsController(
    context: Context,
    private val onSegmentDone: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private val tts: TextToSpeech

    init {
        lateinit var createdEngine: TextToSpeech
        createdEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                createdEngine.language = Locale.US
            } else {
                mainHandler.post { onError("TTS init failed") }
            }
        }
        tts = createdEngine

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.postDelayed({ onSegmentDone() }, 300L)
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post { onError("TTS error") }
            }
        })
    }

    fun speakSegment(itemId: Int, segmentIndex: Int, text: String) {
        if (!initialized || text.isBlank()) return
        val utteranceId = "mimeo-item-$itemId-seg-$segmentIndex-${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
