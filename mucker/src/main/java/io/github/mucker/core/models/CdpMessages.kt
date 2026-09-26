package io.github.mucker.core.models

import org.json.JSONObject

/**
 * Serializer for standard Chrome DevTools Protocol (CDP) JSON-RPC 2.0 messages.
 */
object CdpMessages {

    /**
     * CDP Fetch.requestPaused event. Emitted when an OkHttp request hits a breakpoint.
     */
    fun createFetchRequestPaused(
        requestId: String,
        url: String,
        method: String,
        headers: Map<String, String>,
        postData: String? = null
    ): String {
        val root = JSONObject()
        root.put("method", "Fetch.requestPaused")

        val params = JSONObject()
        params.put("requestId", requestId)
        params.put("resourceType", "XHR")

        val req = JSONObject()
        req.put("url", url)
        req.put("method", method)
        if (postData != null) req.put("postData", postData)

        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        req.put("headers", h)

        params.put("request", req)
        root.put("params", params)
        return root.toString()
    }

    /**
     * CDP Network.requestWillBeSent event.
     */
    fun createNetworkRequestWillBeSent(
        requestId: String,
        url: String,
        method: String,
        headers: Map<String, String>
    ): String {
        val root = JSONObject()
        root.put("method", "Network.requestWillBeSent")

        val params = JSONObject()
        params.put("requestId", requestId)
        params.put("timestamp", System.currentTimeMillis() / 1000.0)

        val req = JSONObject()
        req.put("url", url)
        req.put("method", method)
        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        req.put("headers", h)

        params.put("request", req)
        root.put("params", params)
        return root.toString()
    }

    /**
     * CDP Network.responseReceived event.
     */
    fun createNetworkResponseReceived(
        requestId: String,
        url: String,
        status: Int,
        headers: Map<String, String>,
        body: String? = null,
        responseTime: Long = 0
    ): String {
        val root = JSONObject()
        root.put("method", "Network.responseReceived")

        val params = JSONObject()
        params.put("requestId", requestId)
        params.put("responseTime", responseTime)

        val res = JSONObject()
        res.put("url", url)
        res.put("status", status)
        if (body != null) res.put("body", body)
        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        res.put("headers", h)

        params.put("response", res)
        root.put("params", params)
        return root.toString()
    }

    /**
     * Standard JSON-RPC success response.
     */
    fun createRpcSuccess(id: Long, result: JSONObject = JSONObject()): String {
        val root = JSONObject()
        root.put("id", id)
        root.put("result", result)
        return root.toString()
    }

    /**
     * Standard JSON-RPC error response.
     */
    fun createRpcError(id: Long, code: Int, message: String): String {
        val root = JSONObject()
        root.put("id", id)
        val err = JSONObject()
        err.put("code", code)
        err.put("message", message)
        root.put("error", err)
        return root.toString()
    }
}
