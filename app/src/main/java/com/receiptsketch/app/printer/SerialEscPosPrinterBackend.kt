package com.receiptsketch.app.printer

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class SerialEscPosPrinterBackend : PrinterBackend {
    override val name = "Serial ESC/POS"

    override fun isAvailable(): Boolean = findWritableDevice() != null

    override fun printBitmap(bitmap: Bitmap): PrintResult {
        val device = findWritableDevice() ?: return PrintResult.Failure("Could not open printer")
        return try {
            FileOutputStream(device).use { output ->
                output.write(EscPosEncoder.initialize)
                for (chunk in EscPosEncoder.rasterChunks(bitmap)) output.write(chunk)
                output.write(EscPosEncoder.feedFourLines)
                output.flush()
                val cutSent = try {
                    output.write(EscPosEncoder.partialCut)
                    output.flush()
                    true
                } catch (cutError: Throwable) {
                    Log.w(TAG, "Image printed but cutter command failed", cutError)
                    false
                }
                Log.i(TAG, "printed through ${device.absolutePath}")
                PrintResult.Success(name, cutSent)
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Serial permission denied", error)
            PrintResult.Failure("Could not open printer", error)
        } catch (error: Throwable) {
            Log.e(TAG, "Serial print failed", error)
            PrintResult.Failure("Print failed: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private fun findWritableDevice(): File? {
        return DEVICE_PATHS.asSequence()
            .filter { it.isNotBlank() }
            .map(::File)
            .firstOrNull { it.exists() && it.canWrite() }
    }

    private companion object {
        const val TAG = "ReceiptSketch-Serial"
        // Fill this only after ADB/device documentation confirms the printer's serial node.
        // Guessing ttyS* can write receipt bytes into an unrelated scanner or COM port.
        const val CONFIGURED_SERIAL_PATH = ""
        val DEVICE_PATHS = listOf(CONFIGURED_SERIAL_PATH, "/dev/usb/lp0")
    }
}
