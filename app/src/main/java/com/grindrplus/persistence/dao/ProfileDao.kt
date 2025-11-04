package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.grindrplus.persistence.model.ProfileEntity

@Dao
interface ProfileDao {
    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE profileId = :profileId")
    suspend fun getProfileById(profileId: String): ProfileEntity?
}