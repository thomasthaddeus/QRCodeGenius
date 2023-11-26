package com.example.programmingtools

import android.graphics.Bitmap
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

        val editText = findViewById<EditText>(R.id.editTextText)
        imageViewQRCode = findViewById(R.id.imageViewQRCode)
        val buttonGenerate = findViewById<Button>(R.id.buttonGenerate)

        buttonGenerate.setOnClickListener {
            val text = editText.text.toString()
            bitmap = qrCodeGenerator.generateQRCode(text, 512, 512)
            imageViewQRCode.setImageBitmap(bitmap)

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
}

class QRCodeGenerator {
    fun generateQRCode(text: String, width: Int, height: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) -0x1000000 else -0x1)
            }
        }
        return bmp
    }
}