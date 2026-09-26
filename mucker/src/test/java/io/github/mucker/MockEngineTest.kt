package io.github.mucker

import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.NetworkRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MockEngineTest {

    private lateinit var engine: MockEngine

    @Before
    fun setUp() {
        engine = MockEngine()
    }

    @Test
    fun testBreakpointInterceptionPatternMatching() {
        assertFalse(engine.shouldIntercept("https://example.com/api/v1/user"))

        engine.enableInterception(listOf("*/api/v1/*"))
        assertTrue(engine.breakpointMode)
        assertTrue(engine.shouldIntercept("https://example.com/api/v1/user"))
        assertFalse(engine.shouldIntercept("https://example.com/static/image.png"))

        engine.disableInterception()
        assertFalse(engine.breakpointMode)
        assertFalse(engine.shouldIntercept("https://example.com/api/v1/user"))
    }

    @Test
    fun testPauseAndFulfillRequest() {
        val record = NetworkRecord(
            id = "test_req_1",
            url = "https://example.com/api/v1/test",
            method = "GET"
        )
        engine.history.record(record)

        var broadcastedEvent: String? = null
        engine.eventBroadcaster = { event -> broadcastedEvent = event }

        val future = engine.pauseRequest(record)
        assertEquals(1, engine.getPendingPausedCount())
        assertTrue(record.isPaused)
        assertTrue(broadcastedEvent?.contains("Fetch.requestPaused") == true)

        // Fulfill the paused request
        val fulfilled = engine.fulfill(
            requestId = "test_req_1",
            decision = MockDecision.Fulfill(
                statusCode = 200,
                headers = mapOf("X-Custom" to "123"),
                body = "{\"mocked\":true}"
            )
        )
        assertTrue(fulfilled)
        assertEquals(0, engine.getPendingPausedCount())

        val decision = future.get(1, TimeUnit.SECONDS)
        assertTrue(decision is MockDecision.Fulfill)
        assertEquals(200, (decision as MockDecision.Fulfill).statusCode)
        assertEquals("{\"mocked\":true}", decision.body)
        assertFalse(record.isPaused)
        assertTrue(record.isMocked)
    }

    @Test
    fun testPauseAndContinueRequest() {
        val record = NetworkRecord(
            id = "test_req_2",
            url = "https://example.com/api/v1/continue",
            method = "POST"
        )
        engine.history.record(record)

        val future = engine.pauseRequest(record)
        assertEquals(1, engine.getPendingPausedCount())

        val continued = engine.continueRequest("test_req_2")
        assertTrue(continued)
        assertEquals(0, engine.getPendingPausedCount())

        val decision = future.get(1, TimeUnit.SECONDS)
        assertTrue(decision is MockDecision.Continue)
        assertFalse(record.isPaused)
    }
}
