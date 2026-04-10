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

internal class ReaderTextToolbar(
    private val view: View,
    private val context: Context,
    private val onShare: (String) -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var _status = TextToolbarStatus.Hidden

    override val status: TextToolbarStatus get() = _status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        actionMode?.finish()
        actionMode = view.startActionMode(
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    if (onCopyRequested != null) {
                        menu.add(Menu.NONE, ITEM_COPY, 0, android.R.string.copy)
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }
                    menu.add(Menu.NONE, ITEM_SHARE, 1, "Share")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    if (onSelectAllRequested != null) {
                        menu.add(Menu.NONE, ITEM_SELECT_ALL, 2, android.R.string.selectAll)
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    }
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        ITEM_COPY -> {
                            onCopyRequested?.invoke()
                            mode.finish()
                            true
                        }
                        ITEM_SHARE -> {
                            onCopyRequested?.invoke()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) onShare(text)
                            mode.finish()
                            true
                        }
                        ITEM_SELECT_ALL -> {
                            onSelectAllRequested?.invoke()
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
                    outRect.set(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt(),
                    )
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
    }

    companion object {
        private const val ITEM_COPY = 1
        private const val ITEM_SHARE = 2
        private const val ITEM_SELECT_ALL = 3
    }
}
