package com.grindrplus.manager.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.HttpBodyLogger
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger

object PermissionManager {

    fun requestExternalStoragePermission(context: Context, delayMs: Long = 0L) {
        val alreadyRequested = Config.get("external_permission_requested", false) as Boolean
        if (alreadyRequested) {
            Logger.d("External storage permission already requested", LogSource.MODULE)
            return
        }

        val requestBlock = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()) {

                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Logger.i("Requested MANAGE_EXTERNAL_STORAGE permission", LogSource.MODULE)
                    Config.put("external_permission_requested", true)
                } catch (e: Exception) {
                    Logger.e("Failed to request external storage permission: ${e.message}", LogSource.MODULE)
                    Logger.writeRaw(e.stackTraceToString())
                }
            } else {
                Logger.d("External storage permission already granted or not required", LogSource.MODULE)
            }
        }

        if (delayMs > 0) {
            Handler(Looper.getMainLooper()).postDelayed(requestBlock, delayMs)
        } else {
            requestBlock()
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
        }
    }

    fun resetPermissionFlags() {
        Config.put("external_permission_requested", false)
    }

    // Optional: Trigger on first launch (commented out)

    fun autoRequestOnFirstLaunch(context: Context) {
        val firstLaunch = Config.get("first_launch", true) as Boolean
        if (firstLaunch) {

            requestExternalStoragePermission(context, delayMs = 7000)
        }
    }


    // Optional: Trigger after install confirmation (commented out)
    /*
    fun requestAfterInstallConfirmation(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            requestExternalStoragePermission(context)
        }, 5000)
    }
    */
}