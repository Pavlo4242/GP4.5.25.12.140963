package com.grindrplus.hooks

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hookConstructor
import java.util.*
import com.grindrplus.ui.Utils.copyToClipboard
import com.grindrplus.hooks.ProfileViewsTracker
import android.widget.Toast
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import java.text.SimpleDateFormat

class StatusDialog : Hook(
    "Status Dialog",
    "Check whether GrindrPlus is alive or not"
) {
    private val tabView = "com.google.android.material.tabs.TabLayout\$TabView"

    override fun init() {
        findClass(tabView).hookConstructor(HookStage.AFTER) { param ->
            val tabView = param.thisObject() as View

            tabView.post {
                val parent = tabView.parent as? ViewGroup
                val position = parent?.indexOfChild(tabView) ?: -1

                when (position) {
                    0 -> {
                        Logger.d("Tab 0 detected", LogSource.MODULE)
                        tabView.setOnLongClickListener { v ->
                            Logger.d("Tab 0 long-pressed", LogSource.MODULE)
                            showGrindrPlusDialog(v.context)
                            false
                        }
                    }

                    2 -> {
                        Logger.d("Tab 2 detected", LogSource.MODULE)
                        tabView.setOnLongClickListener { v ->
                            Logger.d("Tab 2 long-pressed", LogSource.MODULE)
                            showProfileViewsDialog(v.context)
                            true
                        }
                    }
                }
            }
        }
    }

    private fun showGrindrPlusDialog(context: Context) {
        GrindrPlus.currentActivity?.runOnUiThread {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

                val appVersionName = packageInfo.versionName
                val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                val packageName = context.packageName
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val moduleVersion = try {
                    BuildConfig.VERSION_NAME
                } catch (e: Exception) {
                    "Unknown"
                }

                val bridgeStatus = if (GrindrPlus.bridgeClient.isConnected()) {
                    "Connected"
                } else {
                    "Disconnected"
                }

                val currentLocationCoords =
                    (Config.get("forced_coordinates", Config.get("current_location", "")) as String)
                        .let { coords -> if (coords.isNotEmpty()) coords else "Not Spoofing (stock)" }


                val androidDeviceIdStatus = (Config.get("android_device_id", "") as String)
                    .let { id -> if (id.isNotEmpty()) "Spoofing ($id)" else "Not Spoofing (stock)" }

                val message = buildString {
                    appendLine("GrindrPlus is active and running")
                    appendLine()
                    appendLine("App Information:")
                    appendLine("• Version: $appVersionName ($appVersionCode)")
                    appendLine("• Package: $packageName")
                    appendLine("• Android ID: $androidDeviceIdStatus")
                    appendLine("• Current Location Coords: $currentLocationCoords (mine)")
                    appendLine()
                    appendLine("Module Information:")
                    appendLine("• GrindrPlus: $moduleVersion")
                    appendLine("• Bridge Status: $bridgeStatus")
                    appendLine()
                    appendLine("Device Information:")
                    appendLine("• Device: $deviceModel")
                    appendLine("• Android: $androidVersion")
                    appendLine()
                    appendLine("Long press this tab to show this dialog")
                    appendLine("Long press the Interests tab for profile views")
                }

                AlertDialog.Builder(context)
                    .setTitle("GrindrPlus")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .show()

            } catch (e: Exception) {
                AlertDialog.Builder(context)
                    .setTitle("GrindrPlus")
                    .setMessage("GrindrPlus is active and running\n\nError retrieving details: ${e.message}")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    private fun showProfileViewsDialog(context: Context) {
        GrindrPlus.executeAsync {
            ProfileViewsTracker.refreshProfileViews()
            GrindrPlus.runOnMainThread(context) {
                GrindrPlus.executeAsync {
                    val recentViews = ProfileViewsTracker.getRecentViewedProfiles(10)
                    val totalCount = ProfileViewsTracker.getViewedProfilesCount()

                    GrindrPlus.runOnMainThread {
                        if (recentViews.isEmpty()) {
                            AlertDialog.Builder(context)
                                .setTitle("Recent Profile Views")
                                .setMessage("No profile views tracked yet.\n\nProfile views will appear here as people view your profile.")
                                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .show()
                            return@runOnMainThread
                        }

                        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                        val message = buildString {
                            appendLine("Recent profile views (${recentViews.size} of $totalCount total):")
                            appendLine()
                            recentViews.forEachIndexed { index, view ->
                                val timeStr = dateFormat.format(Date(view.timestamp))
                                val nameStr = view.displayName?.let { " ($it)" } ?: ""
                                val distanceStr = view.distance?.let { " - ${it.toInt()}m" } ?: ""
                                appendLine("${index + 1}. ${view.profileId}$nameStr")
                                appendLine("   $timeStr$distanceStr")
                                if (index < recentViews.size - 1) appendLine()
                            }
                        }

                        val profileIdsList = recentViews.joinToString(", ") { it.profileId }

                        AlertDialog.Builder(context)
                            .setTitle("Recent Profile Views")
                            .setMessage(message)
                            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                            .setNeutralButton("Copy IDs") { _, _ ->
                                copyToClipboard("Profile IDs", profileIdsList)
                                GrindrPlus.showToast(
                                    android.widget.Toast.LENGTH_SHORT,
                                    "Copied ${recentViews.size} profile IDs"
                                )
                            }
                            .setNegativeButton("Copy All") { _, _ ->
                                copyToClipboard("Profile Views", message.toString())
                                GrindrPlus.showToast(
                                    android.widget.Toast.LENGTH_SHORT,
                                    "Copied full list"
                                )
                            }
                            .setIcon(android.R.drawable.ic_menu_view)
                            .show()
                            .apply {
                                setOnShowListener {
                                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.WHITE)
                                    getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(android.graphics.Color.WHITE)
                                    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
                                }
                            }
                    }
                }
            }
        }
    }
}