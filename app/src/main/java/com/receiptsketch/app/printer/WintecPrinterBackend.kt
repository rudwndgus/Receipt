package com.receiptsketch.app.printer

import android.graphics.Bitmap
import android.util.Log

/**
 * Placeholder for a real Wintec SDK supplied with a specific device shipment.
 * No public AnyPOS80 Android printer API was found, so this backend deliberately
 * never claims availability and never guesses vendor class or method names.
 */
class WintecPrinterBackend : PrinterBackend {
    override val name = "Wintec SDK"

    override fun isAvailable(): Boolean = false

    override fun printBitmap(bitmap: Bitmap): PrintResult {
        Log.w(TAG, "No verified Wintec Android printer SDK is bundled")
        return PrintResult.Failure("Printer service unavailable")
    }

    private companion object {
        const val TAG = "ReceiptSketch-Wintec"
    }
}
