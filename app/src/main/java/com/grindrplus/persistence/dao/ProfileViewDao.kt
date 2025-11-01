package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.grindrplus.persistence.model.ProfileViewEntity

@Dao
interface ProfileViewDao {
    @Upsert
    suspend fun upsert(view: ProfileViewEntity)

    @Query("SELECT * FROM profile_views ORDER BY viewedAt DESC LIMIT :count")
    suspend fun getMostRecentViews(count: Int = 100): List<ProfileViewEntity>

    @Query("DELETE FROM profile_views WHERE viewedAt < :timestampLimit")
    suspend fun deleteOlderThan(timestampLimit: Long)

    @Query("SELECT * FROM profile_views WHERE profileId = :profileId")
    suspend fun getView(profileId: String): ProfileViewEntity?

    @Query("DELETE FROM profile_views")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM profile_views")
    suspend fun getCount(): Int

    // Add this missing method that ProfileViewsTracker is trying to use
    @Query("SELECT * FROM profile_views WHERE profileId LIKE 'unresolved_%'")
    suspend fun getUnresolvedViews(): List<ProfileViewEntity>

    // Add this missing delete method
    @Delete
    suspend fun delete(view: ProfileViewEntity)
}