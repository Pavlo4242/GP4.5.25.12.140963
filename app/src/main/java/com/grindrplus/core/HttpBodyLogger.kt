package com.grindrplus.core

import com.grindrplus.GrindrPlus
import com.grindrplus.persistence.model.HttpBodyLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HttpBodyLogger {
    private val scope = CoroutineScope(Dispatchers.IO)
    private const val MAX_ENTRIES = 10_000
    private const val DELETE_BATCH_SIZE = 1_000 // Delete 1,000 entries at a time to stay under limit

    fun log(url: String, method: String, body: String?) {
        if (body.isNullOrEmpty() || !body.trim().startsWith("{")) {
            Logger.d("Skipping non-JSON or empty body for $method $url", LogSource.HTTP)
            return
        }

        scope.launch {
            try {
                // Check current entry count
                val count = GrindrPlus.database.httpBodyLogDao().getCount()
                if (count >= MAX_ENTRIES) {
                    // Delete oldest entries to make room
                    val entriesToDelete = (count - MAX_ENTRIES + 1).toInt().coerceAtMost(DELETE_BATCH_SIZE)
                    GrindrPlus.database.httpBodyLogDao().deleteOldest(entriesToDelete)
                    Logger.i("Deleted $entriesToDelete oldest HTTP log entries to stay under $MAX_ENTRIES limit", LogSource.HTTP)
                }

                // Insert new log entry
                val logEntry = HttpBodyLogEntity(
                    timestamp = System.currentTimeMillis() / 1000,
                    url = url,
                    method = method,
                    response_body = body
                )
                GrindrPlus.database.httpBodyLogDao().insert(logEntry)
                Logger.i("Successfully logged HTTP body for $method $url", LogSource.HTTP)
            } catch (e: Exception) {
                Logger.e("Failed to write to HTTP body log database: ${e.message}", LogSource.HTTP)
            }
        }
    }
}