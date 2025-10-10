package com.grindrplus.core

import android.annotation.SuppressLint
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.grindrplus.GrindrPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object HttpBodyLogger {
    private const val FILENAME = "http_body_log.jsonl"
    @SuppressLint("StaticFieldLeak")
    private var grindrPlus: GrindrPlus? = null

    fun initialize(grindrPlusInstance: GrindrPlus) {
        this.grindrPlus = grindrPlusInstance
    }

    fun log(url: String, method: String, body: String?) {
        if (body.isNullOrEmpty() || !body.trim().startsWith("{")) {
            return
        }

        GrindrPlus.executeAsync {
            logInternal(url, method, body)
        }
    }

    private suspend fun logInternal(url: String, method: String, body: String?) {
        withContext(Dispatchers.IO) {
            try {
                val gp = grindrPlus ?: throw IllegalStateException("HttpBodyLogger not initialized")
                val context = gp.context
                val storageUriStr = Config.get("storage_uri", "") as? String
                if (storageUriStr.isNullOrEmpty()) {
                    // Don't log if the storage location isn't set
                    return@withContext
                }

                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(storageUriStr))
                if (docDir == null || !docDir.canWrite()) {
                    Logger.e("HttpBodyLogger: Cannot write to the selected directory.")
                    return@withContext
                }

                var logFile = docDir.findFile(FILENAME)
                if (logFile == null) {
                    logFile = docDir.createFile("application/json-lines", FILENAME)
                }

                if (logFile == null) {
                    Logger.e("HttpBodyLogger: Failed to create log file.")
                    return@withContext
                }

                // Create a JSON object for the log entry
                val logEntry = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis() / 1000)
                    put("url", url)
                    put("method", method)
                    // Attempt to parse the body as a JSONObject for clean formatting
                    try {
                        put("response_body", JSONObject(body))
                    } catch (e: Exception) {
                        put("response_body", body) // Fallback to string if not a valid JSON
                    }
                }

                // Append the JSON string as a new line to the file
                context.contentResolver.openOutputStream(logFile.uri, "wa")?.use { outputStream ->
                    outputStream.write((logEntry.toString() + "\n").toByteArray())
                }

            } catch (e: Exception) {
                Logger.e("Failed to write to HTTP body log file: ${e.message}")
            }
        }
    }
}