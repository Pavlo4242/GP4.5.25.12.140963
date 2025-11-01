/*
package com.grindrplus.hooks

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.children
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Constants
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logw
import com.grindrplus.persistence.model.TeleportLocationEntity
import com.grindrplus.ui.Utils
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationSpoofer : Hook(
    "Location spoofer",
    "Spoof your location"
) {
    private val location = "android.location.Location"
    private val chatBottomToolbar = "com.grindrapp.android.chat.presentation.ui.view.ChatBottomToolbar"
    private val appConfiguration = "com.grindrapp.android.platform.config.AppConfiguration"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    //private var diagnosticHookRan = false

    */
/**
     * Recursively dumps the view hierarchy to the logcat for diagnostic purposes.
     *//*

    */
/*    private fun dumpViewHierarchy(view: View, level: Int) {
            val indent = "  ".repeat(level)
            val viewInfo = StringBuilder()
            viewInfo.append("$indent- ${view.javaClass.name}")

            if (view.id != View.NO_ID) {
                try {
                    val resourceName = view.context.resources.getResourceEntryName(view.id)
                    viewInfo.append(" (id: $resourceName)")
                } catch (e: Exception) {
                    // Ignore if resource name can't be found
                }
            }

            when (view) {
                is TextView -> viewInfo.append(" [text: \"${view.text}\"]")
                is ImageView -> view.contentDescription?.let { viewInfo.append(" [contentDesc: \"$it\"]") }
            }

            logd(viewInfo.toString())

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    dumpViewHierarchy(child, level + 1)
                }
            }
        }*//*


    override fun init() {
        // TEMPORARY DIAGNOSTIC HOOK to dump the view hierarchy of the chat activity.
        */
/*try {
            findClass("com.grindrapp.android.chat.presentation.ui.ChatActivityV2")
                .hook("onCreate", HookStage.AFTER) { param ->
                    if (diagnosticHookRan) return@hook
                    val activity = param.thisObject() as android.app.Activity
                    activity.window.decorView.post {
                        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                        logd("--- Start View Hierarchy Dump for ChatActivityV2 ---")
                        dumpViewHierarchy(rootView, 0)
                        logd("--- End View Hierarchy Dump ---")
                        diagnosticHookRan = true
                    }
                }
        } catch (t: Throwable) {
            loge("LocationSpoofer DIAGNOSTIC: Failed to hook ChatActivityV2 onCreate.")
        }*//*


        val locationClass = findClass(location)

        if (Build.VERSION.SDK_INT >= 31) {
            locationClass.hook(
                "isMock",
                HookStage.BEFORE
            ) { param ->
                param.setResult(false)
            }
        } else {
            locationClass.hook(
                "isFromMockProvider",
                HookStage.BEFORE
            ) { param ->
                param.setResult(false)
            }
        }

        locationClass.hook("getLatitude", HookStage.AFTER) { param ->
            (Config.get("forced_coordinates", Config.get("current_location", "")) as String).takeIf {
                it.isNotEmpty()
            }?.split(",")?.firstOrNull()
                ?.toDoubleOrNull()?.let {
                    param.setResult(it)
                }
        }

        locationClass.hook("getLongitude", HookStage.AFTER) { param ->
            (Config.get("forced_coordinates", Config.get("current_location", "")) as String).takeIf {
                it.isNotEmpty()
            }?.split(",")?.lastOrNull()
                ?.toDoubleOrNull()?.let {
                    param.setResult(it)
                }
        }

        findClass(chatBottomToolbar).hook("onAttachedToWindow", HookStage.AFTER) { param ->
            logd("LocationSpoofer: hook 'onAttachedToWindow' for chatBottomToolbar triggered.")

            val chatBottomToolbarLinearLayout = param.thisObject() as LinearLayout
            // Safety check: Ensure children exist before proceeding
            if (chatBottomToolbarLinearLayout.childCount == 0) {
                logw("chatBottomToolbar has no children yet. Aborting.")
                return@hook
            }
            val exampleButton = chatBottomToolbarLinearLayout.children.first()

            // Check if our button has already been added to prevent duplicates
            if (chatBottomToolbarLinearLayout.children.any { it.tag == "custom_location_button" }) {
                logd("Location button already exists. Skipping.")
                return@hook
            }

            val grindrContext = try {
                GrindrPlus.context.createPackageContext(Constants.GRINDR_PACKAGE_NAME, 0)
            } catch (e: Exception) {
                loge("Failed to create package context for ${Constants.GRINDR_PACKAGE_NAME}. Aborting button creation.")
                return@hook
            }

            val rippleDrawableId = Utils.getId("image_button_ripple", "drawable", grindrContext)
            val locationIconId = Utils.getId("ic_my_location", "drawable", grindrContext)

            logd("Resource check: rippleDrawableId=$rippleDrawableId, locationIconId=$locationIconId")
            if (rippleDrawableId == 0 || locationIconId == 0) {
                loge("Required resources for location button not found. Aborting.")
                return@hook
            }

            val customLocationButton = ImageButton(chatBottomToolbarLinearLayout.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { weight = 1f }
                focusable = ImageButton.FOCUSABLE
                scaleType = ImageView.ScaleType.CENTER
                isClickable = true
                tag = "custom_location_button"
                contentDescription = "Teleport"
                setBackgroundResource(rippleDrawableId)
                setImageResource(locationIconId)
                setPadding(
                    exampleButton.paddingLeft,
                    exampleButton.paddingTop,
                    exampleButton.paddingRight,
                    exampleButton.paddingBottom
                )

                val grindrGray = "#9e9ea8".toColorInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    drawable.colorFilter =
                        BlendModeColorFilter(grindrGray, BlendMode.SRC_IN)
                } else {
                    @Suppress("DEPRECATION")
                    drawable.colorFilter =
                        PorterDuffColorFilter(grindrGray, PorterDuff.Mode.SRC_IN)
                }
            }

            customLocationButton.setOnClickListener {
                coroutineScope.launch {
                    val locations = getLocations()

                    val locationNames = locations.map { it.name }
                    val coordinatesMap = locations.associate { location ->
                        location.name to "${location.latitude}, ${location.longitude}"
                    }

                    val locationDialogView = LinearLayout(it.context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 32, 32, 32)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val textViewCoordinates = TextView(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 16
                            marginStart = 70
                            marginEnd = 70
                        }
                        gravity = Gravity.CENTER_HORIZONTAL
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                    }

                    val spinnerLocations = Spinner(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 16
                            marginStart = 120
                            marginEnd = 140
                        }
                    }

                    val adapter = object : ArrayAdapter<String>(
                        it.context,
                        android.R.layout.simple_spinner_item,
                        locationNames
                    ) {
                        override fun getView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup
                        ): View {
                            return getCustomView(position, convertView, parent)
                        }

                        override fun getDropDownView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup
                        ): View {
                            return getCustomView(position, convertView, parent)
                        }

                        private fun getCustomView(
                            position: Int,
                            convertView: View?,
                            parent: ViewGroup
                        ): View {
                            val view = (convertView as? TextView) ?: TextView(it.context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                minHeight = 120
                                gravity = (Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL)
                                textSize = 16f
                                setTextColor(Color.WHITE)
                                background = ColorDrawable(Color.TRANSPARENT)
                            }
                            view.text = getItem(position)
                            return view
                        }
                    }
                    spinnerLocations.adapter = adapter

                    spinnerLocations.onItemSelectedListener =
                        object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: AdapterView<*>,
                                view: View,
                                position: Int,
                                id: Long
                            ) {
                                textViewCoordinates.text = coordinatesMap[locationNames.getOrNull(position)]
                            }

                            override fun onNothingSelected(parent: AdapterView<*>) {
                                textViewCoordinates.text = ""
                            }
                        }

                    val wrapDrawable = DrawableCompat.wrap(spinnerLocations.background)
                    DrawableCompat.setTint(wrapDrawable, Color.WHITE)
                    spinnerLocations.background = wrapDrawable

                    val buttonsContainer = LinearLayout(it.context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 20
                            marginStart = 100
                            marginEnd = 100
                        }
                    }

                    val scrollView = android.widget.ScrollView(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            0
                        ).apply { weight = 1f }
                        isFillViewport = true
                    }

                    val buttonCopy = Button(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 30 }
                        text = "Copy Coordinates"
                        background = Utils.createButtonDrawable(Color.parseColor("#4CAF50"))
                        setTextColor(Color.WHITE)
                        setOnClickListener {
                            Utils.copyToClipboard(
                                "Coordinates",
                                textViewCoordinates.text.toString()
                            )
                        }
                    }

                    val buttonSet = Button(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 15 }
                        text = "Teleport to location"
                        background = Utils.createButtonDrawable(Color.parseColor("#2196F3"))
                        setTextColor(Color.WHITE)
                        setOnClickListener {
                            Config.put("current_location", textViewCoordinates.text.toString())
                            GrindrPlus.showToast(
                                Toast.LENGTH_LONG,
                                "Successfully teleported to ${textViewCoordinates.text}"
                            )
                        }
                    }

                    val buttonOpen = Button(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 15 }
                        text = "Open in Maps"
                        background = Utils.createButtonDrawable(Color.parseColor("#9C27B0"))
                        setTextColor(Color.WHITE)
                        setOnClickListener {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW, Uri.parse(
                                        "geo:${textViewCoordinates.text}"
                                    )
                                )
                            )
                        }
                    }

                    val buttonDelete = Button(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 15 }
                        text = "Delete Location"
                        background = Utils.createButtonDrawable(Color.parseColor("#FF0000"))
                        setTextColor(Color.WHITE)
                        setOnClickListener button@{
                            if (spinnerLocations.selectedItem == null) {
                                GrindrPlus.showToast(Toast.LENGTH_SHORT, "No location selected.")
                                return@button
                            }
                            val name = spinnerLocations.selectedItem.toString()

                            coroutineScope.launch {
                                deleteLocation(name)

                                val updatedLocations = getLocations()
                                val updatedLocationNames = updatedLocations.map { it.name }

                                withContext(Dispatchers.Main) {
                                    adapter.clear()
                                    adapter.addAll(updatedLocationNames)
                                    adapter.notifyDataSetChanged()
                                    if (updatedLocationNames.isEmpty()) {
                                        textViewCoordinates.text = ""
                                    }
                                }

                                GrindrPlus.showToast(Toast.LENGTH_LONG, "Location deleted")
                            }
                        }
                    }

                    val buttonAdd = Button(it.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 15 }
                        text = "Add New Location"
                        background = Utils.createButtonDrawable(Color.parseColor("#FF9800"))
                        setTextColor(Color.WHITE)
                        setOnClickListener {
                            showAddLocationDialog(it.context, adapter)
                        }
                    }
                    buttonsContainer.addView(buttonAdd)

                    if (locations.isNotEmpty()) {
                        buttonsContainer.addView(buttonCopy)
                        buttonsContainer.addView(buttonSet)
                        buttonsContainer.addView(buttonOpen)
                        buttonsContainer.addView(buttonDelete)
                    }

                    scrollView.addView(buttonsContainer)

                    for (view in arrayOf(
                        spinnerLocations,
                        textViewCoordinates, scrollView
                    )) {
                        locationDialogView.addView(view)
                    }

                    AlertDialog.Builder(it.context).apply {
                        setTitle("Teleport Locations")
                        setView(locationDialogView)
                        setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                        show()
                    }
                }
            }

            customLocationButton.setOnLongClickListener {
                GrindrPlus.executeAsync {
                    if (Config.get("default_locations_populated", false) as Boolean) {
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Default locations already populated.")
                    } else {
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Populating default locations...")
                        try {
                            com.grindrplus.persistence.GPDatabase.prePopulate(GrindrPlus.context)
                            Config.put("default_locations_populated", true)
                            GrindrPlus.showToast(Toast.LENGTH_SHORT, "Locations populated successfully!")
                        } catch (e: Exception) {
                            Logger.e("Failed to prepopulate locations: ${e.message}")
                            Logger.writeRaw(e.stackTraceToString())
                            GrindrPlus.showToast(Toast.LENGTH_LONG, "Failed to populate locations. See logs.")
                        }
                    }
                }
                true
            }

            logd("Adding custom location button to the toolbar.")
            val desiredPosition = 2
            if (chatBottomToolbarLinearLayout.childCount >= desiredPosition) {
                chatBottomToolbarLinearLayout.addView(customLocationButton, desiredPosition)
            } else {
                chatBottomToolbarLinearLayout.addView(customLocationButton)
            }
        }
    }

    private suspend fun getLocations(): List<TeleportLocationEntity> = withContext(Dispatchers.IO) {
        return@withContext GrindrPlus.database.teleportLocationDao().getLocations()
    }

    private suspend fun deleteLocation(name: String) = withContext(Dispatchers.IO) {
        GrindrPlus.database.teleportLocationDao().deleteLocation(name)
    }

    private fun showAddLocationDialog(context: Context, adapter: ArrayAdapter<String>) {
        val inputLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val editName = android.widget.EditText(context).apply {
            hint = "Location Name"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val editCoords = android.widget.EditText(context).apply {
            hint = "Latitude, Longitude (e.g. 40.7128, -74.0060)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }

        inputLayout.addView(editName)
        inputLayout.addView(editCoords)

        AlertDialog.Builder(context)
            .setTitle("Add Location")
            .setView(inputLayout)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString().trim()
                val coords = editCoords.text.toString().trim()

                if (name.isBlank() || !coords.matches(Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$"""))) {
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Invalid input")
                    return@setPositiveButton
                }

                val (lat, lon) = coords.split(",").map { it.trim().toDouble() }
                coroutineScope.launch {
                    GrindrPlus.database.teleportLocationDao().upsertLocation(
                        TeleportLocationEntity(name, lat, lon)
                    )
                    val updated = getLocations().map { it.name }
                    withContext(Dispatchers.Main) {
                        adapter.clear()
                        adapter.addAll(updated)
                        adapter.notifyDataSetChanged()
                    }
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Location saved")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}*/
