package com.grindrplus.core

import android.os.Environment
import android.util.Base64
import com.grindrplus.GrindrPlus
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.persistence.model.LogEntity
import org.json.JSONObject
import java.io.File

object CredentialsLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrAccess_Info.txt"
    )
    private var lastAuthToken: String? = null

    private fun getProfileIdFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
            JSONObject(payload).getString("profileId")
        } catch (e: Exception) {
            Logger.e("Could not extract profileId from token: ${e.message}")
            null
        }
    }

    fun log(authToken: String?, lDeviceInfo: String?, userAgent: String?) {
        if (authToken.isNullOrEmpty() || !authToken.startsWith("Grindr3 ")) return

        val cleanAuthToken = authToken.substringAfter("Grindr3 ")
        if (cleanAuthToken == lastAuthToken) return
            try {
                val profileId = getProfileIdFromToken(cleanAuthToken)

                val logMessage = buildString {
                    append("### Latest Grindr Credentials ###\n\n")
                    append("# This file is automatically updated when your session token changes.\n")
                    append("# Use these values in your grindr-access scripts.\n\n")
                    append("profileId: $profileId\n\n")
                    append("authToken: $cleanAuthToken\n\n")
                    append("l-device-info: $lDeviceInfo\n\n")
                    append("user-agent: $userAgent\n")
                }

                logFile.writeText(logMessage)
                lastAuthToken = cleanAuthToken

                // Also save to database
                GrindrPlus.executeAsync {
                    val entity = LogEntity(
                        timestamp = System.currentTimeMillis(),
                        log_type = "credentials",
                        profile_id = profileId,
                        auth_token = cleanAuthToken,
                        device_info = lDeviceInfo,
                        user_agent = userAgent
                    )

                }
            } catch (e: Exception) {
                Logger.e("Failed to write credentials: ${e.message}")
            }
        }
    }
