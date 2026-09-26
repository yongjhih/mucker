package io.github.mucker

/**
 * Configuration options for Mucker Mock Engine.
 */
data class MuckerConfig(
    val port: Int = 8080,
    val showNotification: Boolean = true,
    val autoStart: Boolean = true,
    val maxHistorySize: Int = 200,
    val defaultTimeoutSeconds: Long = 25L,
    val breakpointMode: Boolean = false
)
