package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "archived_conversations", indices = [Index("conversationId")])
data class ArchivedConversationEntity(
    @PrimaryKey val conversationId: String,
    val name: String?,
    val lastMessageTimestamp: Long?
)

@Entity(tableName = "archived_chat_messages", indices = [Index("conversationId")])
data class ArchivedChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String?,
    val timestamp: Long?,
    val body: String? // Storing the raw JSON body
)

@Entity(tableName = "chat_backup")
data class ChatBackup(
    @PrimaryKey
    val message_id: String,
    val conversation_id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val type: String
)