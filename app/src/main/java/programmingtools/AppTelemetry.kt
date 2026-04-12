package com.programmingtools.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppTelemetry {
    private const val TAG = "AppTelemetry"
    private const val EVENTS_LOG_FILE = "events.log"
    private const val CRASH_LOG_FILE = "crashes.log"
    private var appContext: Context? = null
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        appContext = context.applicationContext
        installCrashHandler()
        initialized = true
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val entry = buildEntry("event", name, params)
        Log.i(TAG, entry)
        appendToLog(EVENTS_LOG_FILE, entry)
    }

    fun recordNonFatal(name: String, throwable: Throwable, extras: Map<String, String> = emptyMap()) {
        val params = extras + mapOf(
            "message" to (throwable.message ?: "no_message"),
            "type" to throwable::class.java.simpleName
        )
        val entry = buildEntry("non_fatal", name, params)
        Log.e(TAG, entry, throwable)
        appendToLog(CRASH_LOG_FILE, entry)
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val entry = buildEntry(
                type = "fatal",
                name = "uncaught_exception",
                params = mapOf(
                    "thread" to thread.name,
                    "message" to (throwable.message ?: "no_message"),
                    "type" to throwable::class.java.simpleName
                )
            )
            Log.e(TAG, entry, throwable)
            appendToLog(CRASH_LOG_FILE, entry)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun appendToLog(fileName: String, entry: String) {
        val context = appContext ?: return
        runCatching {
            val directory = File(context.filesDir, "telemetry").apply { mkdirs() }
            val targetFile = File(directory, fileName)
            targetFile.appendText(entry + System.lineSeparator())
        }.onFailure {
            Log.w(TAG, "Unable to write telemetry log", it)
        }
    }

    private fun buildEntry(type: String, name: String, params: Map<String, String>): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        val formattedParams = if (params.isEmpty()) {
            "-"
        } else {
            params.entries.joinToString(separator = ",") { "${it.key}=${it.value.sanitize()}" }
        }
        return "timestamp=$timestamp type=$type name=$name params=$formattedParams"
    }

    private fun String.sanitize(): String {
        return replace(System.lineSeparator(), " ").replace(",", ";")
    }
}
