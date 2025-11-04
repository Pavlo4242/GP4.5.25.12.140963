package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grindrplus.persistence.model.ProfilePhotoEntity

@Dao
interface ProfilePhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(photos: List<ProfilePhotoEntity>)

    @Query("SELECT profileId FROM ProfilePhotoEntity WHERE mediaHash = :mediaHash LIMIT 1")
    suspend fun getProfileIdByMediaHash(mediaHash: String): String?
}