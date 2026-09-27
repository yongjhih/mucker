package io.github.mucker.core

import io.github.mucker.core.models.NetworkRecord
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe ring buffer storing recent network inspections.
 */
class RequestHistory(private val maxSize: Int = 200) {

    private val records = CopyOnWriteArrayList<NetworkRecord>()

    fun record(record: NetworkRecord) {
        records.add(0, record)
        while (records.size > maxSize) {
            records.removeAt(records.lastIndex)
        }
    }

    fun find(id: String): NetworkRecord? {
        return records.firstOrNull { it.id == id }
    }

    fun getAll(): List<NetworkRecord> {
        return records.toList()
    }

    fun clear() {
        records.clear()
    }

    fun toJson(): JSONArray {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        return array
    }
}
