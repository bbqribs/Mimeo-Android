package com.mimeo.android.ui.common

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

fun copyItemText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("article text", text))
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

internal fun buildArticleShareText(
    articleText: String,
    title: String?,
    sourceLabel: String?,
    url: String?,
): String {
    val effectiveSourceLabel = sourceLabel?.takeIf { label ->
        label.isNotBlank() && label.lowercase() !in GENERIC_SOURCE_LABELS
    }
    val lines = mutableListOf<String>()
    if (!title.isNullOrBlank()) lines.add("— \"$title\"")
    if (!effectiveSourceLabel.isNullOrBlank()) lines.add(effectiveSourceLabel)
    if (!url.isNullOrBlank()) lines.add(url)
    return if (lines.isEmpty()) articleText else "$articleText\n\n${lines.joinToString("\n")}"
}
