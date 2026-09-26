package io.github.mucker.core.models

import org.json.JSONObject

/**
 * Represents an inspected network request and response entry.
 */
data class NetworkRecord(
    val id: String,
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val postData: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var statusCode: Int? = null,
    var responseHeaders: Map<String, String> = emptyMap(),
    var responseBody: String? = null,
    var responseTime: Long? = null,
    var isPaused: Boolean = false,
    var isMocked: Boolean = false,
    var error: String? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("url", url)
        json.put("method", method)
        json.put("timestamp", timestamp)
        json.put("isPaused", isPaused)
        json.put("isMocked", isMocked)
        if (statusCode != null) json.put("statusCode", statusCode)
        if (responseTime != null) json.put("responseTime", responseTime)
        if (postData != null) json.put("postData", postData)
        if (responseBody != null) json.put("responseBody", responseBody)
        if (error != null) json.put("error", error)

        val reqH = JSONObject()
        headers.forEach { (k, v) -> reqH.put(k, v) }
        json.put("headers", reqH)

        val resH = JSONObject()
        responseHeaders.forEach { (k, v) -> resH.put(k, v) }
        json.put("responseHeaders", resH)

        return json
    }
}
