package io.github.mucker.server

import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.CdpMessages
import io.github.mucker.core.models.MockDecision
import io.github.mucker.util.NetworkUtils
import org.json.JSONObject

/**
 * Handles incoming JSON-RPC 2.0 messages according to the Chrome DevTools Protocol Fetch & Network domains.
 */
class CdpHandler(private val engine: MockEngine) {

    fun handleMessage(rawMessage: String, connection: WebSocketConnection) {
        try {
            val json = JSONObject(rawMessage)
            val id = json.optLong("id", -1L)
            val method = json.optString("method", "")
            val params = json.optJSONObject("params") ?: JSONObject()

            when (method) {
                "Fetch.enable" -> {
                    val patternsList = mutableListOf<String>()
                    if (params.has("patterns")) {
                        val patternsArray = params.getJSONArray("patterns")
                        for (i in 0 until patternsArray.length()) {
                            val p = patternsArray.getJSONObject(i)
                            patternsList.add(p.optString("urlPattern", "*"))
                        }
                    }
                    if (patternsList.isEmpty()) {
                        patternsList.add("*")
                    }
                    engine.enableInterception(patternsList)
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                "Fetch.disable" -> {
                    engine.disableInterception()
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                "Fetch.fulfillRequest" -> {
                    val requestId = params.getString("requestId")
                    val code = params.optInt("responseCode", 200)
                    val rawBody = params.optString("body", "")
                    val isBase64 = params.optBoolean("bodyIsBase64", true)

                    val body = if (isBase64 && rawBody.isNotEmpty()) {
                        NetworkUtils.decodeBase64(rawBody)
                    } else {
                        rawBody
                    }

                    val headersMap = mutableMapOf<String, String>()
                    if (params.has("responseHeaders")) {
                        val hArray = params.getJSONArray("responseHeaders")
                        for (i in 0 until hArray.length()) {
                            val hObj = hArray.getJSONObject(i)
                            headersMap[hObj.getString("name")] = hObj.getString("value")
                        }
                    } else {
                        headersMap["Content-Type"] = "application/json"
                    }
                    headersMap["X-Mocked-By"] = "CDP-Fetch"

                    engine.fulfill(requestId, MockDecision.Fulfill(code, headersMap, body))
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                "Fetch.continueRequest" -> {
                    val requestId = params.getString("requestId")
                    engine.continueRequest(requestId)
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                "Fetch.failRequest" -> {
                    val requestId = params.getString("requestId")
                    val reason = params.optString("errorReason", "Failed")
                    engine.failRequest(requestId, reason)
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                // Chrome DevTools / Puppeteer handshake compatibility stubs
                "Network.enable", "Page.enable", "Runtime.enable", "Log.enable", "DOM.enable" -> {
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }

                else -> {
                    // Unknown method fallback to success for CDP compatibility
                    if (id != -1L) connection.sendText(CdpMessages.createRpcSuccess(id))
                }
            }
        } catch (e: Exception) {
            // Malformed message
        }
    }
}
