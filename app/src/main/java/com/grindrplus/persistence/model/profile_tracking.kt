package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks profile snapshots over time
 */
@Entity(
    tableName = "profile_snapshots",
    indices = [Index("profileId"), Index("timestamp")]
)
data class ProfileSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val displayName: String?,
    val aboutMe: String?,
    val headline: String?,
    val age: Int?,
    val height: String?,
    val weight: String?,
    val bodyType: String?,
    val ethnicity: String?,
    val tribes: String?, // JSON array as string
    val lookingFor: String?, // JSON array as string
    val timestamp: Long = System.currentTimeMillis(),
    val accountPackage: String // Which account saw this
)

/**
 * Tracks photo changes for profiles
 */
@Entity(
    tableName = "profile_photos",
    indices = [Index("profileId"), Index("photoHash"), Index("timestamp")]
)
data class ProfilePhotoHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val photoHash: String,
    val photoUrl: String?,
    val localPath: String?, // Permanent storage path
    val isPrimary: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val accountPackage: String
)

/**
 * Tracks all conversations across accounts
 */
@Entity(
    tableName = "conversation_metadata",
    indices = [Index("conversationId"), Index("profileId"), Index("accountPackage")]
)
data class ConversationMetadata(
    @PrimaryKey val conversationId: String,
    val profileId: String,
    val displayName: String?,
    val lastMessageTimestamp: Long?,
    val firstMessageTimestamp: Long?,
    val messageCount: Int = 0,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val accountPackage: String
)

/**
 * Cross-reference table for profiles seen by multiple accounts
 */
@Entity(
    tableName = "profile_account_mapping",
    indices = [Index("profileId"), Index("accountPackage")]
)
data class ProfileAccountMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val accountPackage: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val interactionCount: Int = 0
)

/**
 * Permanent media storage tracking
 */
@Entity(
    tableName = "permanent_media",
    indices = [Index("mediaHash"), Index("profileId"), Index("mediaType")]
)
data class PermanentMedia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaHash: String, // MD5/SHA of content
    val originalUrl: String?,
    val localPath: String, // Permanent storage location
    val mediaType: String, // "photo", "album", "video"
    val profileId: String?,
    val albumId: Long?,
    val width: Int?,
    val height: Int?,
    val fileSize: Long,
    val savedTimestamp: Long = System.currentTimeMillis(),
    val accountPackage: String
)
