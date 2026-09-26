package io.github.mucker.demo.api

import io.github.mucker.Mucker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ApiResult(
    val statusCode: Int,
    val isMocked: Boolean,
    val mockSource: String?,
    val durationMs: Long,
    val body: String
)

object DemoApiClient {

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Attach MuckerInterceptor right into the pipeline
            .addInterceptor(Mucker.interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(url: String): ApiResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        execute(request)
    }

    suspend fun post(url: String, jsonBody: String): ApiResult = withContext(Dispatchers.IO) {
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        execute(request)
    }

    private fun execute(request: Request): ApiResult {
        val start = System.currentTimeMillis()
        return try {
            val response = okHttpClient.newCall(request).execute()
            val duration = System.currentTimeMillis() - start
            val mockHeader = response.header("X-Mocked-By")
            val bodyString = response.body?.string() ?: ""

            ApiResult(
                statusCode = response.code,
                isMocked = mockHeader != null,
                mockSource = mockHeader,
                durationMs = duration,
                body = bodyString
            )
        } catch (e: Exception) {
            ApiResult(
                statusCode = 0,
                isMocked = false,
                mockSource = null,
                durationMs = System.currentTimeMillis() - start,
                body = "Network Error: ${e.message}"
            )
        }
    }
}
