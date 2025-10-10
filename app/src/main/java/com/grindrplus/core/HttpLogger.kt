package com.grindrplus.core

import android.os.Environment
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HttpLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrPlus_HttpLogs.txt"
    )

    private fun formatHeaders(headers: okhttp3.Headers): String {
        return headers.toMultimap().entries.joinToString("\n") { (key, values) ->
            "    $key: ${values.joinToString(", ")}"
        }.ifEmpty { "    (No headers)" }
    }

    private fun getRequestBody(request: Request): String {
        return try {
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            "(Error reading body: ${e.message})"
        }
    }

    fun log(request: Request, response: Response) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val responseBody = response.peekBody(Long.MAX_VALUE).string() // Safely peek at the body

            val logMessage = buildString {
                append("[$timestamp]\n")
                append("--- REQUEST -->\n")
                append("  ${request.method} ${request.url}\n")
                append("  Headers:\n")
                append(formatHeaders(request.headers))
                if (request.body != null) {
                    append("\n  Body:\n")
                    append("    ${getRequestBody(request)}")
                }
                append("\n\n<-- RESPONSE ---\n")
                append("  ${response.code} ${response.message}\n")
                append("  Headers:\n")
                append(formatHeaders(response.headers))
                if (responseBody.isNotEmpty()) {
                    append("\n  Body:\n")
                    append("    $responseBody")
                }
                append("\n----------------------------------------\n\n")
            }

            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP log file: ${e.message}")
        }
    }
}