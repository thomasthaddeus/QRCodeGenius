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
import android.widget.SeekBar
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    companion object {
        private const val STATE_TEXT = "state_text"
        private const val STATE_SIZE = "state_size"
        private const val STATE_SAVE_TEXT = "state_save_text"
        private const val STATE_COLOR = "state_color"
        private const val STATE_HAS_QR = "state_has_qr"
    }

    private lateinit var imageViewQRCode: ImageView
    private lateinit var editText: EditText
    private lateinit var editTextSize: EditText
    private lateinit var editTextSaveText: EditText
    private lateinit var buttonGenerate: Button
    private lateinit var buttonSave: Button
    private lateinit var buttonShare: Button
    private lateinit var buttonPickColor: Button
    private lateinit var buttonViewSample: Button
    private lateinit var viewSelectedColor: View
    private lateinit var textViewSelectedColor: TextView
    private var generatedBitmap: Bitmap? = null
    private var pendingSaveBitmap: Bitmap? = null
    private var selectedColor: Int = Color.BLACK
    private val qrCodeGenerator = QRCodeGenerator()

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            val bitmapToSave = pendingSaveBitmap
            if (uri != null && bitmapToSave != null) {
                saveImageToUri(bitmapToSave, uri)
            }
            pendingSaveBitmap = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageViewQRCode = findViewById(R.id.imageViewQRCode)
        editText = findViewById(R.id.editTextText)
        editTextSize = findViewById(R.id.editTextSize)
        editTextSaveText = findViewById(R.id.editTextSaveText)
        buttonGenerate = findViewById(R.id.buttonGenerate)
        buttonSave = findViewById(R.id.buttonSave)
        buttonShare = findViewById(R.id.buttonShare)
        buttonPickColor = findViewById(R.id.buttonPickColor)
        buttonViewSample = findViewById(R.id.buttonViewSample)
        viewSelectedColor = findViewById(R.id.viewSelectedColor)
        textViewSelectedColor = findViewById(R.id.textViewSelectedColor)

        updateSelectedColor(Color.parseColor(getString(R.string.default_qr_color)))

        buttonGenerate.setOnClickListener {
            if (!renderQrFromInputs(showValidationError = true)) {
                return@setOnClickListener
            }
        }

        buttonSave.setOnClickListener {
            val bitmapToSave = generatedBitmap
            if (bitmapToSave == null) {
                Toast.makeText(this, getString(R.string.generate_before_saving), Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            val outputBitmap = combineImageAndText(bitmapToSave, editTextSaveText.text.toString().trim())
            pendingSaveBitmap = outputBitmap
            createDocument.launch(getString(R.string.default_qr_filename))
        }

        buttonShare.setOnClickListener {
            val bitmapToShare = generatedBitmap
            if (bitmapToShare == null) {
                Toast.makeText(this, getString(R.string.generate_before_sharing), Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            shareBitmap(combineImageAndText(bitmapToShare, editTextSaveText.text.toString().trim()))
        }

        buttonPickColor.setOnClickListener {
            showColorPickerDialog()
        }

        buttonViewSample.setOnClickListener {
            val sampleText = "Sample QR Code"
            val sampleSize = 256
            val sampleColor = selectedColor
            val sampleBitmap =
                qrCodeGenerator.generateQRCode(sampleText, sampleSize, sampleSize, sampleColor)
            imageViewQRCode.setImageBitmap(sampleBitmap)
            imageViewQRCode.visibility = View.VISIBLE
            generatedBitmap = sampleBitmap
        }

        if (savedInstanceState != null) {
            editText.setText(savedInstanceState.getString(STATE_TEXT).orEmpty())
            editTextSize.setText(savedInstanceState.getString(STATE_SIZE).orEmpty())
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
        outState.putString(STATE_SAVE_TEXT, editTextSaveText.text.toString())
        outState.putString(STATE_COLOR, formatColor(selectedColor))
        outState.putBoolean(STATE_HAS_QR, generatedBitmap != null)
    }

    private fun saveImageToUri(bitmap: Bitmap, uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.image_saved_successfully),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.error_saving_image, e.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderQrFromInputs(showValidationError: Boolean): Boolean {
        val text = editText.text.toString().trim()
        if (text.isEmpty()) {
            imageViewQRCode.setImageDrawable(null)
            imageViewQRCode.visibility = View.GONE
            generatedBitmap = null
            if (showValidationError) {
                Toast.makeText(this, getString(R.string.enter_text_error), Toast.LENGTH_LONG).show()
            }
            return false
        }

        val size = editTextSize.text.toString().toIntOrNull() ?: 256
        val color = selectedColor
        val qrBitmap = qrCodeGenerator.generateQRCode(text, size, size, color)
        imageViewQRCode.setImageBitmap(qrBitmap)
        imageViewQRCode.visibility = View.VISIBLE
        generatedBitmap = qrBitmap
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
                updateSelectedColor(
                    Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun combineImageAndText(image: Bitmap, text: String): Bitmap {
        if (text.isBlank()) {
            return image
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
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
        canvas.drawColor(Color.WHITE)
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
