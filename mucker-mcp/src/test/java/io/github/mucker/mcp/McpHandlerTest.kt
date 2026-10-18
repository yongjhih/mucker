package io.github.mucker.mcp

import io.github.mucker.core.MockEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpHandlerTest {

    private lateinit var engine: MockEngine
    private lateinit var handler: McpHandler

    @Before
    fun setUp() {
        engine = MockEngine()
        handler = McpHandler(engine = engine, port = 8080, context = null)
    }

    @Test
    fun testInitialize() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "initialize")
            put("params", JSONObject().apply {
                put("protocolVersion", "2024-11-05")
            })
        }

        val rawResponse = handler.handleMessage(request.toString())
        val response = JSONObject(rawResponse)

        assertEquals("2.0", response.getString("jsonrpc"))
        assertEquals(1, response.getInt("id"))
        assertTrue(response.has("result"))

        val result = response.getJSONObject("result")
        assertEquals("2024-11-05", result.getString("protocolVersion"))
        assertEquals("mucker-android-interceptor", result.getJSONObject("serverInfo").getString("name"))
        assertTrue(result.getJSONObject("capabilities").has("tools"))
    }

    @Test
    fun testPing() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", "ping-123")
            put("method", "ping")
        }

        val rawResponse = handler.handleMessage(request.toString())
        val response = JSONObject(rawResponse)

        assertEquals("ping-123", response.getString("id"))
        assertTrue(response.has("result"))
    }

    @Test
    fun testToolsList() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/list")
        }

        val rawResponse = handler.handleMessage(request.toString())
        val response = JSONObject(rawResponse)
        val tools = response.getJSONObject("result").getJSONArray("tools")

        assertTrue("Expected at least 11 tools", tools.length() >= 11)

        val toolNames = mutableListOf<String>()
        for (i in 0 until tools.length()) {
            toolNames.add(tools.getJSONObject(i).getString("name"))
        }

        assertTrue(toolNames.contains("get_status"))
        assertTrue(toolNames.contains("list_rules"))
        assertTrue(toolNames.contains("add_rule"))
        assertTrue(toolNames.contains("remove_rule"))
        assertTrue(toolNames.contains("clear_rules"))
        assertTrue(toolNames.contains("toggle_rule"))
        assertTrue(toolNames.contains("set_breakpoint_mode"))
        assertTrue(toolNames.contains("list_history"))
        assertTrue(toolNames.contains("fulfill_request"))
        assertTrue(toolNames.contains("continue_request"))
        assertTrue(toolNames.contains("get_mock_rule_template"))
    }

    @Test
    fun testToolsCallGetStatus() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 3)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "get_status")
                put("arguments", JSONObject())
            })
        }

        val rawResponse = handler.handleMessage(request.toString())
        val response = JSONObject(rawResponse)
        val content = response.getJSONObject("result").getJSONArray("content")
        val text = content.getJSONObject(0).getString("text")

        val statusJson = JSONObject(text)
        assertEquals("running", statusJson.getString("status"))
        assertEquals(8080, statusJson.getInt("port"))
        assertFalse(statusJson.getBoolean("breakpointMode"))
    }

    @Test
    fun testToolsCallAddAndListAndRemoveRule() {
        // 1. Add Rule
        val addRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 4)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "add_rule")
                put("arguments", JSONObject().apply {
                    put("urlPattern", ".*/api/v1/mocked.*")
                    put("statusCode", 201)
                    put("responseBody", "{\"created\":true}")
                })
            })
        }

        val addResponse = JSONObject(handler.handleMessage(addRequest.toString()))
        assertFalse(addResponse.getJSONObject("result").getBoolean("isError"))
        assertEquals(1, engine.rules.getAllRules().size)
        val rule = engine.rules.getAllRules()[0]
        assertEquals(".*/api/v1/mocked.*", rule.urlPattern)
        assertEquals(201, rule.statusCode)

        // 2. Toggle Rule
        val toggleRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 5)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "toggle_rule")
                put("arguments", JSONObject().apply {
                    put("id", rule.id)
                    put("isEnabled", false)
                })
            })
        }
        val toggleResponse = JSONObject(handler.handleMessage(toggleRequest.toString()))
        assertFalse(toggleResponse.getJSONObject("result").getBoolean("isError"))
        assertFalse(engine.rules.getAllRules()[0].isEnabled)

        // 3. Remove Rule
        val removeRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 6)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "remove_rule")
                put("arguments", JSONObject().apply {
                    put("id", rule.id)
                })
            })
        }
        val removeResponse = JSONObject(handler.handleMessage(removeRequest.toString()))
        assertFalse(removeResponse.getJSONObject("result").getBoolean("isError"))
        assertEquals(0, engine.rules.getAllRules().size)
    }

    @Test
    fun testToolsCallSetBreakpointMode() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 7)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "set_breakpoint_mode")
                put("arguments", JSONObject().apply {
                    put("breakpointMode", true)
                })
            })
        }

        handler.handleMessage(request.toString())
        assertTrue(engine.breakpointMode)

        val disableRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 8)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "set_breakpoint_mode")
                put("arguments", JSONObject().apply {
                    put("breakpointMode", false)
                })
            })
        }
        handler.handleMessage(disableRequest.toString())
        assertFalse(engine.breakpointMode)
    }

    @Test
    fun testUnknownMethodError() {
        val request = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 99)
            put("method", "unknown/action")
        }

        val rawResponse = handler.handleMessage(request.toString())
        val response = JSONObject(rawResponse)
        assertTrue(response.has("error"))
        assertEquals(-32601, response.getJSONObject("error").getInt("code"))
    }

    @Test
    fun testBatchRequests() {
        val batch = JSONArray().apply {
            put(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 101)
                put("method", "ping")
            })
            put(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 102)
                put("method", "ping")
            })
        }

        val rawResponse = handler.handleMessage(batch.toString())
        val responseArray = JSONArray(rawResponse)
        assertEquals(2, responseArray.length())
        assertEquals(101, responseArray.getJSONObject(0).getInt("id"))
        assertEquals(102, responseArray.getJSONObject(1).getInt("id"))
    }
}
