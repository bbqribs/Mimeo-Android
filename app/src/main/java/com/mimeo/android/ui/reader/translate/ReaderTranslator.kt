package com.mimeo.android.ui.reader.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The outcome of a single translation request. */
sealed class TranslationResult {
    data class Success(
        val sourceLanguageTag: String,
        val translatedText: String,
    ) : TranslationResult()

    /** ML Kit's language identifier returned "und" (undetermined). */
    object UnknownSourceLanguage : TranslationResult()

    /** Source detected but ML Kit has no model for source<->target. */
    data class UnsupportedLanguagePair(
        val sourceLanguageTag: String,
        val targetLanguageTag: String,
    ) : TranslationResult()

    data class Error(val message: String) : TranslationResult()
}

/**
 * On-device translator backed by ML Kit. Detects the source language from the
 * selection, downloads the source<->target model on first use, and caches
 * Translator clients so subsequent translations for the same pair are instant.
 */
class ReaderTranslator {

    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<Pair<String, String>, Translator>()

    /** Translate [text] into [targetLanguageTag] (e.g. "en", "es", "ja"). */
    suspend fun translate(
        text: String,
        targetLanguageTag: String,
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return TranslationResult.Error("Nothing to translate.")
        val target = TranslateLanguage.fromLanguageTag(targetLanguageTag)
            ?: return TranslationResult.Error("Unsupported target language.")

        val sourceTag = try {
            languageIdentifier.identifyLanguage(trimmed).await()
        } catch (t: Throwable) {
            return TranslationResult.Error(
                "Could not detect source language: " + (t.localizedMessage ?: t.javaClass.simpleName),
            )
        }
        if (sourceTag == "und") return TranslationResult.UnknownSourceLanguage
        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: return TranslationResult.UnsupportedLanguagePair(sourceTag, targetLanguageTag)

        // Already in the user's target language: short-circuit (no model
        // download, no round trip).
        if (source == target) {
            return TranslationResult.Success(sourceTag, trimmed)
        }

        val translator = translators.getOrPut(source to target) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        }
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } catch (t: Throwable) {
            return TranslationResult.Error(
                "Couldn't download translation model: " +
                    (t.localizedMessage ?: t.javaClass.simpleName),
            )
        }
        return try {
            val translated = translator.translate(trimmed).await()
            TranslationResult.Success(sourceTag, translated)
        } catch (t: Throwable) {
            TranslationResult.Error(
                "Translation failed: " + (t.localizedMessage ?: t.javaClass.simpleName),
            )
        }
    }

    fun close() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
        runCatching { languageIdentifier.close() }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
