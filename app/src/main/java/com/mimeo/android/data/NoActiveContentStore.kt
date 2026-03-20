package com.mimeo.android.data

import android.content.Context

/**
 * Lightweight SharedPreferences-backed store for item IDs known to have no active content version.
 *
 * Written by [AutoDownloadWorker] after each download run; read by [AppViewModel] on the next
 * queue load to seed [_noActiveContentItemIds] before target selection, ensuring items that
 * permanently failed with 404/409-no-active-content are not re-attempted across process deaths.
 *
 * The store holds only IDs currently present in the queue — callers must call [retainOnly] when
 * the queue changes to avoid stale accumulation.
 */
class NoActiveContentStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(itemIds: Set<Int>) {
        if (itemIds.isEmpty()) return
        val merged = getAll() + itemIds
        prefs.edit().putString(KEY_IDS, merged.joinToString(",")).apply()
    }

    fun getAll(): Set<Int> {
        val raw = prefs.getString(KEY_IDS, "") ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    /** Keep only IDs that appear in [currentQueueItemIds]; call after queue changes. */
    fun retainOnly(currentQueueItemIds: Set<Int>) {
        val filtered = getAll().filter { currentQueueItemIds.contains(it) }.toSet()
        prefs.edit().putString(KEY_IDS, filtered.joinToString(",")).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_IDS).apply()
    }

    companion object {
        private const val PREFS_NAME = "mimeo_no_active_content"
        private const val KEY_IDS = "item_ids"
    }
}
