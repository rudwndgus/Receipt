package com.receiptsketch.app.printer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class PrinterManager(private val context: Context) {
    private val usbBackend = UsbEscPosPrinterBackend(context)
    private val backends: List<PrinterBackend> = listOf(
        WintecPrinterBackend(),
        usbBackend,
        SerialEscPosPrinterBackend()
    )
    private var permissionReceiver: BroadcastReceiver? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun ensureUsbPermission(callback: (PrintResult.Failure?) -> Unit) {
        if (!usbBackend.isAvailable() || usbBackend.hasPermission()) {
            callback(null)
            return
        }

        closePermissionReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context, intent: Intent) {
                if (intent.action != USB_PERMISSION_ACTION) return
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG_USB, "USB permission granted=$granted device=$device")
                closePermissionReceiver()
                callback(if (granted) null else PrintResult.Failure("USB permission denied"))
            }
        }
        permissionReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(USB_PERMISSION_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, IntentFilter(USB_PERMISSION_ACTION))
        }
        usbBackend.requestPermission(context, USB_PERMISSION_ACTION)
    }

    fun printBitmap(source: Bitmap): PrintResult {
        val prepared = BitmapPreprocessor.prepare(source)
            ?: return PrintResult.Failure("Draw something first")
        return try {
            val backend = backends.firstOrNull { candidate ->
                try {
                    candidate.isAvailable()
                } catch (error: Throwable) {
                    Log.e(TAG, "Availability check failed for ${candidate.name}", error)
                    false
                }
            } ?: return PrintResult.Failure("Printer not found")

            Log.i(TAG, "selected backend=${backend.name}")
            backend.printBitmap(prepared).also { result ->
                when (result) {
                    is PrintResult.Success -> Log.i(TAG, "print complete backend=${result.backend}, cut=${result.cutSent}")
                    is PrintResult.Failure -> Log.e(TAG, result.message, result.cause)
                }
            }
        } finally {
            prepared.recycle()
        }
    }

    fun close() {
        closePermissionReceiver()
    }

    private fun closePermissionReceiver() {
        val receiver = permissionReceiver ?: return
        permissionReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered by Android.
        }
    }

    private companion object {
        const val USB_PERMISSION_ACTION = "com.receiptsketch.app.USB_PERMISSION"
        const val TAG = "ReceiptSketch-Printer"
        const val TAG_USB = "ReceiptSketch-USB"
    }
}
