package com.mimeo.android.data

import com.mimeo.android.model.AiProviderConfigIn
import com.mimeo.android.model.AiProviderErrorCode
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
 * BYOAI-A5 — operator provider write/test/delete API contract and the no-leak
 * error path. Confirms the request payloads, endpoints, and that a failing
 * response is mapped to a coarse [AiProviderConfigException] whose message never
 * carries the raw response body.
 */
class AiProviderConfigApiTest {

    private val statusBody = """
        {"provider":"anthropic","model":"claude-x","enabled":true,"configured":true,
         "key_present":true,"key_last4":"1234","last_test_status":"untested",
         "source":"database","can_edit":true}
    """.trimIndent()

    private fun client() = ApiClient(okHttpClient = OkHttpClient.Builder().followRedirects(false).build())

    @Test
    fun saveSendsExpectedPayloadAndEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(statusBody))
        server.start()
        try {
            client().saveAiProviderConfig(
                baseUrl = server.url("/").toString(),
                token = "operator-token",
                config = AiProviderConfigIn(
                    provider = "openai_compatible",
                    model = "local-model",
                    enabled = true,
                    baseUrl = "https://host:1234",
                    apiKey = "sk-secret",
                ),
            )
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertEquals("POST", request.method)
            assertEquals("/config/ai-provider", request.path)
            assertEquals("Bearer operator-token", request.getHeader("Authorization"))
            assertTrue(body, body.contains("\"provider\":\"openai_compatible\""))
            assertTrue(body, body.contains("\"model\":\"local-model\""))
            assertTrue(body, body.contains("\"enabled\":true"))
            assertTrue(body, body.contains("\"base_url\":\"https://host:1234\""))
            assertTrue(body, body.contains("\"api_key\":\"sk-secret\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun saveWithNullKeyOmitsApiKeyField() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(statusBody))
        server.start()
        try {
            client().saveAiProviderConfig(
                baseUrl = server.url("/").toString(),
                token = "operator-token",
                config = AiProviderConfigIn(
                    provider = "anthropic",
                    model = "claude-x",
                    enabled = true,
                    baseUrl = null,
                    apiKey = null,
                ),
            )
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body, body.contains("api_key"))
            assertFalse(body, body.contains("base_url"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testUsesTestEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(statusBody))
        server.start()
        try {
            client().testAiProviderConfig(server.url("/").toString(), "operator-token")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/config/ai-provider/test", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun deleteUsesDeleteEndpoint() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            client().deleteAiProviderConfig(server.url("/").toString(), "operator-token")
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/config/ai-provider", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failureMapsToSafeCodeAndNeverLeaksBody() = runBlocking {
        val server = MockWebServer()
        val leakyBody = """
            {"detail":{"code":"encryption_key_required",
             "raw":"AI_PROVIDER_ENCRYPTION_KEY missing; ciphertext gAAAA== sk-leak123"}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(400).setBody(leakyBody))
        server.start()
        try {
            client().saveAiProviderConfig(
                baseUrl = server.url("/").toString(),
                token = "operator-token",
                config = AiProviderConfigIn("anthropic", "claude-x", true, null, "sk-leak123"),
            )
            fail("expected AiProviderConfigException")
        } catch (error: AiProviderConfigException) {
            assertEquals(AiProviderErrorCode.EncryptionKeyRequired, error.code)
            assertEquals(400, error.statusCode)
            val message = error.message.orEmpty()
            // The raw body must not ride along on the exception.
            assertFalse(message, message.contains("AI_PROVIDER_ENCRYPTION_KEY"))
            assertFalse(message, message.contains("gAAAA"))
            assertFalse(message, message.contains("sk-leak123"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testBeforeSaveMapsTo409Code() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"no config"}"""))
        server.start()
        try {
            client().testAiProviderConfig(server.url("/").toString(), "operator-token")
            fail("expected AiProviderConfigException")
        } catch (error: AiProviderConfigException) {
            assertEquals(AiProviderErrorCode.TestBeforeSave, error.code)
        } finally {
            server.shutdown()
        }
    }
}
