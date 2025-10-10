package com.grindrplus.core

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.grindrplus.GrindrPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

object SqlTransactionLogger {
    private const val FILENAME = "transactions.sql"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    suspend fun log(sql: String, args: Array<out Any?>? = null) {
        if (sql.isBlank()) return

        withContext(Dispatchers.IO) {
            try {
                val context = GrindrPlus.context
                val storageUriStr = Config.get("storage_uri", "") as? String
                if (storageUriStr.isNullOrEmpty()) return@withContext

                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(storageUriStr))
                if (docDir == null || !docDir.canWrite()) {
                    Logger.w("SqlTransactionLogger: Cannot write to directory.")
                    return@withContext
                }

                var logFile = docDir.findFile(FILENAME)
                if (logFile == null) {
                    logFile = docDir.createFile("application/sql", FILENAME)
                }

                if (logFile == null) return@withContext

                // Build a more detailed log entry
                val logEntry = buildString {
                    append("-- Transaction at ${dateFormat.format(Date())}\n")
                    val formattedSql = if (sql.trim().endsWith(";")) sql.trim() else "${sql.trim()};"
                    append(formattedSql)
                    if (!args.isNullOrEmpty()) {
                        append("\n-- ARGS: ${args.joinToString(", ") { it?.toString() ?: "NULL" }}\n")
                    }
                    append("\n")
                }

                context.contentResolver.openOutputStream(logFile.uri, "wa")?.use {
                    it.write(logEntry.toByteArray())
                }
            } catch (e: IOException) {
                Logger.e("Failed to write to SQL transaction log: ${e.message}")
            }
        }
    }
}