package com.grindrplus.commands

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.Logger
import com.grindrplus.utils.MediaUtils
import java.io.File

class MediaManager(
    recipient: String,
    sender: String
) : CommandModule("MediaManager", recipient, sender) {

    private fun getScammerDb(): SQLiteDatabase {
        val dbDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, "ScammerDB.db")
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    @Command("log_media", aliases = ["logm"], help = "Cross-references saved media and logs it to the ScammerDB.")
    fun logSavedMedia(args: List<String>) {
        try {
            val savedImageIds = MediaUtils.getAllSavedMediaIds(MediaUtils.MediaType.IMAGE)
            val savedVideoIds = MediaUtils.getAllSavedMediaIds(MediaUtils.MediaType.VIDEO)
            val allMediaIds = (savedImageIds + savedVideoIds).distinct()

            if (allMediaIds.isEmpty()) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "No saved media found to log.")
                return
            }

            val db = getScammerDb()
            var successfulLogs = 0

            allMediaIds.forEach { mediaId ->
                try {
                    val profileResult = DatabaseHelper.query(
                        "SELECT profile_id FROM profile_photo WHERE photo_hash = ? OR photo_url LIKE ?",
                        arrayOf(mediaId.toString(), "%$mediaId%")
                    )
                    val profileId = profileResult.firstOrNull()?.get("profile_id")?.toString()

                    if (profileId != null) {
                        val profileInfo = DatabaseHelper.query(
                            "SELECT display_name FROM profile WHERE profile_id = ?",
                            arrayOf(profileId)
                        ).firstOrNull()
                        val displayName = profileInfo?.get("display_name")?.toString() ?: "Unknown Name"
                        val mediaType = if (savedImageIds.contains(mediaId)) "IMAGE" else "VIDEO"

                        val values = ContentValues().apply {
                            put("media_id", mediaId.toString())
                            put("profile_id", profileId)
                            put("display_name", displayName)
                            put("media_type", mediaType)
                            put("date_added", System.currentTimeMillis() / 1000)
                        }

                        db.insertWithOnConflict("scammer_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                        successfulLogs++
                    }
                } catch (e: Exception) {
                    Logger.e("Error logging media ID $mediaId: ${e.message}")
                }
            }

            db.close()
            GrindrPlus.showToast(Toast.LENGTH_LONG, "✅ Logged data for $successfulLogs media files to ScammerDB.")

        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "❌ Error logging media: ${e.message}")
            Logger.e(e.toString())
        }
    }
}