package io.cpamonitor.android.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class ApiFactoryMockWebServerTest {
    private lateinit var server: MockWebServer
    private val factory = ApiFactory(Json { ignoreUnknownKeys = true })

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun parsesStatusAndSendsBearerWithoutLoggingBodies() = runTest {
        server.enqueue(MockResponse().setBody("""{"service":"cpamp","collector":{"collector":"running"},"events":12}"""))
        val result = factory.create(server.url("/").toString(), "secret-key").status()
        assertEquals(12, result.events)
        assertEquals("running", result.collector.collector)
        assertEquals("Bearer secret-key", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun reusesOneClientSessionAndReplacesChangedCredentials() {
        val baseUrl = server.url("/").toString()

        val first = factory.create(baseUrl, "secret-key")
        assertSame(first, factory.create(baseUrl, "secret-key"))
        assertNotSame(first, factory.create(baseUrl, "rotated-key"))
        factory.invalidate()
    }

    @Test
    fun publicInfoProbeNeverReceivesAdminKey() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"service":"cpa-manager-plus","mode":"manager","configured":true,"adminReady":true}""",
            ),
        )

        factory.create(server.url("/").toString(), "secret-key").info()

        assertEquals(null, server.takeRequest().headers["Authorization"])
    }

    @Test
    fun redirectsAreNotFollowedWithAdminCredentials() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(307)
                .setHeader("Location", server.url("/redirect-target")),
        )
        val api = factory.create(server.url("/").toString(), "secret-key")

        val redirect = assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking { api.status() }
        }

        assertEquals(307, redirect.code())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsChunkedResponsesThatExceedSafetyLimit() {
        server.enqueue(MockResponse().setChunkedBody("123456789", 3))
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(ResponseSizeLimitInterceptor(maxBytes = 8))
            .build()

        assertThrows(java.io.IOException::class.java) {
            client.newCall(Request.Builder().url(server.url("/status")).build()).execute().use {
                it.body.string()
            }
        }
    }

    @Test
    fun parsesOfficialCpampStatusShape() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "service":"cpa-manager-plus",
                  "events":18462,
                  "deadLetters":2,
                  "collector":{
                    "collector":"running",
                    "upstream":"https://cpa.example",
                    "mode":"auto",
                    "transport":"http",
                    "queue":"usage",
                    "lastConsumedAt":1787990000000,
                    "lastInsertedAt":1787990000100,
                    "totalInserted":18462,
                    "totalSkipped":3,
                    "deadLetters":2
                  },
                  "futureStatus":{"enabled":true}
                }
                """.trimIndent(),
            ),
        )

        val result = factory.create(server.url("/").toString(), "secret-key").status()

        assertEquals("running", result.collector.collector)
        assertEquals("http", result.collector.transport)
        assertEquals("auto", result.collector.mode)
        assertEquals(18_462, result.collector.totalInserted)
        assertEquals(2, result.deadLetters)
    }

    @Test
    fun exposes401And404ForCapabilityMapping() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val api = factory.create(server.url("/").toString(), "bad")
        val unauthorized = assertThrows(HttpException::class.java) { kotlinx.coroutines.runBlocking { api.status() } }
        assertEquals(401, unauthorized.code())

        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val missing = assertThrows(HttpException::class.java) {
            kotlinx.coroutines.runBlocking {
                api.analytics(
                    AnalyticsRequest(1, 2, timeZone = "UTC", include = AnalyticsInclude(summary = true)),
                )
            }
        }
        assertEquals(404, missing.code())
    }

    @Test
    fun malformedJsonFailsClosed() = runTest {
        server.enqueue(MockResponse().setBody("{not-json"))
        val api = factory.create(server.url("/").toString(), "secret")
        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { api.status() } }
    }
}
