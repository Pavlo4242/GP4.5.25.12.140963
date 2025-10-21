package com.grindrplus.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
import androidx.room.TypeConverters
import com.grindrplus.core.Logger
import com.grindrplus.persistence.converters.DateConverter
import com.grindrplus.persistence.dao.AlbumDao
import com.grindrplus.persistence.dao.ArchiveDao
import com.grindrplus.persistence.dao.SavedPhraseDao
import com.grindrplus.persistence.dao.TeleportLocationDao
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.persistence.model.ArchivedAlbumEntity
import com.grindrplus.persistence.model.ArchivedChatEntity
import com.grindrplus.persistence.model.ArchivedPhotoEntity
import com.grindrplus.persistence.model.ArchivedProfileEntity
import com.grindrplus.persistence.model.ArchivedRelationshipEntity
import com.grindrplus.persistence.model.SavedPhraseEntity
import com.grindrplus.persistence.model.TeleportLocationEntity
import java.io.IOException

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        ArchivedProfileEntity::class,
        ArchivedPhotoEntity::class,
        ArchivedChatEntity::class,
        ArchivedRelationshipEntity::class,
        ArchivedAlbumEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun archiveDao(): ArchiveDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        fun create(context: Context): GPDatabase {
            // Ensure the database file has the correct permissions upon creation
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                try {
                    // Create an empty file first to set permissions
                    dbFile.parentFile?.mkdirs()
                    dbFile.createNewFile()
                    // Note: These permissions are generally not needed for the app's private directory
                    // but we are keeping your original logic.
                    dbFile.setReadable(true, false)
                    dbFile.setWritable(true, false)
                } catch (e: IOException) {
                    Logger.e("Failed to create or set permissions for database file: ${e.message}")
                }
            }

            return Room.databaseBuilder(context.applicationContext, GPDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration() // Use your desired migration strategy
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}