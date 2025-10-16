package com.grindrplus.core

import android.os.Environment
import android.util.Base64
import org.json.JSONObject
import java.io.File

object CredentialsLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrAccess_Info.txt"
    )

    // Cache the last token to avoid writing duplicate info
    private var lastAuthToken: String? = null

    /**
     * Extracts the profileId from a JWT token.
     */
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

    /**
     * Logs the essential credentials to a separate file.
     * Only writes to the file if the auth token has changed.
     */
    fun log(authToken: String?, lDeviceInfo: String?, userAgent: String?) {
        // We only care about requests that are authenticated
        if (authToken.isNullOrEmpty() || !authToken.startsWith("Grindr3 ")) return

        val cleanAuthToken = authToken.substringAfter("Grindr3 ")

        // If the token is the same as the last one we logged, do nothing.
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

            // Overwrite the file with the latest credentials
            logFile.writeText(logMessage)

            // Update the cache
            lastAuthToken = cleanAuthToken

        } catch (e: Exception) {
            Logger.e("Failed to write to credentials log file: ${e.message}")
        }
    }
}
