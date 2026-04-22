package com.mimeo.android.ui.player

import androidx.compose.runtime.mutableStateOf
import com.mimeo.android.model.ItemTextResponse
import com.mimeo.android.model.PlaybackChunk

class PlayerSurfaceContentState {
    val textPayload = mutableStateOf<ItemTextResponse?>(null)
    val chunks = mutableStateOf<List<PlaybackChunk>>(emptyList())
    val usingCachedText = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val preserveVisibleContentOnReload = mutableStateOf(false)
    val bodyRevealReady = mutableStateOf(false)

    fun reset() {
        textPayload.value = null
        chunks.value = emptyList()
        usingCachedText.value = false
        isLoading.value = false
        preserveVisibleContentOnReload.value = false
        bodyRevealReady.value = false
    }
}
