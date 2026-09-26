package io.github.mucker

import io.github.mucker.core.models.CdpMessages
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CdpMessagesTest {

    @Test
    fun testCreateFetchRequestPaused() {
        val jsonStr = CdpMessages.createFetchRequestPaused(
            requestId = "req_100",
            url = "https://api.example.com/data",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer abc"),
            postData = "{\"key\":\"value\"}"
        )

        val json = JSONObject(jsonStr)
        assertEquals("Fetch.requestPaused", json.getString("method"))
        
        val params = json.getJSONObject("params")
        assertEquals("req_100", params.getString("requestId"))
        assertEquals("XHR", params.getString("resourceType"))

        val req = params.getJSONObject("request")
        assertEquals("https://api.example.com/data", req.getString("url"))
        assertEquals("POST", req.getString("method"))
        assertEquals("{\"key\":\"value\"}", req.getString("postData"))
        assertEquals("Bearer abc", req.getJSONObject("headers").getString("Authorization"))
    }

    @Test
    fun testCreateNetworkResponseReceived() {
        val jsonStr = CdpMessages.createNetworkResponseReceived(
            requestId = "req_101",
            url = "https://api.example.com/test",
            status = 200,
            headers = mapOf("Content-Type" to "application/json", "X-Mocked-By" to "Mucker"),
            body = "{\"ok\":true}",
            responseTime = 120
        )

        val json = JSONObject(jsonStr)
        assertEquals("Network.responseReceived", json.getString("method"))

        val params = json.getJSONObject("params")
        assertEquals("req_101", params.getString("requestId"))
        assertEquals(120L, params.getLong("responseTime"))

        val res = params.getJSONObject("response")
        assertEquals(200, res.getInt("status"))
        assertEquals("{\"ok\":true}", res.getString("body"))
        assertEquals("Mucker", res.getJSONObject("headers").getString("X-Mocked-By"))
    }

    @Test
    fun testCreateRpcSuccessAndError() {
        val successStr = CdpMessages.createRpcSuccess(42L)
        val successJson = JSONObject(successStr)
        assertEquals(42L, successJson.getLong("id"))
        assertNotNull(successJson.getJSONObject("result"))

        val errorStr = CdpMessages.createRpcError(43L, -32601, "Method not found")
        val errorJson = JSONObject(errorStr)
        assertEquals(43L, errorJson.getLong("id"))
        assertEquals(-32601, errorJson.getJSONObject("error").getInt("code"))
        assertEquals("Method not found", errorJson.getJSONObject("error").getString("message"))
    }
}
