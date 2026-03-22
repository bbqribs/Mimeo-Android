package com.mimeo.android.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
const val TITLE_INTRO_CHUNK_INDEX = -1

data class TtsChunkProgressEvent(
    val utteranceId: String,
    val itemId: Int,
    val chunkIndex: Int,
    val absoluteOffsetInChunk: Int,
    val activeRangeInChunk: IntRange? = null,
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
        val chunkTextLength: Int,
        val generation: Long,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val tts: TextToSpeech
    private val generationCounter = AtomicLong(0L)
    private val utteranceMetaById = ConcurrentHashMap<String, UtteranceMeta>()
    private val handledDoneUtterances = CopyOnWriteArraySet<String>()
    private var speechRate = 1.0f
    private var defaultVoiceName: String? = null
    private var preferredVoiceName: String? = null
    private var selectedVoiceRequiresNetwork = false
    private var completionCueToneGenerator: ToneGenerator? = null

    init {
        lateinit var createdEngine: TextToSpeech
        createdEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    createdEngine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                }
                createdEngine.language = Locale.US
                createdEngine.setSpeechRate(speechRate)
                defaultVoiceName = createdEngine.defaultVoice?.name?.trim()?.ifBlank { null }
                applyPreferredOrDefaultVoice()
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
                val activeRange = normalizeActiveChunkRange(
                    textLength = meta.chunkTextLength,
                    baseOffset = meta.baseOffset,
                    start = start,
                    endExclusive = end,
                )
                if (DEBUG_PLAYBACK) {
                    println(
                        "[Mimeo][tts] onRange utteranceId=$id item=${meta.itemId} chunk=${meta.chunkIndex} " +
                            "offset=$absoluteInChunk range=$activeRange",
                    )
                }
                mainHandler.post {
                    onChunkProgress(
                        TtsChunkProgressEvent(
                            utteranceId = id,
                            itemId = meta.itemId,
                            chunkIndex = meta.chunkIndex,
                            absoluteOffsetInChunk = absoluteInChunk,
                            activeRangeInChunk = activeRange,
                        ),
                    )
                }
            }
        })
    }

    fun speakChunk(itemId: Int, chunkIndex: Int, text: String, baseOffset: Int) {
        if (!initialized || text.isBlank()) return
        applyPreferredOrDefaultVoice()
        tts.setSpeechRate(speechRate)
        val generation = generationCounter.incrementAndGet()
        val utteranceId = "mimeo-item-$itemId-chunk-$chunkIndex-$generation"
        handledDoneUtterances.remove(utteranceId)
        utteranceMetaById[utteranceId] = UtteranceMeta(
            itemId = itemId,
            chunkIndex = chunkIndex,
            baseOffset = baseOffset.coerceAtLeast(0),
            chunkTextLength = (baseOffset.coerceAtLeast(0) + text.length).coerceAtLeast(0),
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

    fun speakTitleIntro(itemId: Int, titleText: String) {
        val clean = titleText.trim()
        if (!initialized || clean.isBlank()) return
        applyPreferredOrDefaultVoice()
        tts.setSpeechRate(speechRate)
        val generation = generationCounter.incrementAndGet()
        val utteranceId = "mimeo-item-$itemId-title-intro-$generation"
        handledDoneUtterances.remove(utteranceId)
        utteranceMetaById[utteranceId] = UtteranceMeta(
            itemId = itemId,
            chunkIndex = TITLE_INTRO_CHUNK_INDEX,
            baseOffset = 0,
            chunkTextLength = clean.length.coerceAtLeast(0),
            generation = generation,
        )
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.8f, 2.0f)
        if (initialized) {
            tts.setSpeechRate(speechRate)
        }
    }

    fun setVoiceName(voiceName: String) {
        preferredVoiceName = voiceName.trim().ifBlank { null }
        if (initialized) {
            applyPreferredOrDefaultVoice()
        }
    }

    fun stop() {
        generationCounter.incrementAndGet()
        utteranceMetaById.clear()
        handledDoneUtterances.clear()
        tts.stop()
    }

    fun playCompletionCue() {
        if (!initialized) return
        val tone = completionCueToneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also {
            completionCueToneGenerator = it
        }
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 240)
    }

    fun shutdown() {
        stop()
        completionCueToneGenerator?.release()
        completionCueToneGenerator = null
        tts.shutdown()
    }

    private fun applyPreferredOrDefaultVoice() {
        val voices = tts.voices.orEmpty()
        val preferred = preferredVoiceName
        if (!preferred.isNullOrBlank()) {
            val matchingVoice = voices.firstOrNull { voice ->
                voice.name.equals(preferred, ignoreCase = true)
            }
            if (matchingVoice != null) {
                tts.voice = matchingVoice
                preferredVoiceName = matchingVoice.name
                selectedVoiceRequiresNetwork = matchingVoice.isNetworkConnectionRequired
                if (selectedVoiceRequiresNetwork && !hasInternetConnection()) {
                    applyDefaultVoiceIfAvailable(voices)
                }
                return
            }
        }
        applyDefaultVoiceIfAvailable(voices)
    }

    private fun applyDefaultVoiceIfAvailable(voices: Set<android.speech.tts.Voice>) {
        selectedVoiceRequiresNetwork = false
        val defaultName = defaultVoiceName
        if (defaultName.isNullOrBlank()) return
        val defaultVoice = voices.firstOrNull { voice ->
            voice.name.equals(defaultName, ignoreCase = true)
        } ?: return
        tts.voice = defaultVoice
    }

    private fun hasInternetConnection(): Boolean {
        val manager = connectivityManager ?: return false
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
