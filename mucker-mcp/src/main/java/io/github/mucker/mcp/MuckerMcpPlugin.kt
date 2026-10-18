package io.github.mucker.mcp

import android.content.Context
import io.github.mucker.core.MockEngine
import io.github.mucker.server.MuckerHttpServer
import io.github.mucker.server.MuckerServerPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Idiomatic, Coroutines-powered Model Context Protocol (MCP) server plugin for Mucker.
 * Handles Streamable HTTP (POST /mcp) and Server-Sent Events (GET /sse, POST /message).
 */
class MuckerMcpPlugin(
    private val engine: MockEngine,
    private val port: Int,
    private val context: Context?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : MuckerServerPlugin {

    val handler = McpHandler(engine, port, context)

    private class SseSession(
        val sessionId: String,
        val socket: Socket,
        val channel: Channel<String>,
        val job: Job
    )

    private val activeSessions = ConcurrentHashMap<String, SseSession>()

    override fun handle(
        method: String,
        uri: String,
        cleanPath: String,
        headers: Map<String, String>,
        body: String,
        socket: Socket,
        outStream: OutputStream
    ): Boolean {
        return when {
            // Streamable HTTP: POST /mcp or POST /api/mcp
            (cleanPath == "/mcp" || cleanPath == "/api/mcp") && method == "POST" -> {
                val response = handler.handleMessage(body)
                sendHttpResponse(outStream, 200, "application/json; charset=utf-8", response)
                socket.close()
                true
            }

            // Discovery: GET /mcp or GET /api/mcp
            (cleanPath == "/mcp" || cleanPath == "/api/mcp") && method == "GET" -> {
                val discovery = JSONObject().apply {
                    put("service", "mucker-mcp-server")
                    put("version", "1.0.0")
                    put("transports", JSONArray(listOf("streamable-http", "sse")))
                    put("endpoints", JSONObject().apply {
                        put("http", "/mcp")
                        put("sse", "/sse")
                        put("message", "/message")
                    })
                }
                sendHttpResponse(outStream, 200, "application/json; charset=utf-8", discovery.toString(2))
                socket.close()
                true
            }

            // SSE Stream: GET /sse or GET /mcp/sse
            (cleanPath == "/sse" || cleanPath == "/mcp/sse") && method == "GET" -> {
                handleSseConnect(socket, outStream)
                true
            }

            // SSE Client Message: POST /message or POST /messages
            (cleanPath == "/message" || cleanPath == "/messages") && method == "POST" -> {
                handleSsePostMessage(uri, body, socket, outStream)
                true
            }

            else -> false
        }
    }

    private fun handleSseConnect(socket: Socket, outStream: OutputStream) {
        val sessionId = UUID.randomUUID().toString()
        val eventChannel = Channel<String>(Channel.BUFFERED)

        val job = scope.launch {
            try {
                // 1. Initial SSE Header
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/event-stream\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Connection: keep-alive\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Headers: *\r\n\r\n"
                outStream.write(header.toByteArray(StandardCharsets.UTF_8))
                outStream.flush()

                // 2. Announce message POST endpoint with sessionId per MCP SSE spec
                val endpointEvent = "event: endpoint\r\ndata: /message?sessionId=$sessionId\r\n\r\n"
                outStream.write(endpointEvent.toByteArray(StandardCharsets.UTF_8))
                outStream.flush()

                // 3. Consume channel events and push over SSE
                for (msg in eventChannel) {
                    if (socket.isClosed) break
                    val event = "event: message\r\ndata: $msg\r\n\r\n"
                    outStream.write(event.toByteArray(StandardCharsets.UTF_8))
                    outStream.flush()
                }
            } catch (_: Exception) {
            } finally {
                activeSessions.remove(sessionId)
                eventChannel.close()
                try { socket.close() } catch (_: Exception) {}
            }
        }

        val session = SseSession(sessionId, socket, eventChannel, job)
        activeSessions[sessionId] = session

        // Monitor client disconnect in background
        scope.launch {
            try {
                val inStream = socket.getInputStream()
                val buf = ByteArray(128)
                while (!socket.isClosed) {
                    val r = inStream.read(buf)
                    if (r == -1) break
                }
            } catch (_: Exception) {
            } finally {
                session.job.cancel()
                session.channel.close()
                activeSessions.remove(sessionId)
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleSsePostMessage(uri: String, body: String, socket: Socket, outStream: OutputStream) {
        val sessionId = parseQueryParam(uri, "sessionId")
        val session = sessionId?.let { activeSessions[it] }

        val result = handler.handleMessage(body)

        if (session != null && !session.socket.isClosed) {
            if (result.isNotEmpty()) {
                session.channel.trySend(result)
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

    private fun parseQueryParam(uri: String, key: String): String? {
        val qIdx = uri.indexOf('?')
        if (qIdx == -1 || qIdx >= uri.length - 1) return null
        val query = uri.substring(qIdx + 1)
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts[0] == key) {
                return if (parts.size > 1) parts[1] else ""
            }
        }
        return null
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

    override fun onServerStop() {
        activeSessions.values.forEach { session ->
            session.job.cancel()
            session.channel.close()
            try { session.socket.close() } catch (_: Exception) {}
        }
        activeSessions.clear()
        scope.cancel()
    }
}
