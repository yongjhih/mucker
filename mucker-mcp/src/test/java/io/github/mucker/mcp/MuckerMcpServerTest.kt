package io.github.mucker.mcp

import io.github.mucker.core.MockEngine
import io.github.mucker.server.MuckerHttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

class MuckerMcpServerTest {

    private var serverPort: Int = 0
    private lateinit var engine: MockEngine
    private lateinit var server: MuckerHttpServer
    private lateinit var plugin: MuckerMcpPlugin

    @Before
    fun setUp() {
        val tempSocket = ServerSocket(0)
        serverPort = tempSocket.localPort
        tempSocket.close()

        engine = MockEngine()
        server = MuckerHttpServer(port = serverPort, engine = engine, context = null)
        plugin = MuckerMcp.install(server, engine, null)
        server.start()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun testPostMcpStreamableHttp() {
        val url = URL("http://127.0.0.1:$serverPort/mcp")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")

        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", "mcp-test-1")
            put("method", "tools/list")
        }.toString()

        conn.outputStream.use { os ->
            os.write(payload.toByteArray(StandardCharsets.UTF_8))
            os.flush()
        }

        assertEquals(200, conn.responseCode)
        val responseBody = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val json = JSONObject(responseBody)
        assertEquals("mcp-test-1", json.getString("id"))
        assertTrue(json.has("result"))
        assertTrue(json.getJSONObject("result").getJSONArray("tools").length() >= 11)
    }

    @Test
    fun testGetMcpDiscoveryInfo() {
        val url = URL("http://127.0.0.1:$serverPort/mcp")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val json = JSONObject(body)
        assertEquals("mucker-mcp-server", json.getString("service"))
        assertTrue(json.has("endpoints"))
    }

    @Test
    fun testSseEndpointAndClientMessageFlow() {
        // 1. Connect to GET /sse via socket
        val sseSocket = Socket("127.0.0.1", serverPort)
        val sseOut = OutputStreamWriter(sseSocket.getOutputStream(), StandardCharsets.UTF_8)
        val sseIn = BufferedReader(InputStreamReader(sseSocket.getInputStream(), StandardCharsets.UTF_8))

        sseOut.write("GET /sse HTTP/1.1\r\nHost: 127.0.0.1:$serverPort\r\nAccept: text/event-stream\r\n\r\n")
        sseOut.flush()

        // Read headers
        val statusLine = sseIn.readLine()
        assertTrue(statusLine.contains("200 OK"))

        var line: String?
        var isEventStream = false
        while (sseIn.readLine().also { line = it } != null) {
            if (line.isNullOrEmpty()) break
            if (line!!.lowercase().contains("text/event-stream")) {
                isEventStream = true
            }
        }
        assertTrue("Expected text/event-stream content type", isEventStream)

        // Read initial endpoint event: "event: endpoint\ndata: /message?sessionId=...\n\n"
        val eventLine = sseIn.readLine()
        assertEquals("event: endpoint", eventLine)
        val dataLine = sseIn.readLine()
        assertTrue(dataLine.startsWith("data: /message?sessionId="))
        val messageEndpoint = dataLine.removePrefix("data: ").trim()
        val emptyLine = sseIn.readLine()
        assertEquals("", emptyLine)

        // 2. Client sends a POST to the received message endpoint
        val msgUrl = URL("http://127.0.0.1:$serverPort$messageEndpoint")
        val msgConn = msgUrl.openConnection() as HttpURLConnection
        msgConn.requestMethod = "POST"
        msgConn.doOutput = true
        msgConn.setRequestProperty("Content-Type", "application/json")

        val rpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 88)
            put("method", "ping")
        }.toString()

        msgConn.outputStream.use { os ->
            os.write(rpcPayload.toByteArray(StandardCharsets.UTF_8))
            os.flush()
        }

        assertEquals(202, msgConn.responseCode)

        // 3. Verify event is delivered asynchronously to the open SSE stream
        val sseMessageEvent = sseIn.readLine()
        assertEquals("event: message", sseMessageEvent)
        val sseMessageData = sseIn.readLine()
        assertTrue(sseMessageData.startsWith("data: {"))
        val rpcResult = JSONObject(sseMessageData.removePrefix("data: ").trim())
        assertEquals(88, rpcResult.getInt("id"))
        assertTrue(rpcResult.has("result"))

        sseSocket.close()
    }
}
