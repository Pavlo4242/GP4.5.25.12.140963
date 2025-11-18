package com.grindrplus.hooks

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.ui.Utils
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass

class CascadeGrid : Hook(
    "CascadeGrid",
    "Customize columns for the main cascade (Browse)"
) {
    // Update this class name if it differs in your version.
    // Use Frida/Layout Inspector to confirm. Common names:
    // com.grindrapp.android.ui.browse.BrowseFragment
    // com.grindrapp.android.fragment.CascadeFragment
    private val browseFragment = "com.grindrapp.android.ui.browse.BrowseFragment"

    override fun init() {
        findClass(browseFragment)
            .hook("onViewCreated", HookStage.AFTER) { param ->
                val columns = Config.get("cascade_grid_columns", 4) as Int // Default to 4
                val view = param.arg<View>(0)

                // Find the main RecyclerView (ID often matches the favorites one or is 'recycler_view')
                val recyclerView = view.findViewById<RecyclerView>(
                    Utils.getId("fragment_feed_recycler_view", "id", GrindrPlus.context)
                ) ?: view.findViewById<RecyclerView>(
                    Utils.getId("recycler_view", "id", GrindrPlus.context)
                ) ?: return@hook

                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return@hook

                // Set the new span count
                layoutManager.spanCount = columns

                // CRITICAL: Handle SpanSizeLookup
                // The cascade has ads, upsells, and headers. We don't want those squished into 1 column.
                // We wrap the existing lookup to preserve logic for non-profile items.
                val originalLookup = layoutManager.spanSizeLookup

                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        // Ask the adapter what type of view this is
                        val adapter = recyclerView.adapter ?: return 1
                        val viewType = adapter.getItemViewType(position)

                        // Heuristic: Typically Profiles have a specific ViewType ID.
                        // If we can't identify the exact ID, we assume anything taking up 1 span
                        // in a standard 3-grid is a profile.
                        // However, a safer bet is checking if the original lookup thought it was full width.

                        val originalSpan = originalLookup.getSpanSize(position)
                        val originalSpanCount = 3 // Standard Grindr grid

                        // If the item was previously taking up the FULL width (e.g. Header/Ad), keep it full width.
                        if (originalSpan == originalSpanCount) {
                            return columns
                        }

                        // Otherwise, it's likely a profile tile, so it takes 1 slot.
                        return 1
                    }
                }

                val adapter = recyclerView.adapter ?: return@hook

                // Hook onBind to enforce square sizing (fixes distortion)
                adapter::class.java.hook("onBindViewHolder", HookStage.AFTER) { param ->
                    val holder = param.arg<RecyclerView.ViewHolder>(0)
                    val itemView = holder.itemView

                    // Only resize if it's a grid item (Profile)
                    // We can check layout params or view type
                    val lp = itemView.layoutParams
                    if (lp is GridLayoutManager.LayoutParams) {
                        // If it's set to span full width, don't force square height
                        if (lp.spanSize == columns) return@hook

                        val displayMetrics = GrindrPlus.context.resources.displayMetrics
                        val size = displayMetrics.widthPixels / columns

                        lp.width = size
                        lp.height = size
                        itemView.layoutParams = lp
                    }
                }
            }
    }
}