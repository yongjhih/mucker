package io.github.mucker.server

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Lightweight, robust RFC 6455 WebSocket connection handler.
 */
class WebSocketConnection(
    private val socket: Socket,
    private val onMessage: (String, WebSocketConnection) -> Unit,
    private val onClose: (WebSocketConnection) -> Unit
) {
    private val inStream: InputStream = socket.getInputStream()
    private val outStream: OutputStream = socket.getOutputStream()
    @Volatile var isClosed: Boolean = false
        private set

    fun startListening() {
        Thread({
            try {
                while (!isClosed && !socket.isClosed) {
                    val message = readFrame() ?: break
                    onMessage(message, this)
                }
            } catch (_: Exception) {
            } finally {
                close()
            }
        }, "mucker-ws-${socket.port}").start()
    }

    @Synchronized
    fun sendText(text: String) {
        if (isClosed || socket.isClosed) return
        try {
            val payload = text.toByteArray(StandardCharsets.UTF_8)
            val len = payload.size

            outStream.write(0x81) // FIN + text opcode

            if (len < 126) {
                outStream.write(len)
            } else if (len <= 65535) {
                outStream.write(126)
                outStream.write((len shr 8) and 0xFF)
                outStream.write(len and 0xFF)
            } else {
                outStream.write(127)
                for (i in 7 downTo 0) {
                    outStream.write(((len.toLong() shr (8 * i)) and 0xFF).toInt())
                }
            }

            outStream.write(payload)
            outStream.flush()
        } catch (_: Exception) {
            close()
        }
    }

    private fun readFrame(): String? {
        val b0 = inStream.read()
        if (b0 == -1) return null

        val opcode = b0 and 0x0F
        if (opcode == 0x08) { // Close frame
            return null
        }

        val b1 = inStream.read()
        if (b1 == -1) return null

        val isMasked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()

        if (payloadLen == 126L) {
            val byte1 = inStream.read()
            val byte2 = inStream.read()
            if (byte1 == -1 || byte2 == -1) return null
            payloadLen = ((byte1 shl 8) or byte2).toLong()
        } else if (payloadLen == 127L) {
            payloadLen = 0
            for (i in 0 until 8) {
                val b = inStream.read()
                if (b == -1) return null
                payloadLen = (payloadLen shl 8) or b.toLong()
            }
        }

        val maskingKey = ByteArray(4)
        if (isMasked) {
            var read = 0
            while (read < 4) {
                val r = inStream.read(maskingKey, read, 4 - read)
                if (r == -1) return null
                read += r
            }
        }

        val payload = ByteArray(payloadLen.toInt())
        var totalRead = 0
        while (totalRead < payload.size) {
            val r = inStream.read(payload, totalRead, payload.size - totalRead)
            if (r == -1) return null
            totalRead += r
        }

        if (isMasked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
            }
        }

        return String(payload, StandardCharsets.UTF_8)
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            socket.close()
        } catch (_: Exception) {}
        onClose(this)
    }
}
