package com.grindrplus.persistence.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archived_profiles",
    indices = [Index(value = ["profileId"], unique = true)]
)
data class ArchivedProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val archiveId: Long = 0,

    @ColumnInfo(name = "profileId")
    val profileId: String,
    val displayName: String?,
    val age: Int?,
    val showAge: Boolean?,
    val showDistance: Boolean?,
    val distance: Int?,
    val approximateDistance: String?,
    val aboutMe: String?,
    val profileTags: String?,
    val mediaHash: String?,
    val facebookId: String?,
    val instagramId: String?,
    val verifiedInstagramId: String?,
    val twitterId: String?,
    val hasAlbum: Boolean?,
    val lastOnline: Long?,
    val onlineUntil: Long?,
    val lastSeen: Long?,
    val profileCreatedDate: Long?,
    val archiveTimestamp: Long,
    val dataSource: String
)

@Entity(
    tableName = "archived_photos",
    foreignKeys = [
        ForeignKey(
            entity = ArchivedProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArchivedPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val photoId: Long = 0,

    val profileId: String,
    val mediaHash: String,
    val photoOrder: Int,
    val state: String?,
    val reason: String?,
    val createdAt: Long?,
    val archiveTimestamp: Long
)

@Entity(
    tableName = "archived_chats",
    foreignKeys = [
        ForeignKey(
            entity = ArchivedProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArchivedChatEntity(
    @PrimaryKey(autoGenerate = true)
    val chatId: Long = 0,

    val profileId: String,
    val conversationId: String?,
    val messageText: String?,
    val messageType: String?,
    val timestamp: Long,
    val isFromMe: Boolean,
    val senderProfileId: String?,
    val archiveTimestamp: Long
)

@Entity(
    tableName = "archived_relationships",
    foreignKeys = [
        ForeignKey(
            entity = ArchivedProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArchivedRelationshipEntity(
    @PrimaryKey(autoGenerate = true)
    val relationshipId: Long = 0,

    val profileId: String,
    val relationshipType: String,
    val timestamp: Long,
    val metadata: String?,
    val archiveTimestamp: Long
)

@Entity(
    tableName = "archived_albums",
    foreignKeys = [
        ForeignKey(
            entity = ArchivedProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArchivedAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val albumId: Long = 0,

    val profileId: String,
    val albumContent: String?,
    val sharedCount: Int?,
    val expiration: Long?,
    val archiveTimestamp: Long
)