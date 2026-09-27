package io.github.mucker

import io.github.mucker.core.MockRuleRegistry
import io.github.mucker.core.models.MockRule
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockRuleRegistryTest {

    private lateinit var registry: MockRuleRegistry

    @Before
    fun setUp() {
        registry = MockRuleRegistry()
    }

    @Test
    fun testAddAndFindRule() {
        val rule = MockRule(
            id = "rule1",
            urlPattern = ".*/api/v1/user.*",
            method = "GET",
            statusCode = 200,
            responseBody = "{\"user\":\"Alice\"}"
        )
        registry.addRule(rule)

        assertEquals(1, registry.getAllRules().size)

        val matched = registry.findMatchingRule("GET", "https://example.com/api/v1/user/profile")
        assertNotNull(matched)
        assertEquals("rule1", matched?.id)
        assertEquals(200, matched?.statusCode)

        val nonMatched = registry.findMatchingRule("POST", "https://example.com/api/v1/user/profile")
        assertNull(nonMatched)
    }

    @Test
    fun testToggleRule() {
        val rule = MockRule(
            id = "rule2",
            urlPattern = ".*/api/v1/data.*",
            isEnabled = true
        )
        registry.addRule(rule)

        assertTrue(registry.findMatchingRule("GET", "https://example.com/api/v1/data")?.isEnabled == true)

        registry.toggleRule("rule2", false)
        assertNull(registry.findMatchingRule("GET", "https://example.com/api/v1/data"))

        registry.toggleRule("rule2", true)
        assertNotNull(registry.findMatchingRule("GET", "https://example.com/api/v1/data"))
    }

    @Test
    fun testRemoveAndClearRules() {
        registry.addRule(MockRule(id = "r1", urlPattern = "/a"))
        registry.addRule(MockRule(id = "r2", urlPattern = "/b"))

        assertEquals(2, registry.getAllRules().size)

        registry.removeRule("r1")
        assertEquals(1, registry.getAllRules().size)
        assertNull(registry.findMatchingRule("GET", "/a"))

        registry.clearRules()
        assertEquals(0, registry.getAllRules().size)
    }

    @Test
    fun testJsonSerialization() {
        val rule = MockRule(
            id = "json_rule",
            urlPattern = "/test",
            method = "POST",
            statusCode = 201,
            delayMs = 150,
            responseBody = "{\"created\":true}"
        )
        registry.addRule(rule)

        val jsonArray = registry.toJson()
        assertEquals(1, jsonArray.length())

        val newRegistry = MockRuleRegistry()
        newRegistry.loadFromJson(jsonArray)

        assertEquals(1, newRegistry.getAllRules().size)
        val loaded = newRegistry.findMatchingRule("POST", "/test")
        assertNotNull(loaded)
        assertEquals(201, loaded?.statusCode)
        assertEquals(150L, loaded?.delayMs)
    }
}
