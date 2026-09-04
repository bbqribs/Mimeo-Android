package com.mimeo.android.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-device-only HTTPS probe. The operator supplies the current authoritative origin
 * at invocation time, so this test never embeds a runtime host or any credential.
 */
@RunWith(AndroidJUnit4::class)
class AuthoritativeRuntimeReachabilityInstrumentedTest {
    @Test
    fun configuredAuthoritativeOriginIsReachableOverHttpsFromDevice() {
        val configuredOrigin = InstrumentationRegistry.getArguments().getString("authoritative_origin")
        assumeTrue("authoritative_origin is required for the physical-device lane", !configuredOrigin.isNullOrBlank())
        val origin = requireNotNull(configuredOrigin)
        require(origin.startsWith("https://")) { "authoritative_origin must use HTTPS" }

        val connection = (URL("${origin.trimEnd('/')}/health").openConnection() as HttpsURLConnection)
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        try {
            assertEquals(200, connection.responseCode)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            assertTrue("health response did not report ok", response.contains("\"ok\""))
        } finally {
            connection.disconnect()
        }
    }
}
