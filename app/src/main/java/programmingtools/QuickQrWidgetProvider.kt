package com.programmingtools.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.RemoteViews

class QuickQrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuickQrWidgetProvider::class.java)
            manager.getAppWidgetIds(componentName).forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        PerformanceTracer.trace("widget_update", mapOf("widget_id" to widgetId.toString())) {
            val clipboardText = readClipboardText(context).trim()
            val displayText = if (clipboardText.isBlank()) {
                context.getString(R.string.widget_clipboard_empty_state)
            } else {
                clipboardText
            }

            val bitmap = createWidgetQrBitmap(context, clipboardText)
            val views = RemoteViews(context.packageName, R.layout.widget_quick_qr).apply {
                setTextViewText(R.id.textViewWidgetTitle, context.getString(R.string.widget_title))
                setTextViewText(R.id.textViewWidgetSubtitle, displayText.take(80))
                setImageViewBitmap(R.id.imageViewWidgetQr, bitmap)

                setOnClickPendingIntent(
                    R.id.buttonWidgetRefresh,
                    createRefreshPendingIntent(context)
                )
                setOnClickPendingIntent(
                    R.id.imageViewWidgetQr,
                    createLaunchPendingIntent(context, clipboardText)
                )
                setOnClickPendingIntent(
                    R.id.textViewWidgetSubtitle,
                    createLaunchPendingIntent(context, clipboardText)
                )
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun createWidgetQrBitmap(context: Context, text: String): Bitmap {
        val widgetText = if (text.isBlank()) {
            context.getString(R.string.widget_sample_text)
        } else {
            text
        }
        return QRCodeGenerator().generateQRCode(
            text = widgetText,
            width = 320,
            height = 320,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE,
            designStyle = MainActivity.DesignStyle.MINIMAL,
            eyeStyle = MainActivity.EyeStyle.CLASSIC,
            centerBadge = MainActivity.CenterBadge.NONE,
            centerLogo = null
        )
    }

    private fun createRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, QuickQrWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createLaunchPendingIntent(context: Context, clipboardText: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_widget_text", clipboardText)
            putExtra("extra_widget_autogenerate", clipboardText.isNotBlank())
        }
        return PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun readClipboardText(context: Context): String {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val clipData: ClipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) {
            return ""
        }
        return clipData.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    companion object {
        private const val ACTION_REFRESH_WIDGET = "com.programmingtools.app.action.REFRESH_WIDGET"
    }
}
