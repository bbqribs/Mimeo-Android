package com.mimeo.android.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ReaderTextToolbar(
    private val view: View,
    private val context: Context,
    private val onShare: (String) -> Unit,
    private val onWebSearch: (String) -> Unit,
    private val onTranslate: (String) -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var _status = TextToolbarStatus.Hidden

    // Updated each showMenu call; onGetContentRect reads it for smooth repositioning.
    private var currentRect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    /**
     * The href of the link the active selection lands on, or null when the
     * selection touches no single link. Set from the reader as the selection
     * changes. When this changes while the toolbar is shown, the action mode is
     * invalidated so the link items show/hide without losing the selection.
     */
    var currentLinkUrl: String? = null
        set(value) {
            if (field == value) return
            field = value
            actionMode?.invalidate()
        }

    // Non-zero when the selection rect is within the edge zone (top or bottom).
    // PlayerScreen polls this at ~60fps and calls scrollBy.
    @Volatile var edgeScrollSpeed: Float = 0f
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeResetJob: Job? = null

    override val status: TextToolbarStatus get() = _status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        currentRect = rect
        this.onCopyRequested = onCopyRequested
        this.onSelectAllRequested = onSelectAllRequested

        updateEdgeScroll(rect)

        val existing = actionMode
        if (existing != null) {
            // invalidate() reruns onPrepareActionMode so Select All correctly
            // hides once the entire container is selected (Compose passes
            // onSelectAllRequested = null in that case).
            existing.invalidate()
            existing.invalidateContentRect()
            return
        }

        actionMode = view.startActionMode(
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    // All items are added unconditionally; per-item visibility
                    // is decided in onPrepareActionMode, which reruns on every
                    // invalidate() so callback availability (Copy/Select All)
                    // and link state stay in sync as the selection changes.
                    menu.add(Menu.NONE, ITEM_COPY, 0, android.R.string.copy)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(Menu.NONE, ITEM_SHARE, 1, "Share")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(Menu.NONE, ITEM_WEB_SEARCH, 2, "Web search")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(Menu.NONE, ITEM_SELECT_ALL, 3, android.R.string.selectAll)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(Menu.NONE, ITEM_TRANSLATE, 4, "Translate")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(Menu.NONE, ITEM_SHARE_LINK, 5, "Share link address")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(Menu.NONE, ITEM_COPY_LINK, 6, "Copy link address")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                    val canReadSelection = this@ReaderTextToolbar.onCopyRequested != null
                    val canSelectAll = this@ReaderTextToolbar.onSelectAllRequested != null
                    val hasLink = currentLinkUrl?.isNotBlank() == true
                    menu.findItem(ITEM_COPY)?.isVisible = canReadSelection
                    menu.findItem(ITEM_SHARE)?.isVisible = canReadSelection
                    menu.findItem(ITEM_WEB_SEARCH)?.isVisible = canReadSelection
                    menu.findItem(ITEM_TRANSLATE)?.isVisible = canReadSelection
                    menu.findItem(ITEM_SELECT_ALL)?.isVisible = canSelectAll
                    menu.findItem(ITEM_SHARE_LINK)?.isVisible = hasLink
                    menu.findItem(ITEM_COPY_LINK)?.isVisible = hasLink
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        ITEM_COPY -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            sanitizeReaderClipboard()
                            mode.finish()
                            true
                        }
                        ITEM_SHARE -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            val text = sanitizeReaderClipboard()
                            if (!text.isNullOrBlank()) onShare(text)
                            mode.finish()
                            true
                        }
                        ITEM_WEB_SEARCH -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            val text = sanitizeReaderClipboard()
                            if (!text.isNullOrBlank()) onWebSearch(text)
                            mode.finish()
                            true
                        }
                        ITEM_TRANSLATE -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            val text = sanitizeReaderClipboard()
                            if (!text.isNullOrBlank()) onTranslate(text)
                            mode.finish()
                            true
                        }
                        ITEM_SELECT_ALL -> {
                            this@ReaderTextToolbar.onSelectAllRequested?.invoke()
                            // Don't finish: user usually wants to act on the
                            // newly-expanded selection (Copy, Share, etc.).
                            true
                        }
                        ITEM_SHARE_LINK -> {
                            val url = currentLinkUrl
                            if (!url.isNullOrBlank()) onShare(url)
                            mode.finish()
                            true
                        }
                        ITEM_COPY_LINK -> {
                            val url = currentLinkUrl
                            if (!url.isNullOrBlank()) copyLinkAddress(url)
                            mode.finish()
                            true
                        }
                        else -> false
                    }
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    _status = TextToolbarStatus.Hidden
                    actionMode = null
                }

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                    val r = currentRect
                    outRect.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
                }
            },
            ActionMode.TYPE_FLOATING,
        )
        if (actionMode != null) _status = TextToolbarStatus.Shown
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        _status = TextToolbarStatus.Hidden
        currentLinkUrl = null
        onCopyRequested = null
        onSelectAllRequested = null
        edgeScrollSpeed = 0f
        edgeResetJob?.cancel()
        edgeResetJob = null
    }

    fun dispose() {
        scope.cancel(null)
        edgeScrollSpeed = 0f
    }

    private fun copyLinkAddress(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("link address", url))
    }

    /**
     * Re-read the clipboard after Compose's copy and strip the reader's
     * zero-width layout separators, so copied or shared selections never carry
     * invisible characters. Returns the cleaned text (or null when empty).
     */
    private fun sanitizeReaderClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        val cleaned = readerTextWithPlainSeparators(raw)
        if (cleaned != raw) {
            clipboard.setPrimaryClip(ClipData.newPlainText("reader text", cleaned))
        }
        return cleaned
    }

    private fun updateEdgeScroll(rect: Rect) {
        val viewH = view.height.toFloat()
        // Simple screen-fraction threshold: avoids the coordinate-space mismatch in
        // Compose's getSelectionToolbarLocation() which mixes local/root rect coordinates.
        val speed = when {
            rect.bottom > viewH * 0.80f -> 14f
            rect.top < viewH * 0.20f -> -14f
            else -> 0f
        }
        edgeScrollSpeed = speed

        edgeResetJob?.cancel()
        if (speed != 0f) {
            // 1-second window: 14 px/frame × ~62 frames ≈ 870 px of scroll.
            edgeResetJob = scope.launch {
                delay(1000L)
                edgeScrollSpeed = 0f
            }
        }
    }

    companion object {
        private const val ITEM_COPY = 1
        private const val ITEM_SHARE = 2
        private const val ITEM_WEB_SEARCH = 3
        private const val ITEM_SHARE_LINK = 4
        private const val ITEM_COPY_LINK = 5
        private const val ITEM_SELECT_ALL = 6
        private const val ITEM_TRANSLATE = 7
    }
}
