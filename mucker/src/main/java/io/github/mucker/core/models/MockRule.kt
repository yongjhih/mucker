package io.github.mucker.core.models

import org.json.JSONObject
import java.util.UUID

/**
 * Represents a rule used to intercept and mock OkHttp network requests.
 */
data class MockRule(
    val id: String = UUID.randomUUID().toString().take(8),
    val urlPattern: String,
    val method: String = "ALL", // ALL, GET, POST, PUT, DELETE, PATCH
    val statusCode: Int = 200,
    val delayMs: Long = 0,
    val responseHeaders: Map<String, String> = mapOf("Content-Type" to "application/json", "X-Mocked-By" to "Mucker"),
    val responseBody: String = "{\"status\":\"mocked\"}",
    var isEnabled: Boolean = true
) {
    /**
     * Checks if this rule matches the given HTTP method and URL.
     */
    fun matches(requestMethod: String, url: String): Boolean {
        if (!isEnabled) return false
        
        // Method matching
        if (method != "ALL" && !method.equals(requestMethod, ignoreCase = true)) {
            return false
        }

        // Pattern matching: check exact, glob or regex
        return try {
            if (urlPattern.contains("*") && !urlPattern.startsWith("^") && !urlPattern.startsWith(".*")) {
                // Convert simple glob pattern to regex: e.g. /api/* -> .*/api/.*
                val regexPattern = urlPattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
                url.matches(Regex(regexPattern))
            } else {
                url.matches(Regex(urlPattern)) || url.contains(urlPattern)
            }
        } catch (_: Exception) {
            url.contains(urlPattern)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("urlPattern", urlPattern)
        json.put("method", method)
        json.put("statusCode", statusCode)
        json.put("delayMs", delayMs)
        json.put("responseBody", responseBody)
        json.put("isEnabled", isEnabled)
        
        val headersObj = JSONObject()
        responseHeaders.forEach { (k, v) -> headersObj.put(k, v) }
        json.put("responseHeaders", headersObj)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): MockRule {
            val headersMap = mutableMapOf<String, String>()
            if (json.has("responseHeaders")) {
                val h = json.getJSONObject("responseHeaders")
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headersMap[k] = h.getString(k)
                }
            } else {
                headersMap["Content-Type"] = "application/json"
                headersMap["X-Mocked-By"] = "Mucker"
            }

            return MockRule(
                id = json.optString("id", UUID.randomUUID().toString().take(8)),
                urlPattern = json.getString("urlPattern"),
                method = json.optString("method", "ALL"),
                statusCode = json.optInt("statusCode", 200),
                delayMs = json.optLong("delayMs", 0),
                responseHeaders = headersMap,
                responseBody = json.optString("responseBody", ""),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}
