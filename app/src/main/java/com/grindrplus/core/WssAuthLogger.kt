package com.grindrplus.core

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WssAuthLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrPlus_WssAuth.txt"
    )

    fun log(profileId: String, xmppToken: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = buildString {
                append("[$timestamp]\n")
                append("Captured WSS/XMPP Credentials:\n")
                append("  profileId: $profileId\n")
                append("  xmppToken: $xmppToken\n")
                append("----------------------------------------\n\n")
            }

            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Logger.e("Failed to write to WSS Auth log file: ${e.message}")
        }
    }
}