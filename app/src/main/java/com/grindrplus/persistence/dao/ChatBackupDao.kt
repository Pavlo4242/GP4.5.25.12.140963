package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.grindrplus.persistence.model.ArchivedChatMessageEntity
import com.grindrplus.persistence.model.ArchivedConversationEntity
import com.grindrplus.persistence.model.ChatBackup
import com.grindrplus.persistence.model.ConversationBackup
import com.grindrplus.persistence.model.ParticipantBackup


@Dao
interface ChatBackupDao {
    // Chat messages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chatBackup: ChatBackup)


    @Query("SELECT * FROM chat_backup WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversation(conversationId: String): List<ChatBackup>

    // Conversations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversationBackup: ConversationBackup)

    @Query("SELECT * FROM conversation_backup")
    suspend fun getAllConversations(): List<ConversationBackup>

    // Participants
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participantBackup: ParticipantBackup)

    @Query("SELECT * FROM participant_backup WHERE conversation_id = :conversationId")
    suspend fun getParticipantsByConversation(conversationId: String): List<ParticipantBackup>
}
/*

@Dao
interface ChatBackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chatBackup: ChatBackup)

    @Query("SELECT * FROM chat_backup WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversation(conversationId: String): List<ChatBackup>

    @Query("DELETE FROM chat_backup WHERE message_id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT COUNT(*) FROM chat_backup WHERE conversation_id = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int
}*/
