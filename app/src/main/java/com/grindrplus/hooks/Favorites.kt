package com.grindrplus.hooks

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isGone
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.ui.Utils
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlin.math.roundToInt

class Favorites : Hook(
    "Favorites",
    "Customize layout for the favorites tab"
) {
    private val favoritesFragment = "com.grindrapp.android.favorites.presentation.ui.FavoritesFragment"

    override fun init() {
        findClass(favoritesFragment).hook("onViewCreated", HookStage.AFTER) { param ->
            try {
                logd("Favorites: onViewCreated hooked")
                val columnsNumber = (Config.get("favorites_grid_columns", 3) as Number).toInt()
                val view = param.arg<View>(0)

                // 1. Find RecyclerView via Reflection safe ID check
                val recyclerId = Utils.getId("fragment_favorite_recycler_view", "id", GrindrPlus.context)
                val recyclerView = view.findViewById<View>(recyclerId)

                if (recyclerView == null) {
                    loge("Favorites: RecyclerView not found")
                    return@hook
                }

                // 2. Set Grid Columns via Reflection
                val layoutManager = callMethod(recyclerView, "getLayoutManager")
                if (layoutManager != null && layoutManager.javaClass.name.contains("GridLayoutManager")) {
                    logd("Favorites: Setting span count to $columnsNumber")
                    callMethod(layoutManager, "setSpanCount", columnsNumber)
                } else {
                    loge("Favorites: LayoutManager is not GridLayoutManager")
                }

                // 3. Hook Adapter
                val adapter = callMethod(recyclerView, "getAdapter") ?: return@hook

                adapter.javaClass.hook("onBindViewHolder", HookStage.AFTER) { bindParam ->
                    val holder = bindParam.arg<Any>(0)
                    val itemView = getObjectField(holder, "itemView") as View

                    if (isProfileItem(itemView)) {
                        fixLayout(itemView, columnsNumber)
                        applyTextStyling(itemView)
                        fixLastSeenMargins(itemView)

                        // Force status refresh attempt
                        try {
                            val data = getObjectField(holder, "data")
                            getObjectField(data, "profileId")
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                loge("Favorites Error: ${e.message}")
            }
        }
    }

    private fun fixLayout(itemView: View, columns: Int) {
        val displayMetrics = GrindrPlus.context.resources.displayMetrics
        val targetSize = displayMetrics.widthPixels / columns

        val lp = itemView.layoutParams
        if (lp != null) {
            lp.width = targetSize
            lp.height = targetSize
            // reflection to set margins if needed, but width/height is main priority
            itemView.layoutParams = lp
        }

        val imageId = Utils.getId("profile_image", "id", GrindrPlus.context)
        val imageView = itemView.findViewById<ImageView>(imageId)
        imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun isProfileItem(itemView: View): Boolean {
        val hasImage = itemView.findViewById<View>(Utils.getId("profile_image", "id", GrindrPlus.context)) != null
        val hasName = itemView.findViewById<View>(Utils.getId("display_name", "id", GrindrPlus.context)) != null
        return hasImage && hasName
    }

    private fun applyTextStyling(itemView: View) {
        val distanceTextView = itemView.findViewById<TextView>(Utils.getId("profile_distance", "id", GrindrPlus.context)) ?: return
        val linearLayout = distanceTextView.parent as? LinearLayout
        if (linearLayout != null) {
            linearLayout.orientation = LinearLayout.VERTICAL
            linearLayout.children.forEach { child ->
                child.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
        distanceTextView.gravity = Gravity.START
    }

    private fun fixLastSeenMargins(itemView: View) {
        val profileOnlineNowIcon = itemView.findViewById<ImageView>(Utils.getId("profile_online_now_icon", "id", GrindrPlus.context))
        val profileLastSeen = itemView.findViewById<TextView>(Utils.getId("profile_last_seen", "id", GrindrPlus.context))
        if (profileLastSeen != null && profileOnlineNowIcon != null) {
            val lp = profileLastSeen.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                lp.topMargin = if (profileOnlineNowIcon.isGone) 0 else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, GrindrPlus.context.resources.displayMetrics).roundToInt()
                profileLastSeen.layoutParams = lp
            }
        }
    }
}