package com.grindrplus.core

import com.grindrplus.GrindrPlus
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.persistence.model.LogEntity

object WssAuthLogger {
    fun log(profileId: String, xmppToken: String) {
        if (!(Config.get("enable_wss_logging", false) as Boolean)) return
        GPDatabase.DatabaseManager.executeWhenReady {
            try {
                GrindrPlus.executeAsync {
                    val entity = LogEntity(
                        timestamp = System.currentTimeMillis(),
                        log_type = "wss_auth",
                        profile_id = profileId,
                        xmpp_token = xmppToken
                    )
                    GrindrPlus.database.logDao().insert(entity)
                }
            } catch (e: Exception) {
                Logger.e("Failed to log WSS auth: ${e.message}")
            }
        }
    }
}