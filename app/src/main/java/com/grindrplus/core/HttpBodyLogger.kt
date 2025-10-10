package com.grindrplus.core

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import android.content.Context
import com.grindrplus.GrindrPlus
import com.grindrplus.bridge.BridgeClient





object HttpBodyLogger {
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
        val db = getDatabase()
        try {
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis() / 1000)
                put("url", url)
                put("method", method)
                put("response_body", body)
            }
            db.insert("logs", null, values)
        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP body log database: ${e.message}")
        } finally {
            db.close()
        }
    }

    private suspend fun getDatabase(): SQLiteDatabase {
        val gp = grindrPlus ?: throw IllegalStateException("HttpBodyLogger not initialized with GrindrPlus")
        val dbFile = gp.bridgeClient.getHttpDbFile() ?: throw IOException("Failed to get DB file from BridgeService")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    suspend fun initializeDatabase() {
        val db = getDatabase()
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    method TEXT NOT NULL,
                    response_body TEXT
                )
            """)
        } finally {
            db.close()
        }
    }
}