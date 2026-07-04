package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.ConnectionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreAuthSessionTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking {
            store.clearAllSettingsForTesting()
            store.saveTokenOnly("")
        }
    }

    @Test
    fun savedSignedInSessionRestoresStoredToken() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = " issued-token ",
        )

        val restored = store.settingsFlow.first()

        assertEquals("issued-token", restored.apiToken)
        assertEquals(ConnectionMode.REMOTE, restored.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", restored.baseUrl)
    }

    @Test
    fun clearingStaleTokenPreservesRemoteEndpointState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "stale-token",
        )

        store.saveTokenOnly("")
        val afterClear = store.settingsFlow.first()

        assertEquals("", afterClear.apiToken)
        assertEquals(ConnectionMode.REMOTE, afterClear.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterClear.baseUrl)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterClear.remoteBaseUrl)
    }

    @Test
    fun replacingTokenDoesNotChangeRemoteEndpointState() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://beh-august2015.taildacac5.ts.net",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "stale-token",
        )

        store.saveTokenOnly("fresh-token")
        val afterReplace = store.settingsFlow.first()

        assertEquals("fresh-token", afterReplace.apiToken)
        assertEquals(ConnectionMode.REMOTE, afterReplace.connectionMode)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterReplace.baseUrl)
        assertEquals("https://beh-august2015.taildacac5.ts.net", afterReplace.remoteBaseUrl)
    }
}
