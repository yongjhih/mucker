package io.github.mucker.server

import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Manages an active Server-Sent Events (SSE) connection for Model Context Protocol (MCP) streaming.
 */
class SseConnection(
    val sessionId: String,
    val socket: Socket,
    private val outStream: OutputStream
) {
    val isClosed: Boolean get() = socket.isClosed

    @Synchronized
    fun sendEvent(event: String, data: String) {
        if (socket.isClosed) return
        try {
            val message = "event: $event\r\ndata: $data\r\n\r\n"
            outStream.write(message.toByteArray(StandardCharsets.UTF_8))
            outStream.flush()
        } catch (_: Exception) {
            close()
        }
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
