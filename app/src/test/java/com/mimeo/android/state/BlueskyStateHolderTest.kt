package com.mimeo.android.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueskyStateHolderTest {

    @Test
    fun defaultStateMatchesAppViewModelDefaults() {
        val holder = BlueskyStateHolder()

        assertFalse(holder.blueskyStatusLoading.value)
        assertNull(holder.blueskyStatusError.value)
        assertNull(holder.blueskyAccountConnection.value)
        assertNull(holder.blueskyOperatorStatus.value)

        assertFalse(holder.blueskyConnecting.value)
        assertNull(holder.blueskyConnectError.value)
        assertFalse(holder.blueskyDisconnecting.value)
        assertFalse(holder.blueskyConnectIsReadOnlyScope.value)

        assertTrue(holder.blueskyBrowseItems.value.isEmpty())
        assertTrue(holder.blueskyBrowseSources.value.isEmpty())
        assertTrue(holder.blueskyBrowsePins.value.isEmpty())
        assertFalse(holder.blueskyBrowseLoading.value)
        assertFalse(holder.blueskyBrowseLoadingMore.value)
        assertNull(holder.blueskyBrowseError.value)
        assertNull(holder.blueskyBrowseSourceFilter.value)
        assertEquals("", holder.blueskyBrowseQuery.value)
        assertNull(holder.blueskyBrowseNextCursor.value)
        assertTrue(holder.blueskyBrowsePinsAvailable.value)

        assertNull(holder.blueskyCandidatePicker.value)
        assertFalse(holder.blueskyCandidatePickerLoading.value)
        assertNull(holder.blueskyCandidatePickerError.value)

        assertNull(holder.blueskyCandidateSelection.value)
        assertNull(holder.blueskyCandidateScan.value)
        assertFalse(holder.blueskyCandidateLoading.value)
        assertNull(holder.blueskyCandidateError.value)

        assertTrue(holder.blueskyCandidateSavingUrls.value.isEmpty())
        assertTrue(holder.blueskyCandidateSaveErrors.value.isEmpty())
        assertFalse(holder.blueskyCandidatePinning.value)
    }

    @Test
    fun internalMutableFlowsMutatePublicStateFlows() {
        val holder = BlueskyStateHolder()

        holder._blueskyStatusLoading.value = true
        assertTrue(holder.blueskyStatusLoading.value)

        holder._blueskyConnectError.value = "test error"
        assertEquals("test error", holder.blueskyConnectError.value)

        holder._blueskyBrowseQuery.value = "kotlin"
        assertEquals("kotlin", holder.blueskyBrowseQuery.value)

        holder._blueskyCandidatePinning.value = true
        assertTrue(holder.blueskyCandidatePinning.value)
    }
}
