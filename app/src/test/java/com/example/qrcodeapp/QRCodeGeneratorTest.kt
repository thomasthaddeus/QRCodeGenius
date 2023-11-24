package com.example.qrcodeapp

import org.junit.Assert.*
import org.junit.Test

class QRCodeGeneratorTest {

    @Test
    fun testGenerateQRCode() {
        val generator = QRCodeGenerator()
        val text = "Test QR Code"
        val width = 512
        val height = 512

        val result = generator.generateQRCode(text, width, height)

        assertNotNull("QR Code bitmap should not be null", result)
        assertEquals("Width of the QR code bitmap is incorrect", width, result.width)
        assertEquals("Height of the QR code bitmap is incorrect", height, result.height)
        // Additional assertions can be made here
    }
}
