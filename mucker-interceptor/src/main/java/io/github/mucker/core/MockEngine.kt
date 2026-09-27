package io.github.mucker.core

import io.github.mucker.core.models.CdpMessages
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.NetworkRecord
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates mock rules, breakpoints, and CDP event dispatching.
 */
class MockEngine(
    val rules: MockRuleRegistry = MockRuleRegistry(),
    val history: RequestHistory = RequestHistory()
) {
    var breakpointMode: Boolean = false
    private val interceptPatterns = CopyOnWriteArrayList<String>()
    private val pendingFutures = ConcurrentHashMap<String, CompletableFuture<MockDecision>>()

    var eventBroadcaster: ((String) -> Unit)? = null

    fun enableInterception(patterns: List<String>) {
        breakpointMode = true
        interceptPatterns.clear()
        interceptPatterns.addAll(patterns)
    }

    fun disableInterception() {
        breakpointMode = false
        interceptPatterns.clear()
        // Resume any pending futures so thread is never leaked
        pendingFutures.forEach { (_, future) ->
            future.complete(MockDecision.Continue)
        }
        pendingFutures.clear()
    }

    fun shouldIntercept(url: String): Boolean {
        if (!breakpointMode) return false
        if (interceptPatterns.isEmpty()) return true // default intercept all if pattern is empty

        return interceptPatterns.any { pattern ->
            if (pattern == "*") true
            else try {
                val regexPattern = pattern.replace(".", "\\.").replace("*", ".*")
                url.matches(Regex(regexPattern)) || url.contains(pattern)
            } catch (_: Exception) {
                url.contains(pattern)
            }
        }
    }

    /**
     * Pauses the request thread and broadcasts Fetch.requestPaused to all connected CDP clients / web UI.
     */
    fun pauseRequest(record: NetworkRecord): CompletableFuture<MockDecision> {
        val future = CompletableFuture<MockDecision>()
        pendingFutures[record.id] = future
        record.isPaused = true

        // Broadcast CDP Fetch.requestPaused
        val eventJson = CdpMessages.createFetchRequestPaused(
            requestId = record.id,
            url = record.url,
            method = record.method,
            headers = record.headers,
            postData = record.postData
        )
        eventBroadcaster?.invoke(eventJson)

        return future
    }

    fun fulfill(requestId: String, decision: MockDecision.Fulfill): Boolean {
        val future = pendingFutures.remove(requestId) ?: return false
        val rec = history.find(requestId)
        if (rec != null) {
            rec.isPaused = false
            rec.isMocked = true
            rec.statusCode = decision.statusCode
            rec.responseHeaders = decision.headers
            rec.responseBody = decision.body
        }
        future.complete(decision)
        return true
    }

    fun continueRequest(requestId: String): Boolean {
        val future = pendingFutures.remove(requestId) ?: return false
        val rec = history.find(requestId)
        if (rec != null) {
            rec.isPaused = false
        }
        future.complete(MockDecision.Continue)
        return true
    }

    fun failRequest(requestId: String, reason: String = "Failed"): Boolean {
        val future = pendingFutures.remove(requestId) ?: return false
        val rec = history.find(requestId)
        if (rec != null) {
            rec.isPaused = false
            rec.error = reason
        }
        future.complete(MockDecision.Fail(reason))
        return true
    }

    fun getPendingPausedCount(): Int = pendingFutures.size

    fun broadcast(json: String) {
        eventBroadcaster?.invoke(json)
    }
}
