package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "http_body_logs")
data class HttpBodyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val url: String,
    val method: String,
    val response_body: String?
)