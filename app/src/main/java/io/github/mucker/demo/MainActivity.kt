package io.github.mucker.demo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.mucker.Mucker
import io.github.mucker.demo.api.DemoApiClient
import io.github.mucker.ui.MuckerActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvServerUrl: TextView
    private lateinit var btnOpenDashboard: Button
    private lateinit var btnToggleBp: Button
    private lateinit var btnTestUser: Button
    private lateinit var btnTestProducts: Button
    private lateinit var btnTestCheckout: Button
    private lateinit var tvStatusCode: TextView
    private lateinit var tvMockBadge: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvResponseBody: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvServerUrl = findViewById(R.id.tvServerUrl)
        btnOpenDashboard = findViewById(R.id.btnOpenDashboard)
        btnToggleBp = findViewById(R.id.btnToggleBp)
        btnTestUser = findViewById(R.id.btnTestUser)
        btnTestProducts = findViewById(R.id.btnTestProducts)
        btnTestCheckout = findViewById(R.id.btnTestCheckout)
        tvStatusCode = findViewById(R.id.tvStatusCode)
        tvMockBadge = findViewById(R.id.tvMockBadge)
        tvDuration = findViewById(R.id.tvDuration)
        tvResponseBody = findViewById(R.id.tvResponseBody)

        // Show server URL
        tvServerUrl.text = Mucker.serverUrl

        // Open In-App Dashboard
        btnOpenDashboard.setOnClickListener {
            startActivity(Intent(this, MuckerActivity::class.java))
        }

        // Toggle Breakpoint mode
        btnToggleBp.setOnClickListener {
            Mucker.engine.breakpointMode = !Mucker.engine.breakpointMode
            updateBreakpointButton()
        }

        // Test Endpoint 1: User Profile (matches pre-seeded mock rule)
        btnTestUser.setOnClickListener {
            executeRequest {
                DemoApiClient.get("https://dummyjson.com/api/v1/user/profile")
            }
        }

        // Test Endpoint 2: Products (real network fallback)
        btnTestProducts.setOnClickListener {
            executeRequest {
                DemoApiClient.get("https://dummyjson.com/products?limit=2")
            }
        }

        // Test Endpoint 3: Checkout (simulated post)
        btnTestCheckout.setOnClickListener {
            executeRequest {
                DemoApiClient.post(
                    url = "https://dummyjson.com/api/v1/checkout",
                    jsonBody = "{\"cartId\":1024,\"amount\":99.9}"
                )
            }
        }

        updateBreakpointButton()
    }

    private fun updateBreakpointButton() {
        if (Mucker.engine.breakpointMode) {
            btnToggleBp.text = "Breakpoint: ON"
            btnToggleBp.setBackgroundColor(Color.parseColor("#f59e0b"))
        } else {
            btnToggleBp.text = "Breakpoint: OFF"
            btnToggleBp.setBackgroundColor(Color.parseColor("#334155"))
        }
    }

    private fun executeRequest(block: suspend () -> io.github.mucker.demo.api.ApiResult) {
        tvStatusCode.text = "Fetching..."
        tvStatusCode.setTextColor(Color.parseColor("#94a3b8"))
        tvMockBadge.visibility = View.GONE
        tvDuration.text = "..."
        tvResponseBody.text = "Request in flight..."

        lifecycleScope.launch {
            val result = block()

            tvStatusCode.text = "${result.statusCode}"
            if (result.statusCode in 200..299) {
                tvStatusCode.setTextColor(Color.parseColor("#10b981"))
            } else if (result.statusCode >= 500) {
                tvStatusCode.setTextColor(Color.parseColor("#ef4444"))
            } else {
                tvStatusCode.setTextColor(Color.parseColor("#f59e0b"))
            }

            if (result.isMocked) {
                tvMockBadge.visibility = View.VISIBLE
                tvMockBadge.text = "MOCKED (${result.mockSource ?: "Mucker"})"
            } else {
                tvMockBadge.visibility = View.GONE
            }

            tvDuration.text = "${result.durationMs} ms"
            tvResponseBody.text = result.body
        }
    }
}
