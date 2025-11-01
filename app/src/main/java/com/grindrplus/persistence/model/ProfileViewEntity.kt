package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_views")
data class ProfileViewEntity(
    @PrimaryKey
    val profileId: String,
    val viewedAt: Long,
    val displayName: String? = null,
    val distance: Double? = null,
    val mediaHash: String? = null,
    val source: String
)