package com.receiptsketch.app.printer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object EscPosEncoder {
    val initialize = byteArrayOf(0x1B, 0x40)
    val feedFourLines = byteArrayOf(0x1B, 0x64, 0x04)
    val partialCut = byteArrayOf(0x1D, 0x56, 0x01)

    fun rasterChunks(bitmap: Bitmap, maxRows: Int = 192): List<ByteArray> {
        require(bitmap.width > 0 && bitmap.height > 0)
        val chunks = ArrayList<ByteArray>()
        var startY = 0
        while (startY < bitmap.height) {
            val rows = minOf(maxRows, bitmap.height - startY)
            chunks += rasterChunk(bitmap, startY, rows)
            startY += rows
        }
        return chunks
    }

    private fun rasterChunk(bitmap: Bitmap, startY: Int, rows: Int): ByteArray {
        val bytesPerRow = (bitmap.width + 7) / 8
        val output = ByteArrayOutputStream(8 + bytesPerRow * rows)
        output.write(byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (bytesPerRow and 0xFF).toByte(),
            ((bytesPerRow shr 8) and 0xFF).toByte(),
            (rows and 0xFF).toByte(),
            ((rows shr 8) and 0xFF).toByte()
        ))

        val pixels = IntArray(bitmap.width)
        for (y in startY until startY + rows) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (byteIndex in 0 until bytesPerRow) {
                var value = 0
                for (bit in 0 until 8) {
                    val x = byteIndex * 8 + bit
                    if (x < bitmap.width && Color.red(pixels[x]) < 128) {
                        value = value or (0x80 shr bit)
                    }
                }
                output.write(value)
            }
        }
        return output.toByteArray()
    }
}
