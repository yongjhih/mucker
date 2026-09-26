package io.github.mucker.demo

import android.app.Application
import io.github.mucker.Mucker
import io.github.mucker.MuckerConfig
import io.github.mucker.core.models.MockRule

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Install Mucker In-App Mock Engine
        Mucker.install(
            context = this,
            config = MuckerConfig(
                port = 8080,
                showNotification = true,
                autoStart = true,
                breakpointMode = false
            )
        )

        // Seed some sample mock rules for instant demo verification
        Mucker.rules.addRule(
            MockRule(
                id = "rule_user",
                urlPattern = ".*/api/v1/user/profile.*",
                method = "GET",
                statusCode = 200,
                delayMs = 250,
                responseBody = """
                    {
                      "id": "usr_9942",
                      "name": "Alex Mercer (Mocked)",
                      "email": "alex@mucker.dev",
                      "role": "Lead Architect",
                      "mocked": true
                    }
                """.trimIndent(),
                isEnabled = true
            )
        )

        Mucker.rules.addRule(
            MockRule(
                id = "rule_checkout_err",
                urlPattern = ".*/api/v1/checkout.*",
                method = "POST",
                statusCode = 500,
                delayMs = 400,
                responseBody = """
                    {
                      "error": "PaymentGatewayTimeout",
                      "code": 50001,
                      "message": "Simulated bank timeout error by Mucker"
                    }
                """.trimIndent(),
                isEnabled = false // disabled by default, can be toggled on
            )
        )
    }
}
