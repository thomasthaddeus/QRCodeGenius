package com.programmingtools.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.annotation.StringRes
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    private enum class SaveFormat(
        @param:StringRes val labelResId: Int,
        val fileExtension: String,
        val mimeType: String,
        val compressFormat: Bitmap.CompressFormat
    ) {
        PNG(R.string.save_format_png, "png", "image/png", Bitmap.CompressFormat.PNG),
        JPEG(R.string.save_format_jpeg, "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
        WEBP(R.string.save_format_webp, "webp", "image/webp", Bitmap.CompressFormat.WEBP);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): SaveFormat {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: PNG
            }
        }
    }

    companion object {
        private const val STATE_TEXT = "state_text"
        private const val STATE_SIZE = "state_size"
        private const val STATE_SAVE_FORMAT = "state_save_format"
        private const val STATE_SAVE_TEXT = "state_save_text"
        private const val STATE_COLOR = "state_color"
        private const val STATE_HAS_QR = "state_has_qr"
    }

    private lateinit var imageViewQRCode: ImageView
    private lateinit var editText: EditText
    private lateinit var editTextSize: EditText
    private lateinit var editTextSaveText: EditText
    private lateinit var buttonPickSaveFormat: Button
    private lateinit var buttonGenerate: Button
    private lateinit var buttonSave: Button
    private lateinit var buttonShare: Button
    private lateinit var buttonPickColor: Button
    private lateinit var buttonViewSample: Button
    private lateinit var viewSelectedColor: View
    private lateinit var textViewSelectedSaveFormat: TextView
    private lateinit var textViewSelectedColor: TextView
    private lateinit var textViewPreviewStatus: TextView
    private var generatedBitmap: Bitmap? = null
    private var pendingSaveBitmap: Bitmap? = null
    private var currentSaveFormat: SaveFormat = SaveFormat.PNG
    private var pendingSaveFormat: SaveFormat = SaveFormat.PNG
    private var selectedColor: Int = Color.BLACK
    private val qrCodeGenerator = QRCodeGenerator()

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val bitmapToSave = pendingSaveBitmap
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null && bitmapToSave != null) {
                saveImageToUri(bitmapToSave, uri, pendingSaveFormat)
            }
            pendingSaveBitmap = null
            pendingSaveFormat = SaveFormat.PNG
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageViewQRCode = findViewById(R.id.imageViewQRCode)
        editText = findViewById(R.id.editTextText)
        editTextSize = findViewById(R.id.editTextSize)
        editTextSaveText = findViewById(R.id.editTextSaveText)
        buttonPickSaveFormat = findViewById(R.id.buttonPickSaveFormat)
        buttonGenerate = findViewById(R.id.buttonGenerate)
        buttonSave = findViewById(R.id.buttonSave)
        buttonShare = findViewById(R.id.buttonShare)
        buttonPickColor = findViewById(R.id.buttonPickColor)
        buttonViewSample = findViewById(R.id.buttonViewSample)
        viewSelectedColor = findViewById(R.id.viewSelectedColor)
        textViewSelectedSaveFormat = findViewById(R.id.textViewSelectedSaveFormat)
        textViewSelectedColor = findViewById(R.id.textViewSelectedColor)
        textViewPreviewStatus = findViewById(R.id.textViewPreviewStatus)

        updateSelectedSaveFormat(
            SaveFormat.fromLocalizedLabel(
                getString(R.string.default_save_format),
                ::getString
            )
        )
        updateSelectedColor(Color.parseColor(getString(R.string.default_qr_color)))
        updatePreviewState(hasPreview = false)

        buttonGenerate.setOnClickListener {
            if (!renderQrFromInputs(showValidationError = true)) {
                AppTelemetry.logEvent("generate_attempted_empty_text")
                return@setOnClickListener
            }
        }

        buttonSave.setOnClickListener {
            val bitmapToSave = generatedBitmap
            if (bitmapToSave == null) {
                Toast.makeText(this, getString(R.string.generate_before_saving), Toast.LENGTH_LONG)
                    .show()
                AppTelemetry.logEvent("save_attempted_without_qr")
                return@setOnClickListener
            }

            val outputBitmap = combineImageAndText(bitmapToSave, editTextSaveText.text.toString().trim())
            pendingSaveBitmap = outputBitmap
            pendingSaveFormat = selectedSaveFormat()
            AppTelemetry.logEvent(
                "save_started",
                mapOf("format" to getString(pendingSaveFormat.labelResId))
            )
            announceAccessibilityMessage(getString(R.string.save_started_announcement))
            createDocument.launch(createSaveDocumentIntent(pendingSaveFormat))
        }

        buttonShare.setOnClickListener {
            val bitmapToShare = generatedBitmap
            if (bitmapToShare == null) {
                Toast.makeText(this, getString(R.string.generate_before_sharing), Toast.LENGTH_LONG)
                    .show()
                AppTelemetry.logEvent("share_attempted_without_qr")
                return@setOnClickListener
            }

            AppTelemetry.logEvent("share_started")
            announceAccessibilityMessage(getString(R.string.share_started_announcement))
            shareBitmap(combineImageAndText(bitmapToShare, editTextSaveText.text.toString().trim()))
        }

        buttonPickColor.setOnClickListener {
            showColorPickerDialog()
        }

        buttonPickSaveFormat.setOnClickListener {
            showSaveFormatDialog()
        }

        buttonViewSample.setOnClickListener {
            val sampleText = getString(R.string.sample_qr_text)
            val sampleSize = 256
            val sampleColor = selectedColor
            val sampleBitmap =
                qrCodeGenerator.generateQRCode(sampleText, sampleSize, sampleSize, sampleColor)
            imageViewQRCode.setImageBitmap(sampleBitmap)
            generatedBitmap = sampleBitmap
            updatePreviewState(hasPreview = true)
            AppTelemetry.logEvent("sample_generated")
            announceAccessibilityMessage(getString(R.string.sample_generated_announcement))
        }

        if (savedInstanceState != null) {
            editText.setText(savedInstanceState.getString(STATE_TEXT).orEmpty())
            editTextSize.setText(savedInstanceState.getString(STATE_SIZE).orEmpty())
            restoreSaveFormat(savedInstanceState.getString(STATE_SAVE_FORMAT).orEmpty())
            editTextSaveText.setText(savedInstanceState.getString(STATE_SAVE_TEXT).orEmpty())
            updateSelectedColor(
                parseQrColor(savedInstanceState.getString(STATE_COLOR).orEmpty())
            )

            if (savedInstanceState.getBoolean(STATE_HAS_QR)) {
                renderQrFromInputs(showValidationError = false)
            }
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        try {
            val shareDirectory = File(cacheDir, "shared_images").apply { mkdirs() }
            val shareFile = File(shareDirectory, "qr-code-share.png")
            FileOutputStream(shareFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val imageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(shareIntent, getString(R.string.share_qr_chooser_title))
            )
        } catch (e: IOException) {
            AppTelemetry.recordNonFatal("share_failed", e)
            Toast.makeText(
                this,
                getString(R.string.share_image_error, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TEXT, editText.text.toString())
        outState.putString(STATE_SIZE, editTextSize.text.toString())
        outState.putString(STATE_SAVE_FORMAT, getString(selectedSaveFormat().labelResId))
        outState.putString(STATE_SAVE_TEXT, editTextSaveText.text.toString())
        outState.putString(STATE_COLOR, formatColor(selectedColor))
        outState.putBoolean(STATE_HAS_QR, generatedBitmap != null)
    }

    private fun saveImageToUri(bitmap: Bitmap, uri: Uri, saveFormat: SaveFormat) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val compressSucceeded = bitmap.compress(saveFormat.compressFormat, 100, outputStream)
                outputStream.flush()
                if (!compressSucceeded) {
                    throw IOException("Bitmap compression failed for ${getString(saveFormat.labelResId)}")
                }
                runOnUiThread {
                    AppTelemetry.logEvent(
                        "image_saved",
                        mapOf("format" to getString(saveFormat.labelResId))
                    )
                    Toast.makeText(
                        this,
                        getString(R.string.image_saved_successfully),
                        Toast.LENGTH_LONG
                    ).show()
                    announceAccessibilityMessage(getString(R.string.image_saved_announcement))
                }
            }
        } catch (e: IOException) {
            AppTelemetry.recordNonFatal(
                "save_failed",
                e,
                mapOf("format" to getString(saveFormat.labelResId))
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.error_saving_image, e.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun selectedSaveFormat(): SaveFormat {
        return currentSaveFormat
    }

    private fun restoreSaveFormat(value: String) {
        updateSelectedSaveFormat(
            SaveFormat.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_save_format) },
                ::getString
            )
        )
    }

    private fun createSaveDocumentIntent(saveFormat: SaveFormat): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = saveFormat.mimeType
            putExtra(Intent.EXTRA_TITLE, defaultFileNameFor(saveFormat))
        }
    }

    private fun defaultFileNameFor(saveFormat: SaveFormat): String {
        return "QRCode.${saveFormat.fileExtension}"
    }

    private fun updateSelectedSaveFormat(saveFormat: SaveFormat) {
        currentSaveFormat = saveFormat
        textViewSelectedSaveFormat.text = getString(
            R.string.selected_save_format_format,
            getString(saveFormat.labelResId)
        )
    }

    private fun renderQrFromInputs(showValidationError: Boolean): Boolean {
        val text = editText.text.toString().trim()
        if (text.isEmpty()) {
            imageViewQRCode.setImageDrawable(null)
            generatedBitmap = null
            updatePreviewState(hasPreview = false)
            if (showValidationError) {
                Toast.makeText(this, getString(R.string.enter_text_error), Toast.LENGTH_LONG).show()
            }
            return false
        }

        val size = editTextSize.text.toString().toIntOrNull() ?: 256
        val color = selectedColor
        val qrBitmap = qrCodeGenerator.generateQRCode(text, size, size, color)
        imageViewQRCode.setImageBitmap(qrBitmap)
        generatedBitmap = qrBitmap
        updatePreviewState(hasPreview = true)
        AppTelemetry.logEvent(
            "qr_generated",
            mapOf(
                "size" to size.toString(),
                "color" to formatColor(color),
                "has_caption" to editTextSaveText.text.toString().trim().isNotEmpty().toString()
            )
        )
        if (showValidationError) {
            announceAccessibilityMessage(getString(R.string.qr_generated_announcement))
        }
        return true
    }

    private fun parseQrColor(colorValue: String): Int {
        return try {
            Color.parseColor(colorValue)
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }
    }

    private fun updateSelectedColor(color: Int) {
        selectedColor = color
        viewSelectedColor.setBackgroundColor(color)
        textViewSelectedColor.text = getString(
            R.string.selected_color_format,
            formatColor(color)
        )
        textViewSelectedColor.contentDescription = getString(
            R.string.selected_color_accessibility_format,
            formatColor(color)
        )
    }

    private fun formatColor(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun showColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.viewDialogColorPreview)
        val colorValue = dialogView.findViewById<TextView>(R.id.textViewDialogColorValue)
        val redSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val greenSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val blueSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)

        redSeekBar.progress = Color.red(selectedColor)
        greenSeekBar.progress = Color.green(selectedColor)
        blueSeekBar.progress = Color.blue(selectedColor)

        fun refreshPreview() {
            val color = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            preview.setBackgroundColor(color)
            colorValue.text = getString(R.string.selected_color_format, formatColor(color))
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        redSeekBar.setOnSeekBarChangeListener(listener)
        greenSeekBar.setOnSeekBarChangeListener(listener)
        blueSeekBar.setOnSeekBarChangeListener(listener)
        refreshPreview()

        AlertDialog.Builder(this)
            .setTitle(R.string.color_picker_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newColor =
                    Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
                updateSelectedColor(newColor)
                AppTelemetry.logEvent("color_changed", mapOf("color" to formatColor(newColor)))
                announceAccessibilityMessage(
                    getString(R.string.color_changed_announcement, formatColor(newColor))
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSaveFormatDialog() {
        val formats = SaveFormat.entries.toTypedArray()
        val labels = formats.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = formats.indexOf(selectedSaveFormat()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.save_format_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedSaveFormat(formats[which])
                AppTelemetry.logEvent(
                    "save_format_changed",
                    mapOf("format" to getString(formats[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.save_format_changed_announcement,
                        getString(formats[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updatePreviewState(hasPreview: Boolean) {
        imageViewQRCode.visibility = if (hasPreview) View.VISIBLE else View.GONE
        textViewPreviewStatus.text = if (hasPreview) {
            getString(R.string.image_description)
        } else {
            getString(R.string.preview_unavailable)
        }
    }

    private fun announceAccessibilityMessage(message: String) {
        findViewById<View>(android.R.id.content)?.announceForAccessibility(message)
    }

    private fun combineImageAndText(image: Bitmap, text: String): Bitmap {
        if (text.isBlank()) {
            return image
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = getColor(R.color.qr_caption_text)
        paint.textSize = 40f
        paint.typeface = Typeface.DEFAULT_BOLD

        val textWidth = paint.measureText(text)
        val extraHeight = (paint.textSize * 1.5f).toInt()
        val combinedImage = Bitmap.createBitmap(
            image.width,
            image.height + extraHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(combinedImage)
        canvas.drawColor(getColor(R.color.qr_background))
        canvas.drawBitmap(image, 0f, 0f, null)
        canvas.drawText(text, (image.width - textWidth) / 2, image.height + paint.textSize, paint)

        return combinedImage
    }
}

class QRCodeGenerator {
    fun generateQRCode(text: String, width: Int, height: Int, color: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        // Use the provided color for QR code pixels instead of default black
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) color else -0x1)
            }
        }
        return bmp
    }
}
