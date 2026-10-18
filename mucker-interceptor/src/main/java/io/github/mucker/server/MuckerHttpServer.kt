package io.github.mucker.server

import android.content.Context
import android.util.Base64
import io.github.mucker.core.MockEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Lightweight, zero-dependency HTTP & WebSocket server running inside the Android application.
 */
class MuckerHttpServer(
    val port: Int = 8080,
    private val engine: MockEngine,
    private val context: Context?
) {
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val activeWebSockets = CopyOnWriteArrayList<WebSocketConnection>()
    private val activeSseSessions = ConcurrentHashMap<String, SseConnection>()
    private val cdpHandler = CdpHandler(engine)
    private val restHandler = RestHandler(engine, port, context)
    val mcpHandler = McpHandler(engine, port, context)

    init {
        // Wire engine broadcast to all active WebSocket clients
        engine.eventBroadcaster = { jsonString ->
            broadcastWebSocket(jsonString)
        }
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true

        try {
            serverSocket = ServerSocket(port)
            serverSocket?.reuseAddress = true

            executor.execute {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        executor.execute {
                            handleClient(clientSocket)
                        }
                    } catch (_: Exception) {
                        // socket closed on stop
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            activeWebSockets.forEach { it.close() }
            activeWebSockets.clear()
            activeSseSessions.values.forEach { it.close() }
            activeSseSessions.clear()
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
    }

    private fun broadcastWebSocket(text: String) {
        activeWebSockets.forEach { ws ->
            if (!ws.isClosed) {
                ws.sendText(text)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val inReader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val initialLine = inReader.readLine() ?: return
            val parts = initialLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uri = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (inReader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val colonIdx = line!!.indexOf(':')
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            // Check WebSocket Upgrade
            val isWsUpgrade = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
            if (isWsUpgrade && uri.startsWith("/devtools/page")) {
                handleWebSocketHandshake(socket, headers)
                return
            }

            val outStream = socket.getOutputStream()

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendCorsPreflight(outStream)
                socket.close()
                return
            }

            // Read request body if present
            var body = ""
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength > 0) {
                val charBuf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = inReader.read(charBuf, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                body = String(charBuf, 0, readTotal)
            }

            val cleanPath = uri.split("?")[0].trimEnd('/')

            // Handle MCP Streamable HTTP POST/GET (/mcp, /api/mcp)
            if (cleanPath == "/mcp" || cleanPath == "/api/mcp") {
                if (method == "POST") {
                    val response = mcpHandler.handleMessage(body)
                    sendHttpResponse(outStream, 200, "application/json; charset=utf-8", response)
                } else {
                    val info = JSONObject().apply {
                        put("service", "mucker-mcp-server")
                        put("version", "1.0.0")
                        put("transport", org.json.JSONArray(listOf("streamable-http", "sse")))
                        put("endpoints", JSONObject().apply {
                            put("http", "/mcp")
                            put("sse", "/sse")
                            put("message", "/message")
                        })
                    }
                    sendHttpResponse(outStream, 200, "application/json; charset=utf-8", info.toString(2))
                }
                socket.close()
                return
            }

            // Handle MCP SSE Streaming (/sse, /mcp/sse)
            if (method == "GET" && (cleanPath == "/sse" || cleanPath == "/mcp/sse")) {
                handleSseConnection(socket, outStream)
                return
            }

            // Handle MCP SSE Client Messages (/message, /messages)
            if (method == "POST" && (cleanPath == "/message" || cleanPath == "/messages")) {
                handleSseMessage(socket, uri, body, outStream)
                return
            }

            // Serve Dashboard Web Assets
            if (method == "GET" && (cleanPath == "" || cleanPath == "/index.html")) {
                serveDashboardAsset(outStream)
                socket.close()
                return
            }

            // Route to REST Handler
            val response = restHandler.handle(method, uri, body)
            sendHttpResponse(outStream, response.statusCode, response.contentType, response.body)
            socket.close()

        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleWebSocketHandshake(socket: Socket, headers: Map<String, String>) {
        val key = headers["sec-websocket-key"] ?: return
        val acceptKey = computeWebSocketAccept(key)

        val out = socket.getOutputStream()
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        out.write(response.toByteArray(StandardCharsets.UTF_8))
        out.flush()

        val ws = WebSocketConnection(
            socket = socket,
            onMessage = { msg, connection ->
                cdpHandler.handleMessage(msg, connection)
            },
            onClose = { connection ->
                activeWebSockets.remove(connection)
            }
        )

        activeWebSockets.add(ws)
        ws.startListening()
    }

    private fun computeWebSocketAccept(key: String): String {
        val magic = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(magic.toByteArray(StandardCharsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(hash)
    }

    private fun serveDashboardAsset(out: OutputStream) {
        try {
            var html = ""
            if (context != null) {
                try {
                    context.assets.open("mucker-web/index.html").use { input ->
                        html = input.bufferedReader(StandardCharsets.UTF_8).readText()
                    }
                } catch (_: Exception) {}
            }

            if (html.isEmpty()) {
                html = "<html><body><h2>Mucker Engine Running</h2><p>Dashboard asset not found in bundle.</p></body></html>"
            }

            sendHttpResponse(out, 200, "text/html; charset=utf-8", html)
        } catch (_: Exception) {
            sendHttpResponse(out, 500, "text/plain", "Failed to load dashboard")
        }
    }

    private fun handleSseConnection(socket: Socket, outStream: OutputStream) {
        val sessionId = UUID.randomUUID().toString()
        val sseConn = SseConnection(sessionId, socket, outStream)
        activeSseSessions[sessionId] = sseConn

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: keep-alive\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n\r\n"
        outStream.write(header.toByteArray(StandardCharsets.UTF_8))
        outStream.flush()

        // Announce message endpoint with sessionId for incoming client JSON-RPC calls
        sseConn.sendEvent("endpoint", "/message?sessionId=$sessionId")

        try {
            val inStream = socket.getInputStream()
            val buf = ByteArray(128)
            while (isRunning && !socket.isClosed) {
                val read = inStream.read(buf)
                if (read == -1) break
            }
        } catch (_: Exception) {
        } finally {
            activeSseSessions.remove(sessionId)
            sseConn.close()
        }
    }

    private fun handleSseMessage(socket: Socket, uri: String, body: String, outStream: OutputStream) {
        val queryParams = parseQueryParams(uri)
        val sessionId = queryParams["sessionId"]
        val sseConn = sessionId?.let { activeSseSessions[it] }

        val result = mcpHandler.handleMessage(body)

        if (sseConn != null && !sseConn.isClosed) {
            if (result.isNotEmpty()) {
                sseConn.sendEvent("message", result)
            }
            sendHttpResponse(outStream, 202, "text/plain; charset=utf-8", "Accepted")
        } else {
            if (result.isNotEmpty()) {
                sendHttpResponse(outStream, 200, "application/json; charset=utf-8", result)
            } else {
                sendHttpResponse(outStream, 204, "text/plain", "")
            }
        }
        socket.close()
    }

    private fun parseQueryParams(uri: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val qIdx = uri.indexOf('?')
        if (qIdx != -1 && qIdx < uri.length - 1) {
            val query = uri.substring(qIdx + 1)
            query.split("&").forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.isNotEmpty()) {
                    val key = kv[0]
                    val value = if (kv.size > 1) kv[1] else ""
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun sendHttpResponse(out: OutputStream, statusCode: Int, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"

        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.write(bodyBytes)
        out.flush()
    }

    private fun sendCorsPreflight(out: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }
}
