package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_backup")
data class ConversationBackup(
    @PrimaryKey
    val conversation_id: String,
    val name: String = "",
    val last_message_timestamp: Long,
    val unread: Int = 0
)

