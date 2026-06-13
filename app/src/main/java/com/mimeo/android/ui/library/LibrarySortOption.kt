package com.mimeo.android.ui.library

enum class LibrarySortOption(
    val label: String,
    val sortField: String,
    val sortDir: String,
) {
    SMART_QUEUE("Queue", "", ""),
    NEWEST("Newest", "created", "desc"),
    OLDEST("Oldest", "created", "asc"),
    OPENED("Opened", "opened", "desc"),
    PROGRESS("Progress", "progress", "desc"),
    ARCHIVED_AT("Archived", "archived", "desc"),
    TRASHED_AT("Binned", "trashed", "desc"),
    ;

    companion object {
        val SMART_QUEUE_SORTS = listOf(SMART_QUEUE, NEWEST, OLDEST, OPENED, PROGRESS)
        val INBOX_SORTS = listOf(NEWEST, OLDEST, OPENED, PROGRESS)
        val FAVORITES_SORTS = listOf(NEWEST, OLDEST, OPENED, PROGRESS)
        val ARCHIVE_SORTS = listOf(ARCHIVED_AT, NEWEST, OLDEST, OPENED)
        val BIN_SORTS = listOf(TRASHED_AT, NEWEST, OLDEST, OPENED)
    }
}
