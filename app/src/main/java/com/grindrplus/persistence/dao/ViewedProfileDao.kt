package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.grindrplus.persistence.model.ViewedProfile

@Dao
interface ViewedProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(profile: ViewedProfile)

    @Update
    suspend fun updateProfile(profile: ViewedProfile)

    @Query("SELECT * FROM viewed_profiles WHERE profileId = :profileId")
    suspend fun getProfileById(profileId: String): ViewedProfile?

    @Query("SELECT profileId FROM viewed_profiles ORDER BY lastTimestamp DESC LIMIT 1")
    suspend fun getMostRecentProfileId(): String?

    @Query("SELECT SUM(viewCount) FROM viewed_profiles")
    suspend fun getTotalViewedCount(): Int

    @Transaction
    suspend fun handleNewProfileView(profileId: String, photoHash: String, timestamp: Long) {
        val existingProfile = getProfileById(profileId)
        if (existingProfile == null) {
            val newProfile = ViewedProfile(profileId, 1, timestamp, listOf(photoHash))
            insertProfile(newProfile)
        } else {
            val updatedHashes = if (photoHash !in existingProfile.photoHashes) {
                existingProfile.photoHashes + photoHash
            } else {
                existingProfile.photoHashes
            }
            val updatedTimestamp = maxOf(existingProfile.lastTimestamp, timestamp)
            val updatedProfile = existingProfile.copy(
                viewCount = existingProfile.viewCount + 1,
                lastTimestamp = updatedTimestamp,
                photoHashes = updatedHashes
            )
            updateProfile(updatedProfile)
        }
    }
}