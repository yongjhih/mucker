package io.github.mucker.mcp

import android.content.Context
import io.github.mucker.Mucker
import io.github.mucker.core.MockEngine
import io.github.mucker.server.MuckerHttpServer

/**
 * Entry point to install Model Context Protocol (MCP) support into Mucker.
 */
object MuckerMcp {

    @JvmStatic
    fun install(server: MuckerHttpServer, engine: MockEngine, context: Context?): MuckerMcpPlugin {
        val plugin = MuckerMcpPlugin(engine, server.port, context)
        server.registerPlugin(plugin)
        return plugin
    }
}

/**
 * Kotlin extension function to enable MCP on a MuckerHttpServer instance.
 */
fun MuckerHttpServer.enableMcp(engine: MockEngine, context: Context?): MuckerMcpPlugin {
    return MuckerMcp.install(this, engine, context)
}

/**
 * Kotlin extension function to enable MCP on the Mucker singleton.
 */
fun Mucker.enableMcp(context: Context? = null): MuckerMcpPlugin? {
    val server = currentServer ?: return null
    return MuckerMcp.install(server, engine, context)
}
