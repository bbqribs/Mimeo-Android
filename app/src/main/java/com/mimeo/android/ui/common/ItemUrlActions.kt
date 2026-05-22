package com.mimeo.android.ui.common

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

fun shareItemUrl(context: Context, url: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    val chooser = Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(context, chooser, null)
}

fun openItemInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(context, intent, null)
}

fun shareSelectedText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(context, chooser, null)
}

/**
 * Run a web search for [query] using the standard implicit web-search intent.
 * Falls back to opening a browser search URL when no activity handles
 * [Intent.ACTION_WEB_SEARCH]. A blank query is a no-op.
 */
fun webSearchText(context: Context, query: String) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, trimmed)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ContextCompat.startActivity(context, searchIntent, null)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(trimmed)),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ContextCompat.startActivity(context, fallback, null)
        } catch (_: ActivityNotFoundException) {
            // No browser available; nothing further to do.
        }
    }
}

fun copyItemText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("article text", text))
}

/**
 * Copy article text to the clipboard with the same title/source/url header as
 * [shareItemText], so copied and shared article text stay consistent.
 */
fun copyArticleText(context: Context, articleText: String, title: String?, sourceLabel: String?, url: String?) {
    copyItemText(context, buildArticleShareText(articleText, title, sourceLabel, url))
}

fun shareItemText(context: Context, articleText: String, title: String?, sourceLabel: String?, url: String?) {
    val fullText = buildArticleShareText(articleText, title, sourceLabel, url)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, fullText)
    }
    val chooser = Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ContextCompat.startActivity(context, chooser, null)
}

private val GENERIC_SOURCE_LABELS = setOf(
    "unknown source", "android selection", "app share", "shared-text.mimeo.local",
)

/**
 * Build the copied/shared article text: a title / domain / link header
 * prepended above the article body. Generic source labels are dropped, and a
 * missing header field is skipped. With no header fields, the body is returned
 * unchanged.
 */
internal fun buildArticleShareText(
    articleText: String,
    title: String?,
    sourceLabel: String?,
    url: String?,
): String {
    val effectiveSourceLabel = sourceLabel?.takeIf { label ->
        label.isNotBlank() && label.lowercase() !in GENERIC_SOURCE_LABELS
    }
    val header = mutableListOf<String>()
    if (!title.isNullOrBlank()) header.add(title.trim())
    if (!effectiveSourceLabel.isNullOrBlank()) header.add(effectiveSourceLabel.trim())
    if (!url.isNullOrBlank()) header.add(url.trim())
    return if (header.isEmpty()) articleText else "${header.joinToString("\n")}\n\n$articleText"
}
