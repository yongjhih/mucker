package io.github.mucker

import android.annotation.SuppressLint
import android.content.Context
import io.github.mucker.core.MockEngine
import io.github.mucker.core.MockRuleRegistry
import io.github.mucker.core.RequestHistory
import io.github.mucker.server.MuckerHttpServer
import io.github.mucker.util.NetworkUtils

/**
 * Main entry point for Mucker CDP-compatible In-App Mock Engine.
 */
@SuppressLint("StaticFieldLeak")
object Mucker {

    @Volatile private var appContext: Context? = null
    @Volatile private var server: MuckerHttpServer? = null

    val engine = MockEngine(
        rules = MockRuleRegistry(),
        history = RequestHistory(maxSize = 250)
    )

    val rules: MockRuleRegistry
        get() = engine.rules

    val history: RequestHistory
        get() = engine.history

    var port: Int = 8080
        private set

    val interceptor: MuckerInterceptor by lazy {
        MuckerInterceptor(engine)
    }

    val serverUrl: String
        get() {
            val ip = NetworkUtils.getLocalIpAddress(appContext)
            return "http://$ip:$port"
        }

    @Synchronized
    fun install(context: Context, config: MuckerConfig = MuckerConfig()) {
        if (appContext != null) return
        appContext = context.applicationContext
        port = config.port

        engine.breakpointMode = config.breakpointMode

        if (config.autoStart) {
            start()
        }

        if (config.showNotification) {
            triggerNotificationShow(context, serverUrl)
        }
    }

    @Synchronized
    fun start() {
        val ctx = appContext
        if (server == null) {
            server = MuckerHttpServer(port = port, engine = engine, context = ctx)
        }
        server?.start()
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        appContext?.let { triggerNotificationHide(it) }
    }

    private fun triggerNotificationShow(context: Context, url: String) {
        try {
            val clazz = Class.forName("io.github.mucker.ui.MuckerNotification")
            val method = clazz.getMethod("show", Context::class.java, String::class.java)
            method.invoke(null, context, url)
        } catch (_: Throwable) {
            // Headless mode: no notification UI available on classpath
        }
    }

    private fun triggerNotificationHide(context: Context) {
        try {
            val clazz = Class.forName("io.github.mucker.ui.MuckerNotification")
            val method = clazz.getMethod("hide", Context::class.java)
            method.invoke(null, context)
        } catch (_: Throwable) {
            // Headless mode
        }
    }
}
