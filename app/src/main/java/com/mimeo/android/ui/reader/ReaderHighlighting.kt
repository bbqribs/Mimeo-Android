package com.mimeo.android.ui.reader

fun resolveReaderHighlightRange(
    textLength: Int,
    activeRange: IntRange?,
    sentenceRange: SentenceRange?,
): IntRange? {
    if (textLength <= 0) return null

    val clampedActive = activeRange?.let { range ->
        val start = range.first.coerceIn(0, textLength)
        val endExclusive = (range.last + 1).coerceIn(0, textLength)
        if (endExclusive > start) start until endExclusive else null
    }
    if (clampedActive != null) return clampedActive

    val fallbackSentence = sentenceRange ?: return null
    val start = fallbackSentence.start.coerceIn(0, textLength)
    val endExclusive = fallbackSentence.endExclusive.coerceIn(0, textLength)
    return if (endExclusive > start) start until endExclusive else null
}
