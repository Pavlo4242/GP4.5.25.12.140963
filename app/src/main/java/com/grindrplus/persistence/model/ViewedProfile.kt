package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viewed_profiles")
data class ViewedProfile(
    @PrimaryKey val profileId: String,
    val viewCount: Int,
    val lastTimestamp: Long,
    val photoHashes: List<String>
)