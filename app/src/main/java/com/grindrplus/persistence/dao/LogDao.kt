package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grindrplus.persistence.model.LogEntity

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LogEntity)

    @Query("SELECT * FROM logs WHERE log_type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByType(type: String, limit: Int = 100): List<LogEntity>

    @Query("DELETE FROM logs WHERE log_type = :type")
    suspend fun clearLogsByType(type: String)

    @Query("DELETE FROM logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM logs WHERE log_type = :type")
    suspend fun getCountByType(type: String): Int
}