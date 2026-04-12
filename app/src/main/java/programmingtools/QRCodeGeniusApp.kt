package com.programmingtools.app

import android.app.Application

class QRCodeGeniusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppTelemetry.initialize(this)
        AppTelemetry.logEvent(
            "app_opened",
            mapOf("entry_point" to "application_on_create")
        )
    }
}
