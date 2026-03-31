package com.mimeo.android.ui.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PlaybackScrollLockInSmokeTest {

    @Ignore("Manual smoke: requires signed-in account, seeded queue, and active playback content.")
    @Test
    fun manualScrollDetach_thenLocusReattach_fullFlow() {
        // Manual smoke contract:
        // 1. Open Locus and start playback.
        // 2. Manually scroll so highlight is off-screen.
        // 3. Confirm playback progress does not auto-snap view back.
        // 4. Tap Locus tab while in Locus.
        // 5. Confirm highlight is brought back into view and follow resumes.
        assertTrue(true)
    }
}

