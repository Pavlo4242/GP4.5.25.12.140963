package com.grindrplus.core

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToastLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrPlus_Toasts.txt"
    )

    fun log(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n"

            // appendText will create the file if it doesn't exist
            logFile.appendText(logMessage)
        } catch (e: Exception) {
            // Log error to the main logger if file writing fails
            Logger.e("Failed to write to Toast log file: ${e.message}")
        }
    }
}