package com.receiptsketch.app.printer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbEscPosPrinterBackend(context: Context) : PrinterBackend {
    override val name = "USB ESC/POS"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    override fun isAvailable(): Boolean = findPrinter() != null

    fun hasPermission(): Boolean = findPrinter()?.let(usbManager::hasPermission) ?: true

    fun requestPermission(context: Context, action: String) {
        val device = findPrinter() ?: return
        val permissionIntent = Intent(action).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(context, 0, permissionIntent, flags)
        usbManager.requestPermission(device, pendingIntent)
    }

    override fun printBitmap(bitmap: android.graphics.Bitmap): PrintResult {
        val device = findPrinter() ?: return PrintResult.Failure("Printer not found")
        if (!usbManager.hasPermission(device)) return PrintResult.Failure("USB permission denied")
        val target = findTargetInterface(device) ?: return PrintResult.Failure("Could not open printer")
        val connection = usbManager.openDevice(device) ?: return PrintResult.Failure("Could not open printer")

        return try {
            print(connection, target.first, target.second, bitmap)
        } catch (error: Throwable) {
            Log.e(TAG, "USB print failed", error)
            PrintResult.Failure("Print failed: ${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            try {
                connection.releaseInterface(target.first)
            } catch (_: Throwable) {
                // The connection is closed below even if the interface was not claimed.
            }
            connection.close()
        }
    }

    private fun print(
        connection: UsbDeviceConnection,
        usbInterface: UsbInterface,
        endpoint: UsbEndpoint,
        bitmap: android.graphics.Bitmap
    ): PrintResult {
        if (!connection.claimInterface(usbInterface, true)) {
            return PrintResult.Failure("Printer connection failed")
        }
        Log.i(TAG, "device claimed, endpoint packetSize=${endpoint.maxPacketSize}")
        send(connection, endpoint, EscPosEncoder.initialize)
        for (chunk in EscPosEncoder.rasterChunks(bitmap)) send(connection, endpoint, chunk)
        send(connection, endpoint, EscPosEncoder.feedFourLines)

        val cutSent = try {
            send(connection, endpoint, EscPosEncoder.partialCut)
            true
        } catch (cutError: Throwable) {
            Log.w(TAG, "Image printed but cutter command failed", cutError)
            false
        }
        return PrintResult.Success(name, cutSent)
    }

    private fun send(connection: UsbDeviceConnection, endpoint: UsbEndpoint, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(16_384, bytes.size - offset)
            val sent = connection.bulkTransfer(endpoint, bytes, offset, count, TIMEOUT_MS)
            if (sent <= 0) throw IllegalStateException("Printer connection failed")
            offset += sent
        }
        Log.d(TAG, "sent ${bytes.size} bytes")
    }

    private fun findPrinter(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { findTargetInterface(it) != null }
    }

    private fun findTargetInterface(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)
            val printerClass = device.deviceClass == UsbConstants.USB_CLASS_PRINTER ||
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER
            if (!printerClass) continue
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_OUT &&
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                ) {
                    return usbInterface to endpoint
                }
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ReceiptSketch-USB"
        const val TIMEOUT_MS = 10_000
    }
}
