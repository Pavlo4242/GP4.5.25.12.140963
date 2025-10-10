package com.grindrplus.commands

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Environment
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.grindrplus.GrindrPlus
import com.grindrplus.ui.Utils.copyToClipboard
import java.io.File

class Scammer(
    recipient: String,
    sender: String
) : CommandModule("Scammer", recipient, sender) {

    private fun getScammerDb(): SQLiteDatabase {
        val dbDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, "ScammerDB.db")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    private fun initializeDatabase() {
        val db = getScammerDb()
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scammers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                grindr_id TEXT NOT NULL UNIQUE,
                name TEXT,
                location TEXT,
                notes TEXT,
                date_added INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scammer_media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                media_id TEXT NOT NULL UNIQUE,
                profile_id TEXT,
                display_name TEXT,
                media_type TEXT,
                date_added INTEGER
            )
        """)
        db.close()
    }

    @Command("add_scammer", aliases = ["scammer"], help = "Add a scammer to the database. Usage: /add_scammer [id] [name] | [notes]")
    fun addScammer(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: /add_scammer [id] [name] | [notes]")
            return
        }

        try {
            initializeDatabase()
            val db = getScammerDb()
            val grindrId = args.getOrNull(0) ?: sender

            val parts = args.drop(1).joinToString(" ").split("|")
            val name = parts.getOrNull(0)?.trim()
            val notes = parts.getOrNull(1)?.trim()

            val values = ContentValues().apply {
                put("grindr_id", grindrId)
                put("name", name)
                put("location", "Thailand") // Default location as requested
                put("notes", notes)
                put("date_added", System.currentTimeMillis() / 1000)
            }

            db.insertWithOnConflict("scammers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.close()

            GrindrPlus.showToast(Toast.LENGTH_SHORT, "✅ Scammer logged: $grindrId")

        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "❌ Error logging scammer: ${e.message}")
        }
    }

    @Command("list_scammers", aliases = ["scammers"], help = "List all logged scammers.")
    fun listScammers(args: List<String>) {
        try {
            val db = getScammerDb()
            val cursor = db.rawQuery("SELECT grindr_id, name, notes FROM scammers ORDER BY date_added DESC", null)
            val scammerList = mutableListOf<String>()

            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("grindr_id"))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                    scammerList.add("ID: $id\nName: $name\nNotes: $notes")
                } while (cursor.moveToNext())
            }
            cursor.close()
            db.close()

            val message = if (scammerList.isEmpty()) "No scammers logged yet."
            else scammerList.joinToString("\n\n")

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val textView = AppCompatTextView(activity).apply {
                    text = message
                    textSize = 14f
                    setTextColor(Color.WHITE)
                }
                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Logged Scammers")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("Copy All") { _, _ -> copyToClipboard("Scammers", message) }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "❌ Error listing scammers: ${e.message}")
        }
    }
}