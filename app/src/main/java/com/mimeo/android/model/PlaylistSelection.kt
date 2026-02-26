package com.mimeo.android.model

const val SMART_QUEUE_PLAYLIST_SENTINEL = -1

fun decodeSelectedPlaylistId(rawValue: Int?): Int? {
    return rawValue?.takeIf { it > 0 }
}

fun encodeSelectedPlaylistId(selectedPlaylistId: Int?): Int {
    return selectedPlaylistId?.takeIf { it > 0 } ?: SMART_QUEUE_PLAYLIST_SENTINEL
}
