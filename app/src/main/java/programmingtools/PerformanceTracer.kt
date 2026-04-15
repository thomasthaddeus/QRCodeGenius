package com.programmingtools.app

import android.util.Log

object PerformanceTracer {
    private const val TAG = "QRCodeGeniusPerf"
    private val records = ArrayDeque<String>()

    fun isEnabled(): Boolean = BuildConfig.ENABLE_PERF_LOGGING

    inline fun <T> trace(section: String, metadata: Map<String, String> = emptyMap(), block: () -> T): T {
        if (!isEnabled()) {
            return block()
        }

        val startNs = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            record(section, durationMs, metadata)
        }
    }

    fun record(section: String, durationMs: Long, metadata: Map<String, String> = emptyMap()) {
        if (!isEnabled()) {
            return
        }

        val formatted = buildString {
            append(section)
            append(": ")
            append(durationMs)
            append(" ms")
            if (metadata.isNotEmpty()) {
                append(" | ")
                append(metadata.entries.joinToString(", ") { "${it.key}=${it.value}" })
            }
        }

        Log.d(TAG, formatted)
        if (records.size >= 40) {
            records.removeFirst()
        }
        records.addLast(formatted)
    }

    fun recentRecords(): String {
        if (!isEnabled()) {
            return ""
        }
        return records.joinToString(separator = "\n")
    }

    fun clear() {
        records.clear()
    }
}
