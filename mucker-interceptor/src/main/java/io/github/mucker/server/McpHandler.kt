package io.github.mucker.server

import android.content.Context
import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.MockRule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Handles Model Context Protocol (MCP) JSON-RPC 2.0 messages over Streamable HTTP and SSE.
 * Allows AI assistants (Claude Code, Cursor, Windsurf, local LLMs) to directly control
 * the in-app OkHttp mock engine running on the Android device or emulator.
 */
class McpHandler(
    private val engine: MockEngine,
    private val port: Int,
    private val context: Context?
) {

    /**
     * Handles an incoming MCP JSON-RPC message and returns the JSON-RPC response.
     */
    fun handleMessage(rawMessage: String): String {
        return try {
            val trimmed = rawMessage.trim()
            if (trimmed.isEmpty()) return createError(null, -32700, "Empty payload")
            if (trimmed.startsWith("[")) {
                // Batch request
                val array = JSONArray(trimmed)
                val results = JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val res = processSingleRequest(item)
                    if (res != null) results.put(JSONObject(res))
                }
                results.toString()
            } else {
                val json = JSONObject(trimmed)
                processSingleRequest(json) ?: ""
            }
        } catch (e: Exception) {
            createError(null, -32700, "Parse error: ${e.message}")
        }
    }

    private fun processSingleRequest(json: JSONObject): String? {
        val id = if (json.has("id")) json.get("id") else null
        val method = json.optString("method", "")
        val params = json.optJSONObject("params") ?: JSONObject()

        return when (method) {
            "initialize" -> handleInitialize(id, params)
            "notifications/initialized" -> {
                // Client acknowledgement, no response required if notification (id == null)
                if (id != null) createResult(id, JSONObject()) else null
            }
            "ping" -> createResult(id, JSONObject())
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolsCall(id, params)
            else -> createError(id, -32601, "Method not found: $method")
        }
    }

    private fun handleInitialize(id: Any?, params: JSONObject): String {
        val result = JSONObject()
        result.put("protocolVersion", "2024-11-05")

        val capabilities = JSONObject()
        val toolsCap = JSONObject()
        toolsCap.put("listChanged", false)
        capabilities.put("tools", toolsCap)
        result.put("capabilities", capabilities)

        val serverInfo = JSONObject()
        serverInfo.put("name", "mucker-android-interceptor")
        serverInfo.put("version", "1.0.0")
        result.put("serverInfo", serverInfo)

        return createResult(id, result)
    }

    private fun handleToolsList(id: Any?): String {
        val toolsArray = JSONArray()

        toolsArray.put(createToolDefinition(
            name = "get_status",
            description = "Get live Mucker server status, active mock rules count, breakpoint mode state, and pending requests.",
            inputProperties = mapOf()
        ))

        toolsArray.put(createToolDefinition(
            name = "list_rules",
            description = "List all registered mock rules currently active in Mucker memory.",
            inputProperties = mapOf()
        ))

        toolsArray.put(createToolDefinition(
            name = "add_rule",
            description = "Add or update a mock rule on the connected Mucker instance.",
            inputProperties = mapOf(
                "urlPattern" to mapOf("type" to "string", "description" to "URL pattern (glob or regex, e.g. *://api.example.com/v1/user*)"),
                "method" to mapOf("type" to "string", "description" to "HTTP method: ALL, GET, POST, PUT, DELETE, PATCH"),
                "statusCode" to mapOf("type" to "integer", "description" to "HTTP status code (default: 200)"),
                "responseBody" to mapOf("type" to "string", "description" to "Mocked JSON/text response body"),
                "delayMs" to mapOf("type" to "integer", "description" to "Artificial response delay in milliseconds")
            ),
            required = listOf("urlPattern")
        ))

        toolsArray.put(createToolDefinition(
            name = "remove_rule",
            description = "Delete a mock rule by ID from Mucker memory.",
            inputProperties = mapOf(
                "id" to mapOf("type" to "string", "description" to "ID of the mock rule to remove")
            ),
            required = listOf("id")
        ))

        toolsArray.put(createToolDefinition(
            name = "clear_rules",
            description = "Clear all registered mock rules in Mucker memory.",
            inputProperties = mapOf()
        ))

        toolsArray.put(createToolDefinition(
            name = "toggle_rule",
            description = "Enable or disable a specific mock rule by ID.",
            inputProperties = mapOf(
                "id" to mapOf("type" to "string", "description" to "ID of the rule"),
                "isEnabled" to mapOf("type" to "boolean", "description" to "true to enable, false to disable")
            ),
            required = listOf("id", "isEnabled")
        ))

        toolsArray.put(createToolDefinition(
            name = "set_breakpoint_mode",
            description = "Toggle global breakpoint interception mode on Mucker (pauses all matching requests).",
            inputProperties = mapOf(
                "breakpointMode" to mapOf("type" to "boolean", "description" to "true to enable breakpoint pauses, false to resume")
            ),
            required = listOf("breakpointMode")
        ))

        toolsArray.put(createToolDefinition(
            name = "list_history",
            description = "Query recorded network request history from Mucker memory buffer, with optional keyword or method filtering.",
            inputProperties = mapOf(
                "query" to mapOf("type" to "string", "description" to "Optional keyword filter matching URL or response body"),
                "method" to mapOf("type" to "string", "description" to "Optional HTTP method filter (GET, POST, etc.)"),
                "limit" to mapOf("type" to "integer", "description" to "Maximum number of records to return (default: 20)")
            )
        ))

        toolsArray.put(createToolDefinition(
            name = "fulfill_request",
            description = "Fulfill an in-flight paused OkHttp request with a mocked response.",
            inputProperties = mapOf(
                "requestId" to mapOf("type" to "string", "description" to "ID of the paused request"),
                "statusCode" to mapOf("type" to "integer", "description" to "HTTP status code (default: 200)"),
                "responseBody" to mapOf("type" to "string", "description" to "Mocked JSON/text response body")
            ),
            required = listOf("requestId")
        ))

        toolsArray.put(createToolDefinition(
            name = "continue_request",
            description = "Resume a paused OkHttp request without modification, allowing it to proceed to real network.",
            inputProperties = mapOf(
                "requestId" to mapOf("type" to "string", "description" to "ID of the paused request")
            ),
            required = listOf("requestId")
        ))

        toolsArray.put(createToolDefinition(
            name = "get_mock_rule_template",
            description = "Generate mock rule templates and ready-to-run curl/CLI commands for a URL pattern.",
            inputProperties = mapOf(
                "urlPattern" to mapOf("type" to "string", "description" to "Target URL pattern"),
                "statusCode" to mapOf("type" to "integer", "description" to "Status code to mock (default: 200)"),
                "responseBody" to mapOf("type" to "string", "description" to "JSON response body")
            ),
            required = listOf("urlPattern")
        ))

        val result = JSONObject()
        result.put("tools", toolsArray)
        return createResult(id, result)
    }

    private fun handleToolsCall(id: Any?, params: JSONObject): String {
        val name = params.optString("name", "")
        val args = params.optJSONObject("arguments") ?: JSONObject()

        return try {
            val contentText = when (name) {
                "get_status" -> {
                    val status = JSONObject()
                    status.put("status", "running")
                    status.put("port", port)
                    status.put("app", context?.packageName ?: "Android App")
                    status.put("breakpointMode", engine.breakpointMode)
                    status.put("activeRulesCount", engine.rules.getAllRules().size)
                    status.put("pausedRequestsCount", engine.getPendingPausedCount())
                    status.put("totalHistoryCount", engine.history.getAll().size)
                    status.toString(2)
                }

                "list_rules" -> {
                    engine.rules.toJson().toString(2)
                }

                "add_rule" -> {
                    val urlPattern = args.getString("urlPattern")
                    val method = args.optString("method", "ALL")
                    val statusCode = args.optInt("statusCode", 200)
                    val delayMs = args.optLong("delayMs", 0)
                    val responseBody = args.optString("responseBody", "{\"mocked\":true}")

                    val rule = MockRule(
                        id = UUID.randomUUID().toString().take(8),
                        urlPattern = urlPattern,
                        method = method,
                        statusCode = statusCode,
                        delayMs = delayMs,
                        responseHeaders = mapOf("Content-Type" to "application/json", "X-Mocked-By" to "Mucker-InApp-MCP"),
                        responseBody = responseBody,
                        isEnabled = true
                    )
                    engine.rules.addRule(rule)
                    "Successfully created mock rule:\n${rule.toJson().toString(2)}"
                }

                "remove_rule" -> {
                    val ruleId = args.getString("id")
                    val ok = engine.rules.removeRule(ruleId)
                    if (ok) "Successfully removed mock rule: $ruleId" else "Rule not found: $ruleId"
                }

                "clear_rules" -> {
                    val count = engine.rules.getAllRules().size
                    engine.rules.clearRules()
                    "Cleared all $count mock rules from Mucker engine memory."
                }

                "toggle_rule" -> {
                    val ruleId = args.getString("id")
                    val isEnabled = args.getBoolean("isEnabled")
                    val ok = engine.rules.toggleRule(ruleId, isEnabled)
                    if (ok) "Rule $ruleId is now ${if (isEnabled) "ENABLED" else "DISABLED"}" else "Rule not found: $ruleId"
                }

                "set_breakpoint_mode" -> {
                    val mode = args.getBoolean("breakpointMode")
                    engine.breakpointMode = mode
                    "Breakpoint mode is now ${if (mode) "ENABLED (all matching requests will pause)" else "DISABLED"}."
                }

                "list_history" -> {
                    val query = args.optString("query", "").lowercase()
                    val method = args.optString("method", "").uppercase()
                    val limit = args.optInt("limit", 20)

                    val records = engine.history.getAll().filter { rec ->
                        (method.isEmpty() || rec.method.equals(method, ignoreCase = true)) &&
                        (query.isEmpty() || rec.url.lowercase().contains(query) || (rec.responseBody?.lowercase()?.contains(query) == true))
                    }.take(limit)

                    val arr = JSONArray()
                    for (rec in records) {
                        arr.put(rec.toJson())
                    }
                    arr.toString(2)
                }

                "fulfill_request" -> {
                    val requestId = args.getString("requestId")
                    val statusCode = args.optInt("statusCode", 200)
                    val responseBody = args.optString("responseBody", "{\"status\":\"fulfilled_via_mcp\"}")
                    val headersMap = mapOf("Content-Type" to "application/json", "X-Mocked-By" to "Mucker-InApp-MCP")

                    val ok = engine.fulfill(requestId, MockDecision.Fulfill(statusCode, headersMap, responseBody))
                    if (ok) "Successfully fulfilled paused request $requestId with status $statusCode." else "Failed to fulfill: request $requestId not found or timed out."
                }

                "continue_request" -> {
                    val requestId = args.getString("requestId")
                    val ok = engine.continueRequest(requestId)
                    if (ok) "Successfully resumed request $requestId to real network." else "Failed: request $requestId not found or timed out."
                }

                "get_mock_rule_template" -> {
                    val urlPattern = args.getString("urlPattern")
                    val statusCode = args.optInt("statusCode", 200)
                    val body = args.optString("responseBody", "{\"status\":\"mocked\"}")
                    val curl = "curl -X POST http://localhost:$port/api/rules -H \"Content-Type: application/json\" -d '{\"urlPattern\":\"$urlPattern\",\"statusCode\":$statusCode,\"responseBody\":${JSONObject.quote(body)}}'"
                    "Mock Rule Template for $urlPattern (HTTP $statusCode):\n\n$curl"
                }

                else -> throw IllegalArgumentException("Unknown tool: $name")
            }

            createToolCallResult(id, contentText, isError = false)
        } catch (e: Exception) {
            createToolCallResult(id, "Error executing $name: ${e.message}", isError = true)
        }
    }

    private fun createToolDefinition(
        name: String,
        description: String,
        inputProperties: Map<String, Map<String, String>>,
        required: List<String> = emptyList()
    ): JSONObject {
        val tool = JSONObject()
        tool.put("name", name)
        tool.put("description", description)

        val schema = JSONObject()
        schema.put("type", "object")

        val props = JSONObject()
        inputProperties.forEach { (key, meta) ->
            val p = JSONObject()
            meta.forEach { (mk, mv) -> p.put(mk, mv) }
            props.put(key, p)
        }
        schema.put("properties", props)

        if (required.isNotEmpty()) {
            val reqArray = JSONArray()
            required.forEach { reqArray.put(it) }
            schema.put("required", reqArray)
        }

        tool.put("inputSchema", schema)
        return tool
    }

    private fun createToolCallResult(id: Any?, text: String, isError: Boolean): String {
        val result = JSONObject()
        val contentArray = JSONArray()
        val contentItem = JSONObject()
        contentItem.put("type", "text")
        contentItem.put("text", text)
        contentArray.put(contentItem)

        result.put("content", contentArray)
        result.put("isError", isError)
        return createResult(id, result)
    }

    private fun createResult(id: Any?, result: JSONObject): String {
        val root = JSONObject()
        root.put("jsonrpc", "2.0")
        if (id != null) root.put("id", id) else root.put("id", JSONObject.NULL)
        root.put("result", result)
        return root.toString()
    }

    private fun createError(id: Any?, code: Int, message: String): String {
        val root = JSONObject()
        root.put("jsonrpc", "2.0")
        if (id != null) root.put("id", id) else root.put("id", JSONObject.NULL)

        val err = JSONObject()
        err.put("code", code)
        err.put("message", message)
        root.put("error", err)
        return root.toString()
    }
}
