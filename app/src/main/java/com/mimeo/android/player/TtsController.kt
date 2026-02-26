package com.mimeo.android.player

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

private const val DEBUG_PLAYBACK = false

data class TtsChunkProgressEvent(
    val utteranceId: String,
    val itemId: Int,
    val chunkIndex: Int,
    val absoluteOffsetInChunk: Int,
)

data class TtsChunkDoneEvent(
    val utteranceId: String,
    val itemId: Int,
    val chunkIndex: Int,
)

class TtsController(
    context: Context,
    private val onChunkDone: (TtsChunkDoneEvent) -> Unit,
    private val onChunkProgress: (TtsChunkProgressEvent) -> Unit,
    private val onError: (String) -> Unit,
) {
    private data class UtteranceMeta(
        val itemId: Int,
        val chunkIndex: Int,
        val baseOffset: Int,
        val generation: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private val tts: TextToSpeech
    private val generationCounter = AtomicLong(0L)
    private val utteranceMetaById = ConcurrentHashMap<String, UtteranceMeta>()
    private val handledDoneUtterances = CopyOnWriteArraySet<String>()

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
            override fun onStart(utteranceId: String?) {
                if (DEBUG_PLAYBACK && utteranceId != null) {
                    println("[Mimeo][tts] onStart utteranceId=$utteranceId")
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                val meta = utteranceMetaById[id] ?: return
                if (!handledDoneUtterances.add(id)) {
                    if (DEBUG_PLAYBACK) {
                        println("[Mimeo][tts] onDone duplicate ignored utteranceId=$id chunk=${meta.chunkIndex}")
                    }
                    return
                }
                utteranceMetaById.remove(id)
                if (DEBUG_PLAYBACK) {
                    println("[Mimeo][tts] onDone utteranceId=$id item=${meta.itemId} chunk=${meta.chunkIndex}")
                }
                mainHandler.postDelayed({
                    onChunkDone(
                        TtsChunkDoneEvent(
                            utteranceId = id,
                            itemId = meta.itemId,
                            chunkIndex = meta.chunkIndex,
                        ),
                    )
                }, 250L)
            }

            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: "unknown"
                utteranceMetaById.remove(id)
                handledDoneUtterances.add(id)
                mainHandler.post { onError("TTS error") }
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    return
                }
                val id = utteranceId ?: return
                val meta = utteranceMetaById[id] ?: return
                val absoluteInChunk = (meta.baseOffset + start).coerceAtLeast(0)
                if (DEBUG_PLAYBACK) {
                    println(
                        "[Mimeo][tts] onRange utteranceId=$id item=${meta.itemId} chunk=${meta.chunkIndex} " +
                            "offset=$absoluteInChunk",
                    )
                }
                mainHandler.post {
                    onChunkProgress(
                        TtsChunkProgressEvent(
                            utteranceId = id,
                            itemId = meta.itemId,
                            chunkIndex = meta.chunkIndex,
                            absoluteOffsetInChunk = absoluteInChunk,
                        ),
                    )
                }
            }
        })
    }

    fun speakChunk(itemId: Int, chunkIndex: Int, text: String, baseOffset: Int) {
        if (!initialized || text.isBlank()) return
        val generation = generationCounter.incrementAndGet()
        val utteranceId = "mimeo-item-$itemId-chunk-$chunkIndex-$generation"
        handledDoneUtterances.remove(utteranceId)
        utteranceMetaById[utteranceId] = UtteranceMeta(
            itemId = itemId,
            chunkIndex = chunkIndex,
            baseOffset = baseOffset.coerceAtLeast(0),
            generation = generation,
        )
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        if (DEBUG_PLAYBACK) {
            println("[Mimeo][tts] speak utteranceId=$utteranceId item=$itemId chunk=$chunkIndex base=$baseOffset")
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        generationCounter.incrementAndGet()
        utteranceMetaById.clear()
        handledDoneUtterances.clear()
        tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
