package com.mimeo.android.ui.reader

private val HTTP_URL_REGEX = """https?://[^\s<>()]+""".toRegex(RegexOption.IGNORE_CASE)
private val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

data class ReaderLinkRange(
    val start: Int,
    val endExclusive: Int,
    val url: String,
)

internal fun extractReaderHttpLinks(text: String): List<ReaderLinkRange> {
    if (text.isBlank()) return emptyList()
    return HTTP_URL_REGEX.findAll(text).mapNotNull { match ->
        val raw = match.value
        val trimmed = raw.trimEnd(*TRAILING_URL_PUNCTUATION)
        if (trimmed.isBlank()) return@mapNotNull null
        val endExclusive = match.range.first + trimmed.length
        if (endExclusive <= match.range.first) return@mapNotNull null
        ReaderLinkRange(
            start = match.range.first,
            endExclusive = endExclusive,
            url = trimmed,
        )
    }.toList()
}
