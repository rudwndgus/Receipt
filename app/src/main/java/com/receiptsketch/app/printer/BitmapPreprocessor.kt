package com.receiptsketch.app.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapPreprocessor {
    private const val TAG = "ReceiptSketch-Printer"
    private const val PAPER_WIDTH = 576
    private const val PAPER_MARGIN = 24
    private const val MAX_CONTENT_HEIGHT = 4096
    private const val INK_LIMIT = 245
    private const val THRESHOLD = 180

    fun prepare(source: Bitmap): Bitmap? {
        Log.i(TAG, "source bitmap=${source.width}x${source.height}")
        val bounds = findInkBounds(source) ?: return null
        val sourceMargin = 12
        bounds.left = max(0, bounds.left - sourceMargin)
        bounds.top = max(0, bounds.top - sourceMargin)
        bounds.right = min(source.width, bounds.right + sourceMargin)
        bounds.bottom = min(source.height, bounds.bottom + sourceMargin)

        val contentWidth = PAPER_WIDTH - PAPER_MARGIN * 2
        val scale = minOf(
            contentWidth.toFloat() / bounds.width().toFloat(),
            MAX_CONTENT_HEIGHT.toFloat() / bounds.height().toFloat()
        )
        val scaledWidth = max(1, (bounds.width() * scale).roundToInt())
        val contentHeight = max(1, (bounds.height() * scale).roundToInt())
        val output = Bitmap.createBitmap(PAPER_WIDTH, contentHeight + PAPER_MARGIN * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val leftMargin = (PAPER_WIDTH - scaledWidth) / 2
        val destination = Rect(leftMargin, PAPER_MARGIN, leftMargin + scaledWidth, PAPER_MARGIN + contentHeight)
        canvas.drawBitmap(source, bounds, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        makeBlackAndWhite(output)
        Log.i(TAG, "print bitmap=${output.width}x${output.height}")
        return output
    }

    private fun findInkBounds(bitmap: Bitmap): Rect? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                if (luminance(pixel) < INK_LIMIT) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        return if (right < left || bottom < top) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun makeBlackAndWhite(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in pixels.indices) {
                pixels[x] = if (luminance(pixels[x]) < THRESHOLD) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }

    private fun luminance(color: Int): Int {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
    }
}
