package com.receiptsketch.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.receiptsketch.app.printer.PrintResult
import com.receiptsketch.app.printer.PrinterManager
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var drawingView: DrawingView
    private lateinit var penButton: Button
    private lateinit var eraserButton: Button
    private lateinit var printButton: Button
    private lateinit var printerManager: PrinterManager
    private val printExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        enterImmersiveMode()

        Log.i(TAG, "manufacturer=${android.os.Build.MANUFACTURER}, model=${android.os.Build.MODEL}, android=${android.os.Build.VERSION.RELEASE}")

        drawingView = findViewById(R.id.drawingView)
        penButton = findViewById(R.id.penButton)
        eraserButton = findViewById(R.id.eraserButton)
        printButton = findViewById(R.id.printButton)
        printerManager = PrinterManager(applicationContext)

        penButton.setOnClickListener {
            drawingView.usePen()
            showActiveTool(true)
        }
        eraserButton.setOnClickListener {
            drawingView.useEraser()
            showActiveTool(false)
        }
        findViewById<Button>(R.id.clearButton).setOnClickListener { confirmClear() }
        printButton.setOnClickListener { printDrawing() }
        showActiveTool(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        printerManager.close()
        printExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_question)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ -> drawingView.clear() }
            .show()
    }

    private fun printDrawing() {
        if (!drawingView.hasDrawing()) {
            Toast.makeText(this, R.string.draw_first, Toast.LENGTH_SHORT).show()
            return
        }

        printButton.isEnabled = false
        val source = drawingView.bitmapCopy()
        printerManager.ensureUsbPermission { permissionResult ->
            if (permissionResult != null) {
                source.recycle()
                showPrintResult(permissionResult)
                return@ensureUsbPermission
            }
            printExecutor.execute {
                val result = try {
                    printerManager.printBitmap(source)
                } finally {
                    source.recycle()
                }
                runOnUiThread { showPrintResult(result) }
            }
        }
    }

    private fun showPrintResult(result: PrintResult) {
        printButton.isEnabled = true
        val message = when (result) {
            is PrintResult.Success -> getString(R.string.printed)
            is PrintResult.Failure -> result.message
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showActiveTool(penActive: Boolean) {
        penButton.alpha = if (penActive) 1f else 0.55f
        eraserButton.alpha = if (penActive) 0.55f else 1f
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private companion object {
        const val TAG = "ReceiptSketch"
    }
}
