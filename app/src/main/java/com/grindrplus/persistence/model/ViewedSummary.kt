package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viewed_summary")
data class ViewedSummary(
    @PrimaryKey val id: Int = 1,
    val viewedCount: Int = 0,
    val mostRecentProfileId: String? = null
)