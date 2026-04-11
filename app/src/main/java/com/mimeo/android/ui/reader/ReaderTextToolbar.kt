package com.mimeo.android.ui.reader

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
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var _status = TextToolbarStatus.Hidden

    // Updated each showMenu call; onGetContentRect reads it for smooth repositioning.
    private var currentRect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null

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

        updateEdgeScroll(rect)

        val existing = actionMode
        if (existing != null) {
            existing.invalidateContentRect()
            return
        }

        actionMode = view.startActionMode(
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    if (this@ReaderTextToolbar.onCopyRequested != null) {
                        menu.add(Menu.NONE, ITEM_COPY, 0, android.R.string.copy)
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                    menu.add(Menu.NONE, ITEM_SHARE, 1, "Share")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        ITEM_COPY -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            mode.finish()
                            true
                        }
                        ITEM_SHARE -> {
                            this@ReaderTextToolbar.onCopyRequested?.invoke()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) onShare(text)
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
        edgeScrollSpeed = 0f
        edgeResetJob?.cancel()
        edgeResetJob = null
    }

    fun dispose() {
        scope.cancel(null)
        edgeScrollSpeed = 0f
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
    }
}
