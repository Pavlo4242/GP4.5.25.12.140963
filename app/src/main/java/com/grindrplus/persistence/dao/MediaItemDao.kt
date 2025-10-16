package com.grindrplus.persistence.dao

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grindrplus.persistence.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Dao
interface MediaItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(mediaItem: MediaItem)

    @Query("SELECT * FROM media_items WHERE imageHash = :hash")
    suspend fun getMediaItemByHash(hash: String): MediaItem?

    suspend fun downloadAndSaveMedia(context: Context, mediaItem: MediaItem): MediaItem? {
        if (mediaItem.localPath != null && File(mediaItem.localPath).exists()) {
            return mediaItem
        }

        return try {
            val directory = File(context.filesDir, "media")
            directory.mkdirs()
            val file = File(directory, "${mediaItem.imageHash}.jpg")

            withContext(Dispatchers.IO) {
                URL(mediaItem.url).openStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val updatedMediaItem = mediaItem.copy(localPath = file.absolutePath)
            insertMediaItem(updatedMediaItem)
            updatedMediaItem
        } catch (e: Exception) {
            // Handle exceptions, e.g., logging
            null
        }
    }
}