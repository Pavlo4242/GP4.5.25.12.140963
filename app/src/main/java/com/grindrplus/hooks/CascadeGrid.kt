package com.grindrplus.hooks

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Utils.openProfile
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.ui.Utils
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

class CascadeGrid : Hook(
    "CascadeGrid",
    "Customize columns for the main cascade (Browse)"
) {
    private val browseFragment = "com.grindrapp.android.ui.browse.CascadeFragment"

    override fun init() {
        findClass(browseFragment).hook("onViewCreated", HookStage.AFTER) { param ->
            try {
                logd("CascadeGrid: onViewCreated hooked")
                val columns = Config.get("cascade_grid_columns", 4) as Int
                val view = param.arg<View>(0)

                // 1. Find RecyclerView as a generic View to avoid ClassCastException
                val recyclerViewId = Utils.getId("recycler_view", "id", GrindrPlus.context)
                val recyclerView = view.findViewById<View>(recyclerViewId)

                if (recyclerView == null) {
                    loge("CascadeGrid: RecyclerView not found with ID 'recycler_view'")
                    return@hook
                }
                logd("CascadeGrid: Found RecyclerView: ${recyclerView.javaClass.name}")

                // 2. Get LayoutManager via reflection
                val layoutManager = callMethod(recyclerView, "getLayoutManager")
                if (layoutManager == null) {
                    loge("CascadeGrid: LayoutManager is null")
                    return@hook
                }
                logd("CascadeGrid: LayoutManager type: ${layoutManager.javaClass.name}")

                // Check if it is a GridLayoutManager (by name check to avoid casting)
                if (layoutManager.javaClass.name.contains("GridLayoutManager")) {
                    val oldSpan = callMethod(layoutManager, "getSpanCount") as Int
                    logd("CascadeGrid: Current SpanCount: $oldSpan. Setting to: $columns")

                    callMethod(layoutManager, "setSpanCount", columns)

                    // Note: Custom SpanSizeLookup logic removed to prevent ClassCastException.
                    // Headers may appear 1-column wide, but the crash will be gone.
                } else {
                    loge("CascadeGrid: LayoutManager is not a GridLayoutManager, skipping span update.")
                }

                // 3. Fix Adapter (Thumbnails & Clicks)
                fixAdapter(recyclerView, columns)

            } catch (e: Exception) {
                loge("CascadeGrid Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun fixAdapter(recyclerView: Any, columns: Int) {
        try {
            val adapter = callMethod(recyclerView, "getAdapter")
            if (adapter == null) {
                loge("CascadeGrid: Adapter is null")
                return
            }
            logd("CascadeGrid: Hooking Adapter: ${adapter.javaClass.name}")

            adapter.javaClass.hook("onBindViewHolder", HookStage.AFTER) { param ->
                try {
                    // Args: holder, position. Using generic Any for safety.
                    val holder = param.arg<Any>(0)
                    val position = param.arg<Int>(1)
                    val itemView = getObjectField(holder, "itemView") as View

                    if (isProfileItem(itemView)) {
                        val displayMetrics = GrindrPlus.context.resources.displayMetrics
                        val targetSize = displayMetrics.widthPixels / columns

                        // A. Force Square Layout
                        val lp = itemView.layoutParams
                        if (lp != null) {
                            // We assume it has width/height fields (standard ViewGroup.LayoutParams)
                            lp.width = targetSize
                            lp.height = targetSize
                            itemView.layoutParams = lp
                        }

                        // B. Fix Distortion (CENTER_CROP)
                        val imageId = Utils.getId("profile_image", "id", GrindrPlus.context)
                        val imageView = itemView.findViewById<ImageView>(imageId)
                        if (imageView != null) {
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        }

                        // C. Unlimited Profiles Click Fix
                        try {
                            val item = callMethod(adapter, "getItem", position)
                            val profileId = extractProfileId(item)
                            if (profileId != null) {
                                itemView.setOnClickListener {
                                    openProfile(profileId)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore header items causing getItem errors
                        }
                    }
                } catch (e: Exception) {
                    // Suppress logging spam for every item
                }
            }
        } catch (e: Exception) {
            loge("CascadeGrid: Failed to hook adapter: ${e.message}")
        }
    }

    private fun isProfileItem(itemView: View): Boolean {
        val hasProfileImage = itemView.findViewById<View>(Utils.getId("profile_image", "id", GrindrPlus.context)) != null
        return hasProfileImage
    }

    private fun extractProfileId(obj: Any?): String? {
        if (obj == null) return null
        return try {
            getObjectField(obj, "profileId")?.toString()
        } catch (e: Exception) {
            try {
                getObjectField(obj, "profileIdLong")?.toString()
            } catch (e2: Exception) { null }
        }
    }
}