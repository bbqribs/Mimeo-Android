package com.mimeo.android.ui.library

enum class LibrarySortOption(
    val label: String,
    val sortField: String,
    val sortDir: String,
) {
    NEWEST("Newest", "added_at", "desc"),
    OLDEST("Oldest", "added_at", "asc"),
    TITLE("Title", "title", "asc"),
    PROGRESS("Progress", "progress_percent", "desc"),
}
