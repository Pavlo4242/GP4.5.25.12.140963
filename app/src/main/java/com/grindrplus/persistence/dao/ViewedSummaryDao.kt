package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grindrplus.persistence.model.ViewedSummary

@Dao
interface ViewedSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: ViewedSummary)

    @Query("SELECT * FROM viewed_summary WHERE id = 1")
    suspend fun getSummary(): ViewedSummary?
}