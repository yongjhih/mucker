package io.github.mucker

/**
 * No-op version of MuckerConfig for release builds.
 */
data class MuckerConfig(
    val port: Int = 8080,
    val showNotification: Boolean = false,
    val autoStart: Boolean = false,
    val maxHistorySize: Int = 0,
    val defaultTimeoutSeconds: Long = 0L,
    val breakpointMode: Boolean = false
)
