package com.grindrplus.hooks

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.grindrplus.GrindrPlus
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook

class ViewHierarchyDumper : Hook("ViewHierarchyDumper", "Dumps the UI view hierarchy to logcat.") {

    private var hasDumped = false

    override fun init() {
        // Dynamically find the main launcher activity class name
        val launcherActivityClassName = getLauncherActivityClassName()
        if (launcherActivityClassName == null) {
            Log.e("ViewHierarchyDumper", "Could not find the launcher activity for Grindr. Aborting dump.")
            return
        }

        Log.d("ViewHierarchyDumper", "Found launcher activity: $launcherActivityClassName. Hooking onResume...")

        findClass(launcherActivityClassName).hook("onResume", HookStage.AFTER) { param ->
            if (hasDumped) return@hook

            val activity = param.thisObject() as Activity
            // Post to the view's message queue to ensure the layout is complete
            activity.window.decorView.post {
                Log.d("ViewHierarchyDumper", "================ VIEW HIERARCHY DUMP START ================")
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                dumpView(rootView, 0)
                Log.d("ViewHierarchyDumper", "================  VIEW HIERARCHY DUMP END  ================")
                hasDumped = true // Ensure this only runs once
            }
        }
    }

    private fun getLauncherActivityClassName(): String? {
        val pm = GrindrPlus.context.packageManager
        val intent = pm.getLaunchIntentForPackage(GrindrPlus.context.packageName)
        val resolveInfo = pm.queryIntentActivities(intent ?: Intent(), PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo.firstOrNull()?.activityInfo?.name
    }

    private fun dumpView(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        var viewId = "NO_ID"
        try {
            if (view.id != View.NO_ID) {
                viewId = view.context.resources.getResourceEntryName(view.id)
            }
        } catch (e: Exception) {
            // Failsafe
        }

        val logMessage = "$indent- Class: ${view.javaClass.simpleName}, ID: $viewId"
        Log.d("ViewHierarchyDumper", logMessage)

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                dumpView(view.getChildAt(i), depth + 1)
            }
        }
    }
}