package com.grindrplus.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grindrplus.persistence.converters.DateConverter
import com.grindrplus.persistence.converters.ListConverter
import com.grindrplus.persistence.dao.AlbumDao
import com.grindrplus.persistence.dao.ChatBackupDao
import com.grindrplus.persistence.dao.HttpBodyLogDao
import com.grindrplus.persistence.dao.SavedPhraseDao
import com.grindrplus.persistence.dao.TeleportLocationDao
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.persistence.model.ArchivedChatMessageEntity
import com.grindrplus.persistence.model.ArchivedConversationEntity
import com.grindrplus.persistence.model.ChatBackup
import com.grindrplus.persistence.model.ConversationBackup
import com.grindrplus.persistence.model.HttpBodyLogEntity
import com.grindrplus.persistence.model.MediaItem
import com.grindrplus.persistence.model.ParticipantBackup
import com.grindrplus.persistence.model.SavedPhraseEntity
import com.grindrplus.persistence.model.TeleportLocationEntity
import com.grindrplus.persistence.model.ViewedProfile
import com.grindrplus.persistence.model.ViewedSummary

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        ViewedSummary::class,
        ViewedProfile::class,
        MediaItem::class,
        ArchivedConversationEntity::class,
        ArchivedChatMessageEntity::class,
        ChatBackup::class,
        ConversationBackup::class,
        ParticipantBackup::class,
        HttpBodyLogEntity::class
    ],
    version = 2, // Increment to version 10
    exportSchema = false
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun chatBackupDao(): ChatBackupDao
    abstract fun httpBodyLogDao(): HttpBodyLogDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        @Volatile
        private var INSTANCE: GPDatabase? = null

        fun create(context: Context): GPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    GPDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2) // Only include the latest migration
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }


        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `http_body_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `url` TEXT NOT NULL,
                        `method` TEXT NOT NULL,
                        `response_body` TEXT
                    )
                """
                )
            }
        }
    }
}
