package io.github.mucker

import io.github.mucker.core.MockEngine
import io.github.mucker.core.models.CdpMessages
import io.github.mucker.core.models.MockDecision
import io.github.mucker.core.models.NetworkRecord
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * OkHttp Interceptor that logs network requests and dynamically injects mock responses or breakpoints.
 */
class MuckerInterceptor(
    private val engine: MockEngine = Mucker.engine,
    private val timeoutSeconds: Long = 25L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val method = request.method
        val requestId = "req_" + UUID.randomUUID().toString().take(8)

        // Read request headers
        val requestHeaders = mutableMapOf<String, String>()
        for (i in 0 until request.headers.size) {
            requestHeaders[request.headers.name(i)] = request.headers.value(i)
        }

        // Read request payload if present
        var postData: String? = null
        try {
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                postData = buffer.readString(StandardCharsets.UTF_8)
            }
        } catch (_: Exception) {}

        val record = NetworkRecord(
            id = requestId,
            url = url,
            method = method,
            headers = requestHeaders,
            postData = postData
        )
        engine.history.record(record)

        // Broadcast CDP Network.requestWillBeSent
        engine.broadcast(CdpMessages.createNetworkRequestWillBeSent(requestId, url, method, requestHeaders))

        // 1. Check static mock rules
        val matchedRule = engine.rules.findMatchingRule(method, url)
        if (matchedRule != null && matchedRule.isEnabled) {
            if (matchedRule.delayMs > 0) {
                try {
                    Thread.sleep(matchedRule.delayMs)
                } catch (_: InterruptedException) {}
            }

            val mockResponse = buildMockResponse(
                chain = chain,
                statusCode = matchedRule.statusCode,
                headers = matchedRule.responseHeaders,
                body = matchedRule.responseBody,
                mockTag = "Mucker-Rule"
            )

            record.statusCode = matchedRule.statusCode
            record.responseHeaders = matchedRule.responseHeaders
            record.responseBody = matchedRule.responseBody
            record.responseTime = matchedRule.delayMs
            record.isMocked = true

            engine.broadcast(
                CdpMessages.createNetworkResponseReceived(
                    requestId = requestId,
                    url = url,
                    status = matchedRule.statusCode,
                    headers = matchedRule.responseHeaders,
                    body = matchedRule.responseBody,
                    responseTime = matchedRule.delayMs
                )
            )

            return mockResponse
        }

        // 2. Check Breakpoint Mode
        if (engine.shouldIntercept(url)) {
            val future = engine.pauseRequest(record)
            val decision = try {
                future.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                // Safe timeout fallback so app never hangs
                MockDecision.Continue
            } catch (e: Exception) {
                MockDecision.Continue
            }

            when (decision) {
                is MockDecision.Fulfill -> {
                    val mockResponse = buildMockResponse(
                        chain = chain,
                        statusCode = decision.statusCode,
                        headers = decision.headers,
                        body = decision.body,
                        mockTag = "Mucker-Breakpoint"
                    )

                    record.isPaused = false
                    record.isMocked = true
                    record.statusCode = decision.statusCode
                    record.responseHeaders = decision.headers
                    record.responseBody = decision.body
                    record.responseTime = 0

                    engine.broadcast(
                        CdpMessages.createNetworkResponseReceived(
                            requestId = requestId,
                            url = url,
                            status = decision.statusCode,
                            headers = decision.headers,
                            body = decision.body
                        )
                    )

                    return mockResponse
                }
                is MockDecision.Fail -> {
                    record.isPaused = false
                    record.error = decision.errorReason
                    throw IOException("Blocked by Mucker Breakpoint: ${decision.errorReason}")
                }
                is MockDecision.Continue -> {
                    record.isPaused = false
                    // Fallthrough to chain.proceed below
                }
            }
        }

        // 3. Normal real network execution
        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            record.error = e.message ?: "Network error"
            throw e
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

        // Read response headers
        val responseHeaders = mutableMapOf<String, String>()
        for (i in 0 until response.headers.size) {
            responseHeaders[response.headers.name(i)] = response.headers.value(i)
        }

        // Peek response body safely for inspection (up to 1MB)
        var responseBodyString: String? = null
        try {
            response.peekBody(1024 * 1024).let { peeked ->
                responseBodyString = peeked.string()
            }
        } catch (_: Exception) {}

        record.statusCode = response.code
        record.responseHeaders = responseHeaders
        record.responseBody = responseBodyString
        record.responseTime = tookMs
        record.isMocked = false

        engine.broadcast(
            CdpMessages.createNetworkResponseReceived(
                requestId = requestId,
                url = url,
                status = response.code,
                headers = responseHeaders,
                body = responseBodyString,
                responseTime = tookMs
            )
        )

        return response
    }

    private fun buildMockResponse(
        chain: Interceptor.Chain,
        statusCode: Int,
        headers: Map<String, String>,
        body: String,
        mockTag: String
    ): Response {
        val mediaType = (headers["Content-Type"] ?: "application/json; charset=utf-8").toMediaTypeOrNull()
        val responseBody = body.toResponseBody(mediaType)

        val builder = Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("Mocked by $mockTag")
            .body(responseBody)
            .addHeader("X-Mocked-By", mockTag)

        headers.forEach { (name, value) ->
            if (!name.equals("X-Mocked-By", ignoreCase = true)) {
                builder.addHeader(name, value)
            }
        }

        return builder.build()
    }
}
