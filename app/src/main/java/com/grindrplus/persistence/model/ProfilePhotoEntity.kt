package com.grindrplus.persistence.model

import androidx.room.Entity

@Entity(primaryKeys = ["profileId", "mediaHash"])
data class ProfilePhotoEntity(
    val profileId: String,
    val mediaHash: String
)