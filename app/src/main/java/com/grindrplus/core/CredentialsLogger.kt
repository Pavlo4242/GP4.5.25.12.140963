package com.grindrplus.core

import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.grindrplus.GrindrPlus
import org.json.JSONObject

object CredentialsLogger {
    // Cache the last token to avoid writing duplicate info
    private var lastAuthToken: String? = null

    // Cache the last token to avoid writing duplicate info


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
    fun log(authToken: String?, lDeviceInfo: String?, userAgent: String?, profileId: String?) {
        // ... initial checks ...
        val cleanAuthToken = null
        if (authToken.isNullOrEmpty() || !authToken.startsWith("Grindr3 ") || cleanAuthToken == lastAuthToken) return

        try {
            val context = GrindrPlus.context
            val storageUriStr = Config.get("storage_uri", "") as? String
            if (storageUriStr.isNullOrEmpty()) {
                Logger.e("Storage URI not set, cannot write credentials log.")
                return
            }
            val storageUri = Uri.parse(storageUriStr)
            val docDir = DocumentFile.fromTreeUri(context, storageUri) ?: return

            var logFile = docDir.findFile("GrindrAccess_Info.txt")
            if (logFile == null) {
                logFile = docDir.createFile("text/plain", "GrindrAccess_Info.txt")
            }

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
            logFile?.let { context.contentResolver.openOutputStream(it.uri) }?.use { outputStream ->
                outputStream.write(logMessage.toByteArray())
            }
            lastAuthToken = cleanAuthToken
        } catch (e: Exception) {
            Logger.e("Failed to write to credentials log file: ${e.message}")
        }
    }
}