package com.mimeo.android.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WorkManager test-driver coverage for the pending-save retry scheduling contract:
 * parking schedules a network-constrained unique job; an unmet constraint blocks execution;
 * resolving cancels the obsolete work.
 */
@RunWith(AndroidJUnit4::class)
class WorkSchedulerPendingSaveRetryTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun pendingRetryInfos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork("pending-save-retry").get()

    @Test
    fun parkingSchedules_networkConstrainedWork_thatWaitsForConstraint() {
        WorkScheduler.enqueuePendingSaveRetry(context, parkedBaseUrl = "https://a.example")

        val infos = pendingRetryInfos()
        assertEquals(1, infos.size)
        // Network constraint not yet satisfied by the test driver → still enqueued, not run.
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    @Test
    fun keepPolicy_doesNotStackASecondJob() {
        WorkScheduler.enqueuePendingSaveRetry(context, parkedBaseUrl = "https://a.example")
        WorkScheduler.enqueuePendingSaveRetry(context, parkedBaseUrl = "https://a.example")

        assertEquals(1, pendingRetryInfos().size)
    }

    @Test
    fun cancel_marksWorkCancelled() {
        WorkScheduler.enqueuePendingSaveRetry(context, parkedBaseUrl = "https://a.example")
        assertTrue(pendingRetryInfos().isNotEmpty())

        WorkScheduler.cancelPendingSaveRetry(context)

        val states = pendingRetryInfos().map { it.state }
        assertTrue(states.all { it == WorkInfo.State.CANCELLED })
    }
}
