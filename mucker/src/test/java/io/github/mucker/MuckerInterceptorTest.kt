package io.github.mucker

import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.MockRule
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

class MuckerInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var engine: MockEngine
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        engine = MockEngine()
        val interceptor = MuckerInterceptor(engine = engine, timeoutSeconds = 5L)

        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testMockRuleReturnsMockedResponseDirectly() {
        val serverUrl = mockWebServer.url("/api/v1/user/profile").toString()

        // Configure mock rule
        engine.rules.addRule(
            MockRule(
                id = "rule_user",
                urlPattern = ".*/api/v1/user/profile.*",
                method = "GET",
                statusCode = 200,
                responseBody = "{\"mocked_user\":\"Grace Hopper\"}",
                responseHeaders = mapOf("Content-Type" to "application/json", "X-Custom-Test" to "Mucker-Test")
            )
        )

        val request = Request.Builder().url(serverUrl).get().build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("Mucker-Rule", response.header("X-Mocked-By"))
        assertEquals("Mucker-Test", response.header("X-Custom-Test"))
        assertEquals("{\"mocked_user\":\"Grace Hopper\"}", response.body?.string())

        // Ensure real mockWebServer received 0 requests because Mucker intercepted it!
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun testPassThroughWhenNoRuleMatches() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"real\":\"data from server\"}")
        )

        val serverUrl = mockWebServer.url("/real/api").toString()
        val request = Request.Builder().url(serverUrl).get().build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(null, response.header("X-Mocked-By"))
        assertEquals("{\"real\":\"data from server\"}", response.body?.string())

        // mockWebServer should have received exactly 1 request
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun testBreakpointModeInterceptionAndFulfill() {
        val serverUrl = mockWebServer.url("/breakpoint/test").toString()
        engine.enableInterception(listOf("*breakpoint*"))

        thread {
            // Wait slightly for request to hit breakpoint
            Thread.sleep(150)
            val historyList = engine.history.getAll()
            val paused = historyList.firstOrNull { it.isPaused }
            if (paused != null) {
                engine.fulfill(
                    requestId = paused.id,
                    decision = MockDecision.Fulfill(
                        statusCode = 202,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = "{\"breakpoint\":\"fulfilled\"}"
                    )
                )
            }
        }

        val request = Request.Builder().url(serverUrl).get().build()
        val response = client.newCall(request).execute()

        assertEquals(202, response.code)
        assertEquals("Mucker-Breakpoint", response.header("X-Mocked-By"))
        assertEquals("{\"breakpoint\":\"fulfilled\"}", response.body?.string())
        assertEquals(0, mockWebServer.requestCount)
    }
}
