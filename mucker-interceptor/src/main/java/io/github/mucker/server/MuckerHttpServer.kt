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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    private val cdpHandler = CdpHandler(engine)
    private val restHandler = RestHandler(engine, port, context)

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

            // Serve Dashboard Web Assets
            val cleanPath = uri.split("?")[0]
            if (method == "GET" && (cleanPath == "/" || cleanPath == "/index.html")) {
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

    private fun sendHttpResponse(out: OutputStream, statusCode: Int, contentType: String, body: String) {
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
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
