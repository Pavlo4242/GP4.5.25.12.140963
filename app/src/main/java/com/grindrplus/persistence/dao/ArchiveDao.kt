package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.grindrplus.persistence.model.ArchivedAlbumEntity
import com.grindrplus.persistence.model.ArchivedChatEntity
import com.grindrplus.persistence.model.ArchivedPhotoEntity
import com.grindrplus.persistence.model.ArchivedProfileEntity
import com.grindrplus.persistence.model.ArchivedRelationshipEntity

@Dao
interface ArchiveDao {

    // Archived Profiles
    @Upsert
    suspend fun upsertArchivedProfile(profile: ArchivedProfileEntity)

    @Upsert
    suspend fun upsertArchivedProfiles(profiles: List<ArchivedProfileEntity>)

    @Query("SELECT * FROM archived_profiles WHERE profileId = :profileId")
    suspend fun getArchivedProfile(profileId: String): ArchivedProfileEntity?

    @Query("SELECT * FROM archived_profiles ORDER BY archiveTimestamp DESC")
    suspend fun getAllArchivedProfiles(): List<ArchivedProfileEntity>

    @Query("SELECT COUNT(*) FROM archived_profiles")
    suspend fun getArchivedProfilesCount(): Int

    // Archived Photos
    @Upsert
    suspend fun upsertArchivedPhoto(photo: ArchivedPhotoEntity)

    @Upsert
    suspend fun upsertArchivedPhotos(photos: List<ArchivedPhotoEntity>)

    @Query("SELECT * FROM archived_photos WHERE profileId = :profileId ORDER BY photoOrder ASC")
    suspend fun getArchivedPhotos(profileId: String): List<ArchivedPhotoEntity>

    // Archived Chats
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArchivedChat(chat: ArchivedChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArchivedChats(chats: List<ArchivedChatEntity>)

    @Query("SELECT * FROM archived_chats WHERE profileId = :profileId ORDER BY timestamp ASC")
    suspend fun getArchivedChats(profileId: String): List<ArchivedChatEntity>

    // Archived Relationships
    @Upsert
    suspend fun upsertArchivedRelationship(relationship: ArchivedRelationshipEntity)

    @Query("SELECT * FROM archived_relationships WHERE profileId = :profileId AND relationshipType = :relationshipType")
    suspend fun getArchivedRelationship(profileId: String, relationshipType: String): ArchivedRelationshipEntity?

    // Archived Albums
    @Upsert
    suspend fun upsertArchivedAlbum(album: ArchivedAlbumEntity)

    @Query("SELECT * FROM archived_albums WHERE profileId = :profileId")
    suspend fun getArchivedAlbums(profileId: String): List<ArchivedAlbumEntity>

    // Bulk operations
    @Transaction
    suspend fun archiveCompleteProfile(
        profile: ArchivedProfileEntity,
        photos: List<ArchivedPhotoEntity>,
        chats: List<ArchivedChatEntity>,
        relationships: List<ArchivedRelationshipEntity>,
        albums: List<ArchivedAlbumEntity>
    ) {
        upsertArchivedProfile(profile)
        if (photos.isNotEmpty()) upsertArchivedPhotos(photos)
        if (chats.isNotEmpty()) insertArchivedChats(chats)
        relationships.forEach { upsertArchivedRelationship(it) }
        albums.forEach { upsertArchivedAlbum(it) }
    }

    // Cleanup operations
    @Query("DELETE FROM archived_profiles WHERE profileId = :profileId")
    suspend fun deleteArchivedProfile(profileId: String)

    @Query("DELETE FROM archived_photos WHERE profileId = :profileId")
    suspend fun deleteArchivedPhotos(profileId: String)

    @Query("DELETE FROM archived_chats WHERE profileId = :profileId")
    suspend fun deleteArchivedChats(profileId: String)
}