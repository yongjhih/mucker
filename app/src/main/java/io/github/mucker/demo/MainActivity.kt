package io.github.mucker.demo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.mucker.Mucker
import io.github.mucker.demo.api.DemoApiClient
import io.github.mucker.demo.databinding.ActivityMainBinding
import io.github.mucker.ui.MuckerActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show server URL
        binding.tvServerUrl.text = Mucker.serverUrl

        // Open In-App Dashboard
        binding.btnOpenDashboard.setOnClickListener {
            startActivity(Intent(this, MuckerActivity::class.java))
        }

        // Toggle Breakpoint mode
        binding.btnToggleBp.setOnClickListener {
            Mucker.engine.breakpointMode = !Mucker.engine.breakpointMode
            updateBreakpointButton()
        }

        // Test Endpoint 1: User Profile (matches pre-seeded mock rule)
        binding.btnTestUser.setOnClickListener {
            executeRequest {
                DemoApiClient.get("https://dummyjson.com/api/v1/user/profile")
            }
        }

        // Test Endpoint 2: Products (real network fallback)
        binding.btnTestProducts.setOnClickListener {
            executeRequest {
                DemoApiClient.get("https://dummyjson.com/products?limit=2")
            }
        }

        // Test Endpoint 3: Checkout (simulated post)
        binding.btnTestCheckout.setOnClickListener {
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
            binding.btnToggleBp.text = "Breakpoint: ON"
            binding.btnToggleBp.setBackgroundColor(Color.parseColor("#f59e0b"))
        } else {
            binding.btnToggleBp.text = "Breakpoint: OFF"
            binding.btnToggleBp.setBackgroundColor(Color.parseColor("#334155"))
        }
    }

    private fun executeRequest(block: suspend () -> io.github.mucker.demo.api.ApiResult) {
        binding.tvStatusCode.text = "Fetching..."
        binding.tvStatusCode.setTextColor(Color.parseColor("#94a3b8"))
        binding.tvMockBadge.visibility = View.GONE
        binding.tvDuration.text = "..."
        binding.tvResponseBody.text = "Request in flight..."

        lifecycleScope.launch {
            val result = block()

            binding.tvStatusCode.text = "${result.statusCode}"
            if (result.statusCode in 200..299) {
                binding.tvStatusCode.setTextColor(Color.parseColor("#10b981"))
            } else if (result.statusCode >= 500) {
                binding.tvStatusCode.setTextColor(Color.parseColor("#ef4444"))
            } else {
                binding.tvStatusCode.setTextColor(Color.parseColor("#f59e0b"))
            }

            if (result.isMocked) {
                binding.tvMockBadge.visibility = View.VISIBLE
                binding.tvMockBadge.text = "MOCKED (${result.mockSource ?: "Mucker"})"
            } else {
                binding.tvMockBadge.visibility = View.GONE
            }

            binding.tvDuration.text = "${result.durationMs} ms"
            binding.tvResponseBody.text = result.body
        }
    }
}
