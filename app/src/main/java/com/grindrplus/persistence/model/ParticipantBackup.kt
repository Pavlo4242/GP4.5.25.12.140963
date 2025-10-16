package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participant_backup")
data class ParticipantBackup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversation_id: String,
    val profile_id: String,
    val last_online: Long
)