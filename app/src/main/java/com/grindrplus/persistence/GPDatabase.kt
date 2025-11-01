package com.grindrplus.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grindrplus.persistence.converters.DateConverter
import com.grindrplus.persistence.dao.AlbumDao
import com.grindrplus.persistence.dao.SavedPhraseDao
import com.grindrplus.persistence.dao.TeleportLocationDao
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.persistence.model.SavedPhraseEntity
import com.grindrplus.persistence.model.TeleportLocationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.grindrplus.persistence.dao.ProfileViewDao // <-- NEW IMPORT
import com.grindrplus.persistence.model.ProfileViewEntity // <-- NEW IMPORT

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        ProfileViewEntity::class // <-- ADDED
    ],
    version = 1, // <-- INCREMENTED VERSION
    exportSchema = false
)

@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao

    abstract fun profileViewDao(): ProfileViewDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        fun create(context: Context): GPDatabase {
            return Room.databaseBuilder(context, GPDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(false)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }

        @Volatile
        private var INSTANCE: GPDatabase? = null

        fun getInstance(context: Context): GPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GPDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(false)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun prePopulate(context: Context) {
            val dao = getInstance(context).teleportLocationDao()
            prepopulateLocations(dao)
        }

        private class PrepopulateCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // This logic is now handled manually via the prePopulate() function
            }

        }

        private suspend fun prepopulateLocations(dao: TeleportLocationDao) {
            val customLocationsJson =
                com.grindrplus.core.Config.get("prepopulation_locations", "") as String
            val locationsToPrepopulate = mutableListOf<TeleportLocationEntity>()

            if (customLocationsJson.isNotEmpty()) {
                try {
                    val jsonArray = org.json.JSONArray(customLocationsJson)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObj = jsonArray.getJSONObject(i)
                        val name = jsonObj.optString("name")
                        val latStr = jsonObj.optString("lat")
                        val lonStr = jsonObj.optString("lon")
                        if (name.isNotBlank() && latStr.isNotBlank() && lonStr.isNotBlank()) {
                            val lat = latStr.toDoubleOrNull()
                            val lon = lonStr.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                locationsToPrepopulate.add(TeleportLocationEntity(name, lat, lon))
                            }
                        }
                    }
                } catch (e: org.json.JSONException) {
                    // Invalid JSON, fall back to default
                    locationsToPrepopulate.clear()
                }
            }

            if (locationsToPrepopulate.isEmpty()) {
                locationsToPrepopulate.addAll(
                    listOf(
                        TeleportLocationEntity("SaranJai", 13.7387, 100.5544),
                        TeleportLocationEntity("VT5D", 12.9030, 100.8671),
                        TeleportLocationEntity("ICONSIAM", 13.7270, 100.5101),
                        TeleportLocationEntity("ThongLo", 13.7243, 100.5784),
                        TeleportLocationEntity("Sydney", -33.8688, 151.2093)
                    )
                )
            }

            locationsToPrepopulate.forEach { dao.addLocation(it) }
        }
    }
}