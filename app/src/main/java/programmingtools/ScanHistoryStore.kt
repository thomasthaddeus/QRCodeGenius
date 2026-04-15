package com.programmingtools.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScanHistoryEntry(
    val id: String,
    val rawValue: String,
    val type: String,
    val title: String,
    val summary: String,
    val timestamp: Long
)

class ScanHistoryStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun list(): List<ScanHistoryEntry> {
        val rawJson = preferences.getString(KEY_ENTRIES, "[]").orEmpty()
        val array = JSONArray(rawJson)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ScanHistoryEntry(
                        id = item.optString(KEY_ID),
                        rawValue = item.optString(KEY_RAW_VALUE),
                        type = item.optString(KEY_TYPE),
                        title = item.optString(KEY_TITLE),
                        summary = item.optString(KEY_SUMMARY),
                        timestamp = item.optLong(KEY_TIMESTAMP)
                    )
                )
            }
        }
    }

    fun add(type: String, title: String, summary: String, rawValue: String) {
        val current = list().toMutableList()
        current.removeAll { it.rawValue == rawValue }
        current.add(
            0,
            ScanHistoryEntry(
                id = UUID.randomUUID().toString(),
                rawValue = rawValue,
                type = type,
                title = title,
                summary = summary,
                timestamp = System.currentTimeMillis()
            )
        )
        save(current.take(MAX_ENTRIES))
    }

    fun delete(id: String) {
        save(list().filterNot { it.id == id })
    }

    fun clear() {
        preferences.edit().putString(KEY_ENTRIES, "[]").apply()
    }

    fun exportJson(): String {
        return preferences.getString(KEY_ENTRIES, "[]").orEmpty()
    }

    fun importJson(rawJson: String, replaceExisting: Boolean) {
        val importedEntries = parseEntries(rawJson)
        val merged = if (replaceExisting) {
            importedEntries
        } else {
            val combined = (importedEntries + list()).associateBy { it.rawValue }
            combined.values.sortedByDescending { it.timestamp }
        }
        save(merged.take(MAX_ENTRIES))
    }

    private fun parseEntries(rawJson: String): List<ScanHistoryEntry> {
        val array = JSONArray(rawJson)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ScanHistoryEntry(
                        id = item.optString(KEY_ID).ifBlank { UUID.randomUUID().toString() },
                        rawValue = item.optString(KEY_RAW_VALUE),
                        type = item.optString(KEY_TYPE),
                        title = item.optString(KEY_TITLE),
                        summary = item.optString(KEY_SUMMARY),
                        timestamp = item.optLong(KEY_TIMESTAMP)
                    )
                )
            }
        }.filter { it.rawValue.isNotBlank() }
    }

    private fun save(entries: List<ScanHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, entry.id)
                    put(KEY_RAW_VALUE, entry.rawValue)
                    put(KEY_TYPE, entry.type)
                    put(KEY_TITLE, entry.title)
                    put(KEY_SUMMARY, entry.summary)
                    put(KEY_TIMESTAMP, entry.timestamp)
                }
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "scan_history_store"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_ID = "id"
        private const val KEY_RAW_VALUE = "raw_value"
        private const val KEY_TYPE = "type"
        private const val KEY_TITLE = "title"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val MAX_ENTRIES = 50
    }
}
