package io.github.mucker

import io.github.mucker.core.models.MockRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRuleTest {

    @Test
    fun testExactUrlMatch() {
        val rule = MockRule(
            urlPattern = "https://api.example.com/v1/user",
            method = "GET"
        )
        assertTrue(rule.matches("GET", "https://api.example.com/v1/user"))
        assertFalse(rule.matches("POST", "https://api.example.com/v1/user"))
        assertFalse(rule.matches("GET", "https://api.example.com/v1/other"))
    }

    @Test
    fun testGlobPatternMatch() {
        val rule = MockRule(
            urlPattern = "*/api/v1/user/*",
            method = "ALL"
        )
        assertTrue(rule.matches("GET", "https://api.example.com/api/v1/user/profile"))
        assertTrue(rule.matches("POST", "https://api.example.com/api/v1/user/settings"))
        assertFalse(rule.matches("GET", "https://api.example.com/api/v2/products"))
    }

    @Test
    fun testDisabledRuleDoesNotMatch() {
        val rule = MockRule(
            urlPattern = "*/test*",
            isEnabled = false
        )
        assertFalse(rule.matches("GET", "https://api.example.com/test"))
    }
}
