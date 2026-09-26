package io.github.mucker.server

import android.content.Context
import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.MockRule
import io.github.mucker.util.NetworkUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles HTTP REST endpoints for Mucker Dashboard, CLI, and CDP Discovery.
 */
class RestHandler(
    private val engine: MockEngine,
    private val port: Int,
    private val context: Context?
) {
    data class HttpResponse(
        val statusCode: Int = 200,
        val contentType: String = "application/json; charset=utf-8",
        val body: String = ""
    )

    fun handle(method: String, path: String, body: String): HttpResponse {
        val cleanPath = path.split("?")[0].trimEnd('/')

        return when {
            // CDP Discovery Endpoints
            method == "GET" && cleanPath == "/json/version" -> {
                val json = JSONObject()
                json.put("Browser", "Mucker/1.0.0")
                json.put("Protocol-Version", "1.3")
                json.put("User-Agent", "Mucker In-App DevTools")
                json.put("V8-Version", "1.0.0")
                json.put("webSocketDebuggerUrl", "ws://${getWsHost()}/devtools/page")
                HttpResponse(200, "application/json", json.toString())
            }

            method == "GET" && (cleanPath == "/json/list" || cleanPath == "/json") -> {
                val array = JSONArray()
                val target = JSONObject()
                target.put("id", "mucker-inapp-target")
                target.put("type", "page")
                target.put("title", "Mucker DevTools (${context?.packageName ?: "Android App"})")
                target.put("description", "Mucker In-App OkHttp Mock Engine")
                target.put("devtoolsFrontendUrl", "devtools/inspector.html?ws=${getWsHost()}/devtools/page")
                target.put("webSocketDebuggerUrl", "ws://${getWsHost()}/devtools/page")
                target.put("url", "http://${getWsHost()}/")
                array.put(target)
                HttpResponse(200, "application/json", array.toString())
            }

            // Status Endpoints
            method == "GET" && cleanPath == "/api/status" -> {
                val json = JSONObject()
                json.put("status", "running")
                json.put("version", "1.0.0")
                json.put("port", port)
                json.put("app", context?.packageName ?: "Android App")
                json.put("breakpointMode", engine.breakpointMode)
                json.put("activeRulesCount", engine.rules.getAllRules().size)
                json.put("pausedRequestsCount", engine.getPendingPausedCount())
                HttpResponse(200, "application/json", json.toString())
            }

            method == "POST" && cleanPath == "/api/status" -> {
                try {
                    val req = JSONObject(body)
                    if (req.has("breakpointMode")) {
                        engine.breakpointMode = req.getBoolean("breakpointMode")
                    }
                    HttpResponse(200, "application/json", JSONObject().put("success", true).toString())
                } catch (e: Exception) {
                    HttpResponse(400, "application/json", JSONObject().put("error", e.message).toString())
                }
            }

            // Rules Endpoints
            method == "GET" && cleanPath == "/api/rules" -> {
                HttpResponse(200, "application/json", engine.rules.toJson().toString())
            }

            method == "POST" && cleanPath == "/api/rules" -> {
                try {
                    val reqJson = JSONObject(body)
                    val rule = MockRule.fromJson(reqJson)
                    engine.rules.addRule(rule)
                    HttpResponse(200, "application/json", rule.toJson().toString())
                } catch (e: Exception) {
                    HttpResponse(400, "application/json", JSONObject().put("error", e.message).toString())
                }
            }

            method == "DELETE" && cleanPath == "/api/rules" -> {
                engine.rules.clearRules()
                HttpResponse(200, "application/json", JSONObject().put("success", true).toString())
            }

            method == "DELETE" && cleanPath.startsWith("/api/rules/") -> {
                val id = cleanPath.removePrefix("/api/rules/")
                val removed = engine.rules.removeRule(id)
                HttpResponse(200, "application/json", JSONObject().put("success", removed).toString())
            }

            method == "POST" && cleanPath == "/api/rules/toggle" -> {
                try {
                    val req = JSONObject(body)
                    val id = req.getString("id")
                    val isEnabled = req.getBoolean("isEnabled")
                    val ok = engine.rules.toggleRule(id, isEnabled)
                    HttpResponse(200, "application/json", JSONObject().put("success", ok).toString())
                } catch (e: Exception) {
                    HttpResponse(400, "application/json", JSONObject().put("error", e.message).toString())
                }
            }

            // History Endpoints
            method == "GET" && cleanPath == "/api/history" -> {
                HttpResponse(200, "application/json", engine.history.toJson().toString())
            }

            method == "DELETE" && cleanPath == "/api/history" -> {
                engine.history.clear()
                HttpResponse(200, "application/json", JSONObject().put("success", true).toString())
            }

            // Paused Request Endpoints
            method == "POST" && cleanPath.startsWith("/api/paused/") && cleanPath.endsWith("/fulfill") -> {
                val reqId = cleanPath.removePrefix("/api/paused/").removeSuffix("/fulfill")
                try {
                    val req = JSONObject(body)
                    val statusCode = req.optInt("statusCode", 200)
                    val resBody = req.optString("responseBody", "")
                    val headersMap = mutableMapOf<String, String>()
                    if (req.has("responseHeaders")) {
                        val h = req.getJSONObject("responseHeaders")
                        val keys = h.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            headersMap[k] = h.getString(k)
                        }
                    } else {
                        headersMap["Content-Type"] = "application/json"
                    }
                    headersMap["X-Mocked-By"] = "Dashboard"

                    val ok = engine.fulfill(reqId, MockDecision.Fulfill(statusCode, headersMap, resBody))
                    HttpResponse(200, "application/json", JSONObject().put("success", ok).toString())
                } catch (e: Exception) {
                    HttpResponse(400, "application/json", JSONObject().put("error", e.message).toString())
                }
            }

            method == "POST" && cleanPath.startsWith("/api/paused/") && cleanPath.endsWith("/continue") -> {
                val reqId = cleanPath.removePrefix("/api/paused/").removeSuffix("/continue")
                val ok = engine.continueRequest(reqId)
                HttpResponse(200, "application/json", JSONObject().put("success", ok).toString())
            }

            else -> HttpResponse(404, "application/json", JSONObject().put("error", "Not Found").toString())
        }
    }

    private fun getWsHost(): String {
        val ip = NetworkUtils.getLocalIpAddress(context)
        return "$ip:$port"
    }
}
