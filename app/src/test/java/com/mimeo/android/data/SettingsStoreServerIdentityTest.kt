package com.mimeo.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mimeo.android.model.ConnectionMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreServerIdentityTest {

    private lateinit var context: Context
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SettingsStore(context)
        runBlocking { store.clearAllSettingsForTesting() }
    }

    @Test
    fun emptyStore_hasBlankServerIdentity() = runBlocking {
        assertEquals("", store.readServerIdentity())
    }

    @Test
    fun saveSignedInSession_recordsNormalizedServerIdentity() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "HTTPS://Example.com/",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "token-1",
        )
        assertEquals("https://example.com", store.readServerIdentity())
    }

    @Test
    fun backfill_noToken_doesNotStampIdentity() = runBlocking {
        // A base URL exists but there is no token (signed out) -> nothing to protect.
        store.setBaseUrlForTesting("https://example.com")
        store.backfillServerIdentityIfNeeded()
        assertEquals("", store.readServerIdentity())
    }

    @Test
    fun backfill_signedInWithoutIdentity_stampsCurrentServer() = runBlocking {
        // Simulate a session signed in before the identity record existed:
        // a token + base URL are present, but identity was never written.
        store.saveSignedInSession(
            baseUrl = "https://old-server.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "token-1",
        )
        store.clearServerIdentityForTesting()
        assertEquals("", store.readServerIdentity())

        store.backfillServerIdentityIfNeeded()
        assertEquals("https://old-server.com", store.readServerIdentity())
    }

    @Test
    fun backfill_existingIdentity_isNotOverwritten() = runBlocking {
        store.saveSignedInSession(
            baseUrl = "https://server-a.com",
            connectionMode = ConnectionMode.REMOTE,
            apiToken = "token-1",
        )
        // Identity is already https://server-a.com; backfill must be a no-op.
        store.backfillServerIdentityIfNeeded()
        assertEquals("https://server-a.com", store.readServerIdentity())
    }
}
