package io.github.mucker.core

import io.github.mucker.core.models.MockRule
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe registry for configured mock rules.
 */
class MockRuleRegistry {

    private val rules = CopyOnWriteArrayList<MockRule>()

    fun addRule(rule: MockRule): MockRule {
        // If rule with same ID exists, replace it
        val existingIndex = rules.indexOfFirst { it.id == rule.id }
        if (existingIndex >= 0) {
            rules[existingIndex] = rule
        } else {
            rules.add(rule)
        }
        return rule
    }

    fun removeRule(ruleId: String): Boolean {
        return rules.removeIf { it.id == ruleId }
    }

    fun clearRules() {
        rules.clear()
    }

    fun toggleRule(ruleId: String, isEnabled: Boolean): Boolean {
        val rule = rules.firstOrNull { it.id == ruleId } ?: return false
        rule.isEnabled = isEnabled
        return true
    }

    fun findMatchingRule(method: String, url: String): MockRule? {
        return rules.firstOrNull { it.matches(method, url) }
    }

    fun getAllRules(): List<MockRule> {
        return rules.toList()
    }

    fun toJson(): JSONArray {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        return array
    }

    fun loadFromJson(jsonArray: JSONArray) {
        rules.clear()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            rules.add(MockRule.fromJson(obj))
        }
    }
}
