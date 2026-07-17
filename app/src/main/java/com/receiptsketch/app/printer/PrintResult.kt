package com.receiptsketch.app.printer

sealed class PrintResult {
    data class Success(val backend: String, val cutSent: Boolean) : PrintResult()
    data class Failure(val message: String, val cause: Throwable? = null) : PrintResult()
}
