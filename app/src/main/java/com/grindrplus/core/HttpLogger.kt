package com.grindrplus.core

import com.grindrplus.GrindrPlus
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.persistence.model.LogEntity
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

object HttpLogger {
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
        if (!(Config.get("enable_http_logging", false) as Boolean)) return

        GPDatabase.DatabaseManager.executeWhenReady {
            try {
                val responseBody = response.peekBody(Long.MAX_VALUE).string()

                GrindrPlus.executeAsync {
                    val entity = LogEntity(
                        timestamp = System.currentTimeMillis(),
                        log_type = "http",
                        url = request.url.toString(),
                        method = request.method,
                        request_headers = formatHeaders(request.headers),
                        request_body = if (request.body != null) getRequestBody(request) else null,
                        response_code = response.code,
                        response_headers = formatHeaders(response.headers),
                        response_body = responseBody.takeIf { it.isNotEmpty() }
                    )
                    GrindrPlus.database.logDao().insert(entity)
                }
            } catch (e: Exception) {
                Logger.e("Failed to log HTTP request: ${e.message}")
            }
        }
    }
}