package com.mimeo.android.ui.reader

import com.mimeo.android.model.ItemTextContentBlock
import com.mimeo.android.model.ItemTextContentLink
import java.net.URI

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

internal fun extractReaderPreservedLinks(
    text: String,
    contentBlocks: List<ItemTextContentBlock>?,
): List<ReaderLinkRange> {
    if (text.isBlank() || contentBlocks.isNullOrEmpty()) return emptyList()
    val ranges = mutableListOf<ReaderLinkRange>()
    val normalizedBlocks = contentBlocks.filter { it.type.equals("paragraph", ignoreCase = true) }
    if (normalizedBlocks.isEmpty()) return emptyList()
    var paragraphSearchCursor = 0
    normalizedBlocks.forEach { block ->
        val paragraphText = block.text.orEmpty()
        if (paragraphText.isBlank()) return@forEach
        val paragraphStart = text.indexOf(paragraphText, startIndex = paragraphSearchCursor)
            .takeIf { it >= 0 }
            ?: text.indexOf(paragraphText).takeIf { it >= 0 }
            ?: return@forEach
        paragraphSearchCursor = paragraphStart + paragraphText.length
        val paragraphLinks = block.links.orEmpty()
        if (paragraphLinks.isEmpty()) return@forEach
        var localSearchCursor = 0
        paragraphLinks.forEach { link ->
            val normalized = normalizePreservedLink(link = link, paragraphText = paragraphText, localSearchCursor = localSearchCursor)
                ?: return@forEach
            localSearchCursor = normalized.endExclusive
            val globalStart = paragraphStart + normalized.start
            val globalEnd = paragraphStart + normalized.endExclusive
            if (globalStart < 0 || globalEnd > text.length || globalStart >= globalEnd) return@forEach
            if (ranges.any { existing -> globalStart < existing.endExclusive && globalEnd > existing.start }) return@forEach
            ranges += ReaderLinkRange(
                start = globalStart,
                endExclusive = globalEnd,
                url = normalized.url,
            )
        }
    }
    return ranges.sortedBy { it.start }
}

private data class NormalizedLinkRange(
    val start: Int,
    val endExclusive: Int,
    val url: String,
)

private fun normalizePreservedLink(
    link: ItemTextContentLink,
    paragraphText: String,
    localSearchCursor: Int,
): NormalizedLinkRange? {
    val url = normalizeHttpUrl(link.href ?: return null) ?: return null
    val textLength = paragraphText.length
    if (textLength <= 0) return null

    val explicitStart = link.start
    val explicitEnd = link.end
    if (explicitStart != null && explicitEnd != null) {
        val safeStart = explicitStart.coerceIn(0, textLength)
        val safeEnd = explicitEnd.coerceIn(0, textLength)
        if (safeEnd > safeStart) {
            val expected = link.text?.trim().orEmpty()
            if (expected.isBlank() || paragraphText.substring(safeStart, safeEnd) == expected) {
                return NormalizedLinkRange(
                    start = safeStart,
                    endExclusive = safeEnd,
                    url = url,
                )
            }
        }
    }

    val linkText = link.text?.trim().orEmpty()
    if (linkText.isBlank()) return null
    val found = paragraphText.indexOf(linkText, startIndex = localSearchCursor.coerceIn(0, textLength))
        .takeIf { it >= 0 }
        ?: paragraphText.indexOf(linkText).takeIf { it >= 0 }
        ?: return null
    return NormalizedLinkRange(
        start = found,
        endExclusive = (found + linkText.length).coerceIn(found, textLength),
        url = url,
    )
}

private fun normalizeHttpUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    return trimmed
}
