package io.cpamonitor.android.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadOnlyPathInterceptorTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().addInterceptor(ReadOnlyPathInterceptor()).build()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun allowsWhitelistedReadAndAnalyticsPost() {
        server.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(server.url("/status")).build()).execute().use {
            assertEquals(200, it.code)
        }
    }

    @Test
    fun rejectsManagementWritesBeforeNetwork() {
        val request = Request.Builder().url(server.url("/v0/management/auth-files")).delete().build()
        val error = assertThrows(IllegalStateException::class.java) {
            client.newCall(request).execute()
        }
        assertTrue(error.message!!.contains("Blocked"))
        assertEquals(0, server.requestCount)
    }
}
