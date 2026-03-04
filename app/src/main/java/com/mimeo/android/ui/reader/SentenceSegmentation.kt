package com.mimeo.android.ui.reader

data class SentenceRange(
    val start: Int,
    val endExclusive: Int,
)

private val COMMON_ABBREVIATIONS = setOf(
    "mr.",
    "mrs.",
    "ms.",
    "dr.",
    "prof.",
    "sr.",
    "jr.",
    "st.",
    "vs.",
    "etc.",
    "e.g.",
    "i.e.",
    "u.s.",
)

fun segmentSentences(text: String): List<SentenceRange> {
    if (text.isEmpty()) return emptyList()

    val ranges = mutableListOf<SentenceRange>()
    var start = 0
    var index = 0

    while (index < text.length) {
        val current = text[index]
        val isBoundary = when {
            current == '\n' -> true
            current == '.' || current == '?' || current == '!' -> {
                !isAbbreviationPeriod(text, index) && hasSentenceBoundaryTail(text, index)
            }
            else -> false
        }
        if (!isBoundary) {
            index += 1
            continue
        }

        val endExclusive = consumeBoundaryWhitespace(text, index + 1)
        if (start < endExclusive) {
            ranges += SentenceRange(start = start, endExclusive = endExclusive)
        }
        start = endExclusive
        index = endExclusive
    }

    if (start < text.length) {
        ranges += SentenceRange(start = start, endExclusive = text.length)
    }

    return ranges
}

fun findSentenceRangeForOffset(
    text: String,
    offsetInText: Int,
    sentenceRanges: List<SentenceRange> = segmentSentences(text),
): SentenceRange? {
    if (sentenceRanges.isEmpty()) return null
    val safeOffset = offsetInText.coerceIn(0, text.length)
    return sentenceRanges.firstOrNull { safeOffset in it.start until it.endExclusive }
        ?: sentenceRanges.lastOrNull { safeOffset >= it.start }
        ?: sentenceRanges.firstOrNull()
}

private fun hasSentenceBoundaryTail(text: String, punctuationIndex: Int): Boolean {
    val nextIndex = punctuationIndex + 1
    if (nextIndex >= text.length) return true
    return text[nextIndex].isWhitespace()
}

private fun consumeBoundaryWhitespace(text: String, startIndex: Int): Int {
    var cursor = startIndex
    while (cursor < text.length && text[cursor].isWhitespace()) {
        cursor += 1
    }
    return cursor
}

private fun isAbbreviationPeriod(text: String, punctuationIndex: Int): Boolean {
    if (text.getOrNull(punctuationIndex) != '.') return false

    var tokenStart = punctuationIndex
    while (tokenStart > 0) {
        val previous = text[tokenStart - 1]
        if (!previous.isLetter() && previous != '.') break
        tokenStart -= 1
    }

    val token = text.substring(tokenStart, punctuationIndex + 1).lowercase()
    if (token in COMMON_ABBREVIATIONS) return true
    if (Regex("([a-z]\\.){2,}").matches(token)) return true
    return Regex("[a-z]\\.").matches(token)
}
