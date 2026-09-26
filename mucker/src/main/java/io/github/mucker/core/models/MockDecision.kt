package io.github.mucker.core.models

/**
 * Result of a decision made for a paused request (either via CDP Fetch.fulfillRequest/continueRequest or Dashboard UI).
 */
sealed interface MockDecision {
    data class Fulfill(
        val statusCode: Int = 200,
        val headers: Map<String, String> = mapOf("Content-Type" to "application/json", "X-Mocked-By" to "Mucker-Breakpoint"),
        val body: String = ""
    ) : MockDecision

    data object Continue : MockDecision

    data class Fail(
        val errorReason: String = "Failed by client"
    ) : MockDecision
}
