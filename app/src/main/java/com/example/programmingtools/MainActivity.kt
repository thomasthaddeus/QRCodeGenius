package com.example.programmingtools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var imageViewQRCode: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var editTextSize: EditText // For user to input desired size
    private lateinit var editTextSaveText: EditText // For user to input text to save beneath the image
    private lateinit var editTextColor: EditText // For user to input desired QR code color
    private val qrCodeGenerator = QRCodeGenerator()

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.let {
                saveImageToUri(bitmap, it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Assuming you have a corresponding XML layout

        imageViewQRCode = findViewById(R.id.imageViewQRCode1)
        val editText = findViewById<EditText>(R.id.editTextText)
        val buttonGenerate = findViewById<Button>(R.id.buttonGenerate1)

        editTextSize = findViewById(R.id.editTextSize) // Assuming you have this in your layout
        editTextSaveText = findViewById(R.id.editTextSaveText) // Assuming you have this in your layout
        editTextColor = findViewById(R.id.editTextColor) // Assuming you have this in your layout

        buttonGenerate.setOnClickListener {
            val text = editText.text.toString()
            val size = editTextSize.text.toString().toIntOrNull() ?: 512 // Default size if input is invalid

            // Get the color from the input field
            val color = Color.parseColor(editTextColor.text.toString())

            // Generate QR code with custom color
            bitmap = qrCodeGenerator.generateQRCode(text, size, size, color)
            imageViewQRCode.setImageBitmap(bitmap)

            // Combine QR code and saved text into one image
            bitmap = combineImageAndText(bitmap, editTextSaveText.text.toString())

            // Launch intent to save the image
            createDocument.launch("QRCode.png")
        }
    }

    private fun saveImageToUri(bitmap: Bitmap, uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                runOnUiThread {
                    Toast.makeText(this, "Image saved successfully", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(this, "Error saving image: ${e.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun combineImageAndText(image: Bitmap, text: String): Bitmap {
        // Implementation to combine the QR code and text into a single Bitmap
        // This can be done using Canvas and Paint classes
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK // Set the text color
        paint.textSize = 40f // Set the text size
        paint.typeface = Typeface.DEFAULT_BOLD // Set the typeface as bold

        val textWidth = paint.measureText(text)
        val combinedImage = Bitmap.createBitmap(image.width, image.height + (paint.textSize * 1.5).toInt(), Bitmap.Config.ARGB_8888)

        val canvas = Canvas(combinedImage)
        canvas.drawBitmap(image, 0f, 0f, null) // Draw the QR code on the canvas
        canvas.drawText(text, (image.width - textWidth) / 2, image.height + paint.textSize, paint) // Draw the text beneath the QR code

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