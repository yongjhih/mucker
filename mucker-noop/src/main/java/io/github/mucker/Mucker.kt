package io.github.mucker

import android.content.Context

/**
 * No-op version of Mucker singleton for release builds.
 */
object Mucker {

    val interceptor: MuckerInterceptor by lazy {
        MuckerInterceptor()
    }

    val serverUrl: String = ""

    val port: Int = 0

    fun install(context: Context, config: MuckerConfig = MuckerConfig()) {
        // No-op
    }

    fun start() {
        // No-op
    }

    fun stop() {
        // No-op
    }
}
