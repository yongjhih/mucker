package io.github.mucker.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.Base64

object NetworkUtils {

    /**
     * Resolves the primary local IPv4 address (e.g. Wi-Fi IP or hotspot IP).
     */
    fun getLocalIpAddress(context: Context? = null): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "127.0.0.1"
    }

    fun decodeBase64(str: String): String {
        return try {
            val bytes = android.util.Base64.decode(str, android.util.Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            str
        }
    }

    fun encodeBase64(str: String): String {
        return try {
            android.util.Base64.encodeToString(str.toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            str
        }
    }
}
