package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val log_type: String, // "http", "credentials", "wss_auth"
    val url: String? = null,
    val method: String? = null,
    val request_headers: String? = null,
    val request_body: String? = null,
    val response_code: Int? = null,
    val response_headers: String? = null,
    val response_body: String? = null,
    val profile_id: String? = null,
    val auth_token: String? = null,
    val xmpp_token: String? = null,
    val device_info: String? = null,
    val user_agent: String? = null
)