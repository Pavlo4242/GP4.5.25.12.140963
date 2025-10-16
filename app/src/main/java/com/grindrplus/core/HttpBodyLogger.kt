package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import java.io.File

object HttpBodyLogger {
    @Volatile
    private var databaseHelper: HttpBodyDatabaseHelper? = null
    private val LOCK = Any()
        /**
     * Manually initializes the database helper. This must be called before any logging can occur.
     * It is thread-safe and can be called multiple times without issue.
     */
    fun initialize(context: Context) {
        if (databaseHelper == null) {
            synchronized(LOCK) {
                if (databaseHelper == null) {
                    databaseHelper = HttpBodyDatabaseHelper(context.applicationContext)
                    Logger.i("HttpBodyLogger initialized.")
                }
            }
        }
    }

    fun logHttpBody(url: String, method: String, requestBody: String?, responseBody: String?) {
        // Get the helper instance. If it's null (not initialized), exit the function immediately.
        val dbHelper = databaseHelper ?: return

        if (requestBody.isNullOrBlankOrEmptyJson() && responseBody.isNullOrBlankOrEmptyJson()) {
            return
        }

        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis())
                put("url", url)
                put("method", method)
                if (!requestBody.isNullOrBlankOrEmptyJson()) {
                    put("request_body", requestBody)
                }
                if (!responseBody.isNullOrBlankOrEmptyJson()) {
                    put("response_body", responseBody)
                }
            }
            db.insert("http_body_logs", null, values)
        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP body log database: ${e.message}")
        }
    }

    fun getDatabasePath(): String {
        return databaseHelper?.readableDatabase?.path ?: "HttpBodyLogger not initialized"
    }

    private fun String?.isNullOrBlankOrEmptyJson(): Boolean {
        return this.isNullOrBlank() || this == "{}" || this == "[]"
    }

    private class HttpBodyDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        File(context.cacheDir, "HttpBodyLogs.db").absolutePath,
        null,
        1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS http_body_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    method TEXT NOT NULL,
                    request_body TEXT,
                    response_body TEXT
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS http_body_logs")
            onCreate(db)
        }
    }
}