package com.grindrplus.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.grindrplus.GrindrPlus.context
import androidx.core.net.toUri


object PermissionManager {

    fun requestExternalStoragePermission(context: Context, delayMs: Long = 0L) {
        val isAlreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            Logger.d(message = "External storage permission already granted", source = LogSource.MODULE)
            Config.put(name = "external_permission_requested", value = false) // Reset since we have it
            return
        }

        val alreadyRequested = Config.get("external_permission_requested", false) as Boolean
        if (alreadyRequested) {
            Logger.d(message = "Permission already requested this session. Use /relog to force.", source = LogSource.MODULE)
            return
        }

        val requestBlock = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:${context.packageName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        Logger.i("Requested MANAGE_EXTERNAL_STORAGE permission", LogSource.MODULE)
                        Config.put("external_permission_requested", true)
                    } else {
                        Logger.e("No activity found to handle MANAGE_EXTERNAL_STORAGE intent", LogSource.MODULE)
                        requestLegacyStoragePermission(context)
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to request external storage permission: ${e.message}", LogSource.MODULE)
                    Logger.writeRaw(e.stackTraceToString())
                    requestLegacyStoragePermission(context)
                }
            } else {
                requestLegacyStoragePermission(context)
            }
        }

        if (delayMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed(requestBlock, delayMs)
        } else {
            requestBlock()
        }
    }

    fun checkStoragePermissionStatus(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                "GRANTED - MANAGE_EXTERNAL_STORAGE"
            } else {
                "DENIED - Needs MANAGE_EXTERNAL_STORAGE"
            }
        } else {
            val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

            when {
                readGranted && writeGranted -> "GRANTED - Legacy permissions"
                else -> "DENIED - Needs legacy storage permissions"
            }
        }
    }

    fun testStoragePermission(context: Context) {
        Logger.d("Storage permission status: ${checkStoragePermissionStatus(context)}", LogSource.MODULE)

        // Test if MediaStore is accessible
        try {
            val contentResolver = context.contentResolver
            val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(uri, null, null, null, null)
            val accessible = cursor != null
            cursor?.close()
            Logger.d("MediaStore accessibility: $accessible", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("MediaStore test failed: ${e.message}", LogSource.MODULE)
        }

        // Request permission
        requestExternalStoragePermission(context, delayMs = 1000)
    }

    private fun requestLegacyStoragePermission(context: Context) {
        if (context is Activity) {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            context.requestPermissions(permissions, 1001)
            Logger.i("Requested legacy storage permissions", LogSource.MODULE)
        } else {
            Logger.w("Cannot request legacy permissions without an Activity context.", LogSource.MODULE)
        }
    }
}