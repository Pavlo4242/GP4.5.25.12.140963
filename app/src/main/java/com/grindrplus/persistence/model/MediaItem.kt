package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey val imageHash: String,
    val mediaId: Long? = null,
    val url: String,
    val width: Int,
    val height: Int,
    val takenOnGrindr: Boolean,
    val createdAt: Long,
    val imageType: Int,
    val tapType: Int,
    val localPath: String? = null
)