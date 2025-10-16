package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.grindrplus.persistence.model.HttpBodyLogEntity

@Dao
interface HttpBodyLogDao {
    @Insert
    suspend fun insert(log: HttpBodyLogEntity)

    @Query("SELECT * FROM http_body_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<HttpBodyLogEntity>

    @Query("DELETE FROM http_body_logs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM http_body_logs")
    suspend fun getCount(): Long

    @Query("DELETE FROM http_body_logs WHERE id IN (SELECT id FROM http_body_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}