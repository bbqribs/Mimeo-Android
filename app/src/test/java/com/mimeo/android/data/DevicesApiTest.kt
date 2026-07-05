package com.mimeo.android.data

import com.mimeo.android.isStaleTokenAuthFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Android T-AND-DEVICES-1 — API contract coverage for the `/account/devices`
 * surface: list parsing (including the never-a-token guarantee), the two
 * revoke actions, and the endpoint/base-url independence of every call.
 */
class DevicesApiTest {

    private fun client() = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())

    private val listBody = """
        [
            {
                "id": 2, "name": "web-session", "token_prefix": "abcd1234",
                "scope": "read_write", "created_at": "2026-01-10T08:00:00+00:00",
                "expires_at": null, "is_expired": false,
                "last_used_at": "2026-01-15T09:30:00+00:00", "is_current": true
            },
            {
                "id": 5, "name": "Pixel 8", "token_prefix": "efgh5678",
                "scope": "read", "created_at": "2026-01-12T08:00:00+00:00",
                "expires_at": "2026-06-01T00:00:00+00:00", "is_expired": false,
                "last_used_at": null, "is_current": false
            }
        ]
    """.trimIndent()

    @Test
    fun devicesListParsesExpectedBackendPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(listBody))
        server.start()
        try {
            val devices = client().getAccountDevices(server.url("/").toString(), "device-token")
            assertEquals(2, devices.size)

            val current = devices.first { it.id == 2 }
            assertEquals("web-session", current.name)
            assertEquals("abcd1234", current.tokenPrefix)
            assertEquals("read_write", current.scope)
            assertEquals("2026-01-10T08:00:00+00:00", current.createdAt)
            assertEquals(null, current.expiresAt)
            assertFalse(current.isExpired)
            assertEquals("2026-01-15T09:30:00+00:00", current.lastUsedAt)
            assertTrue(current.isCurrent)

            val other = devices.first { it.id == 5 }
            assertEquals("Pixel 8", other.name)
            assertFalse(other.isCurrent)
            assertEquals(null, other.lastUsedAt)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/account/devices", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun devicesListNeverExposesATokenValueEvenIfBackendIncludedOne() = runBlocking {
        // Defends the client contract: even if a future/misbehaving backend response smuggled
        // a raw token alongside the safe metadata, ignoreUnknownKeys means the parsed model has
        // no field to carry it, so it can never reach the UI.
        val bodyWithUnexpectedTokenField = """
            [{"id": 9, "name": "rogue", "token_prefix": "aaaa0000", "token": "super-secret-raw-token",
              "scope": "read", "created_at": null, "expires_at": null, "is_expired": false,
              "last_used_at": null, "is_current": false}]
        """.trimIndent()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(bodyWithUnexpectedTokenField))
        server.start()
        try {
            val devices = client().getAccountDevices(server.url("/").toString(), "device-token")
            val device = devices.single()
            assertFalse(device.toString().contains("super-secret-raw-token"))
            assertEquals("aaaa0000", device.tokenPrefix)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun revokeDeviceCallsExpectedEndpointAndParsesOk() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.start()
        try {
            val result = client().postRevokeDevice(server.url("/").toString(), "device-token", 5)
            assertTrue(result.ok)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/account/devices/5/revoke", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("{}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun revokeDeviceRefusalForCurrentSessionSurfacesAs400() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"detail":"Cannot revoke the token used for this request. Sign out instead."}"""),
        )
        server.start()
        try {
            client().postRevokeDevice(server.url("/").toString(), "device-token", 2)
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(400, error.statusCode)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun revokeOtherDevicesCallsExpectedEndpointAndParsesCount() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"revoked":2}"""))
        server.start()
        try {
            val result = client().postRevokeOtherDevices(server.url("/").toString(), "device-token")
            assertEquals(2, result.revoked)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/account/devices/revoke-others", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("{}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun devicesEndpointsHonorWhicheverBaseUrlIsPassedIn() = runBlocking {
        // Each call takes baseUrl as a plain parameter (no global/static host), so Local/LAN/Remote
        // endpoint selection upstream is preserved across the devices flow.
        val serverA = MockWebServer()
        serverA.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        serverA.start()
        val serverB = MockWebServer()
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        serverB.start()
        try {
            val api = client()
            api.getAccountDevices(serverA.url("/").toString(), "token-a")
            api.getAccountDevices(serverB.url("/").toString(), "token-b")

            assertEquals("Bearer token-a", serverA.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer token-b", serverB.takeRequest().getHeader("Authorization"))
        } finally {
            serverA.shutdown()
            serverB.shutdown()
        }
    }

    @Test
    fun unauthorizedSurfacesAs401ForReauthRouting() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"Unauthorized"}"""))
        server.start()
        try {
            client().getAccountDevices(server.url("/").toString(), "stale-token")
            fail("Expected ApiException")
        } catch (error: ApiException) {
            assertEquals(401, error.statusCode)
            assertTrue(isStaleTokenAuthFailure(error))
        } finally {
            server.shutdown()
        }
    }
}
