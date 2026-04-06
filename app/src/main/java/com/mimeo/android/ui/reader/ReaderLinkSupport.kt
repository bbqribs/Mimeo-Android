package com.mimeo.android.ui.reader

import com.mimeo.android.model.ItemTextContentBlock
import com.mimeo.android.model.ItemTextContentLink
import java.net.URI
import java.util.Locale

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
    val linkSpecs = buildPreservedLinkSpecs(contentBlocks)
    if (linkSpecs.isEmpty()) return emptyList()
    var globalSearchCursor = 0
    linkSpecs.forEach { spec ->
        val match = resolveLinkRangeInText(
            fullText = text,
            spec = spec,
            globalSearchCursor = globalSearchCursor,
        ) ?: return@forEach
        if (ranges.any { existing -> match.start < existing.endExclusive && match.endExclusive > existing.start }) {
            return@forEach
        }
        ranges += ReaderLinkRange(
            start = match.start,
            endExclusive = match.endExclusive,
            url = spec.url,
        )
        globalSearchCursor = match.endExclusive.coerceAtLeast(globalSearchCursor)
    }
    return ranges
}

private data class PreservedLinkSpec(
    val url: String,
    val preferredText: String?,
    val hrefHints: List<String>,
)

private data class FullTextLinkRange(
    val start: Int,
    val endExclusive: Int,
)

private fun buildPreservedLinkSpecs(contentBlocks: List<ItemTextContentBlock>): List<PreservedLinkSpec> {
    return buildList {
        contentBlocks
            .asSequence()
            .filter { block ->
                block.links.isNullOrEmpty().not() && block.text.isNullOrBlank().not()
            }
            .forEach { block ->
                val paragraphText = block.text.orEmpty()
                block.links.orEmpty().forEach { link ->
                    val url = normalizeHttpUrl(link.href ?: return@forEach) ?: return@forEach
                    val preferredFromOffsets = normalizedTextFromOffsets(
                        paragraphText = paragraphText,
                        start = link.start,
                        end = link.end,
                    )
                    val preferredText = preferredFromOffsets
                        ?: link.text?.trim()?.takeIf { it.isNotEmpty() }
                    val hrefHints = buildHrefHostPathHints(url)
                    add(
                        PreservedLinkSpec(
                            url = url,
                            preferredText = preferredText,
                            hrefHints = hrefHints,
                        ),
                    )
                }
            }
    }
}

private fun normalizedTextFromOffsets(
    paragraphText: String,
    start: Int?,
    end: Int?,
): String? {
    if (paragraphText.isBlank()) return null
    if (start == null || end == null) return null
    val length = paragraphText.length
    val safeStart = start.coerceIn(0, length)
    val safeEnd = end.coerceIn(0, length)
    if (safeEnd <= safeStart) return null
    return paragraphText.substring(safeStart, safeEnd).trim().takeIf { it.isNotEmpty() }
}

private fun resolveLinkRangeInText(
    fullText: String,
    spec: PreservedLinkSpec,
    globalSearchCursor: Int,
): FullTextLinkRange? {
    val fullLength = fullText.length
    if (fullLength <= 0) return null
    val candidates = buildList {
        spec.preferredText?.takeIf { it.isNotBlank() }?.let { add(it) }
        spec.hrefHints.forEach { hint ->
            if (hint.isNotBlank()) add(hint)
        }
    }.distinct()
    if (candidates.isEmpty()) return null

    val cursor = globalSearchCursor.coerceIn(0, fullLength)
    val loweredFull = fullText.lowercase(Locale.US)
    for (candidate in candidates) {
        val loweredCandidate = candidate.lowercase(Locale.US)
        val index = loweredFull.indexOf(loweredCandidate, startIndex = cursor)
            .takeIf { it >= 0 }
            ?: loweredFull.indexOf(loweredCandidate).takeIf { it >= 0 }
            ?: continue
        val tokenLength = if (spec.hrefHints.contains(candidate)) {
            computeLinkTokenLength(fullText, index)
        } else {
            candidate.length
        }
        if (tokenLength <= 0) continue
        val endExclusive = (index + tokenLength).coerceIn(index, fullLength)
        if (endExclusive <= index) continue
        return FullTextLinkRange(
            start = index,
            endExclusive = endExclusive,
        )
    }
    return null
}

private fun buildHrefHostPathHints(url: String): List<String> {
    val uri = runCatching { URI(url) }.getOrNull() ?: return emptyList()
    val host = uri.host?.removePrefix("www.") ?: return emptyList()
    val path = uri.rawPath.orEmpty()
    if (path.isBlank() || path == "/") return listOf(host)
    val segments = path.split('/').filter { it.isNotBlank() }
    if (segments.isEmpty()) return listOf(host)

    val hints = mutableListOf<String>()
    var prefix = host
    hints += prefix
    segments.forEach { segment ->
        prefix += "/$segment"
        hints += prefix
    }
    return hints.reversed()
}

private fun normalizeHttpUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    return trimmed
}

private fun computeLinkTokenLength(text: String, start: Int): Int {
    if (start !in text.indices) return 0
    var idx = start
    while (idx < text.length) {
        val ch = text[idx]
        if (ch.isWhitespace()) break
        idx += 1
    }
    return (idx - start).coerceAtLeast(0)
}
