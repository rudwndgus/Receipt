package com.receiptsketch.app.printer

import android.graphics.Bitmap

interface PrinterBackend {
    val name: String
    fun isAvailable(): Boolean
    fun printBitmap(bitmap: Bitmap): PrintResult
}
