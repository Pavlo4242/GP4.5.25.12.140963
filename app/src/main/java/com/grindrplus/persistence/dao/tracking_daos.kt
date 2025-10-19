package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.grindrplus.persistence.model.*

@Dao
interface ProfileTrackingDao {
    /**
     * Insert a profile snapshot
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ProfileSnapshot): Long

    /**
     * Get all snapshots for a profile
     */
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getProfileHistory(profileId: String): List<ProfileSnapshot>

    /**
     * Get latest snapshot for a profile
     */
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(profileId: String): ProfileSnapshot?

    /**
     * Detect profile changes between snapshots
     */
    @Query("""
        SELECT * FROM profile_snapshots 
        WHERE profileId = :profileId 
        AND timestamp > :sinceTimestamp 
        ORDER BY timestamp ASC
    """)
    suspend fun getChanges(profileId: String, sinceTimestamp: Long): List<ProfileSnapshot>

    /**
     * Insert photo history entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotoHistory(photo: ProfilePhotoHistory): Long

    /**
     * Get photo history for a profile
     */
    @Query("SELECT * FROM profile_photos WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getPhotoHistory(profileId: String): List<ProfilePhotoHistory>

    /**
     * Find profiles that have used a specific photo
     */
    @Query("SELECT DISTINCT profileId FROM profile_photos WHERE photoHash = :photoHash")
    suspend fun findProfilesWithPhoto(photoHash: String): List<String>
}

@Dao
interface ConversationTrackingDao {
    /**
     * Upsert conversation metadata
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationMetadata)

    /**
     * Get all conversations for an account
     */
    @Query("SELECT * FROM conversation_metadata WHERE accountPackage = :accountPackage ORDER BY lastMessageTimestamp DESC")
    suspend fun getConversations(accountPackage: String): List<ConversationMetadata>

    /**
     * Get conversation by ID
     */
    @Query("SELECT * FROM conversation_metadata WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationMetadata?

    /**
     * Update message count
     */
    @Query("UPDATE conversation_metadata SET messageCount = messageCount + 1, lastMessageTimestamp = :timestamp WHERE conversationId = :conversationId")
    suspend fun incrementMessageCount(conversationId: String, timestamp: Long)

    /**
     * Get all conversations with a specific profile across all accounts
     */
    @Query("SELECT * FROM conversation_metadata WHERE profileId = :profileId")
    suspend fun getConversationsForProfile(profileId: String): List<ConversationMetadata>
}

@Dao
interface ProfileAccountMappingDao {
    /**
     * Record that an account has seen a profile
     */
    @Transaction
    suspend fun recordProfileSighting(profileId: String, accountPackage: String) {
        val existing = getMapping(profileId, accountPackage)
        if (existing != null) {
            updateLastSeen(profileId, accountPackage, System.currentTimeMillis())
            incrementInteractionCount(profileId, accountPackage)
        } else {
            insertMapping(ProfileAccountMapping(
                profileId = profileId,
                accountPackage = accountPackage,
                interactionCount = 1
            ))
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMapping(mapping: ProfileAccountMapping)

    @Query("SELECT * FROM profile_account_mapping WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun getMapping(profileId: String, accountPackage: String): ProfileAccountMapping?

    @Query("UPDATE profile_account_mapping SET lastSeen = :timestamp WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun updateLastSeen(profileId: String, accountPackage: String, timestamp: Long)

    @Query("UPDATE profile_account_mapping SET interactionCount = interactionCount + 1 WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun incrementInteractionCount(profileId: String, accountPackage: String)

    /**
     * Get all accounts that have seen a profile
     */
    @Query("SELECT * FROM profile_account_mapping WHERE profileId = :profileId")
    suspend fun getAccountsForProfile(profileId: String): List<ProfileAccountMapping>

    /**
     * Get all profiles seen by an account
     */
    @Query("SELECT * FROM profile_account_mapping WHERE accountPackage = :accountPackage ORDER BY lastSeen DESC")
    suspend fun getProfilesForAccount(accountPackage: String): List<ProfileAccountMapping>
}

@Dao
interface PermanentMediaDao {
    /**
     * Save media permanently
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: PermanentMedia): Long

    /**
     * Check if media already exists
     */
    @Query("SELECT * FROM permanent_media WHERE mediaHash = :hash")
    suspend fun findByHash(hash: String): PermanentMedia?

    /**
     * Get all media for a profile
     */
    @Query("SELECT * FROM permanent_media WHERE profileId = :profileId ORDER BY savedTimestamp DESC")
    suspend fun getProfileMedia(profileId: String): List<PermanentMedia>

    /**
     * Get all media for an album
     */
    @Query("SELECT * FROM permanent_media WHERE albumId = :albumId ORDER BY savedTimestamp DESC")
    suspend fun getAlbumMedia(albumId: Long): List<PermanentMedia>

    /**
     * Get media by type
     */
    @Query("SELECT * FROM permanent_media WHERE mediaType = :type ORDER BY savedTimestamp DESC")
    suspend fun getMediaByType(type: String): List<PermanentMedia>

    /**
     * Get storage statistics
     */
    @Query("SELECT SUM(fileSize) FROM permanent_media")
    suspend fun getTotalStorageUsed(): Long?

    @Query("SELECT COUNT(*) FROM permanent_media")
    suspend fun getTotalMediaCount(): Int
}
