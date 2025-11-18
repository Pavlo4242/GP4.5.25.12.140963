package com.grindrplus.hooks

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.children
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.ui.Utils.copyToClipboard
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking

class StatusDialog : Hook(
    "Status Dialog",
    "Check whether GrindrPlus is alive or not"
) {
    private val tabView = "com.google.android.material.tabs.TabLayout\$TabView"
    private val homeActivityClass = "com.grindrapp.android.ui.home.HomeActivity"

    override fun init() {
        findClass(tabView).hookConstructor(HookStage.AFTER) { param ->
            val tabView = param.thisObject() as View
            tabView.post {
                val parent = tabView.parent as? ViewGroup
                val position = parent?.indexOfChild(tabView) ?: -1
                when (position) {
                    0 -> tabView.setOnLongClickListener { v ->
                        showGrindrPlusInfoDialog(v.context)
                        true
                    }
                    2 -> tabView.setOnLongClickListener { v ->
                        showProfileViewsDialog(v.context)
                        true
                    }
                }
            }
        }

        try {
            findClass(homeActivityClass).hook("onCreate", HookStage.AFTER) { param ->
                val activity = param.thisObject() as? android.app.Activity ?: return@hook
                activity.window.decorView.post {
                    try {
                        val toolbarId = activity.resources.getIdentifier("home_toolbar", "id", activity.packageName)
                        if (toolbarId == 0) return@post
                        val toolbar = activity.findViewById<androidx.appcompat.widget.Toolbar>(toolbarId)
                        val navButton = toolbar?.children?.find { it is ImageButton }
                        navButton?.setOnLongClickListener { v ->
                            showProfileViewsDialog(v.context)
                            true
                        }
                    } catch (e: Exception) {
                        Logger.e("Failed to attach long-click to profile icon: ${e.message}", LogSource.HOOK)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to hook HomeActivity for profile icon long-click: ${e.message}", LogSource.HOOK)
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
                        val message = if (recentViews.isEmpty()) {
                            "No profile views tracked yet."
                        } else {
                            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                            buildString {
                                appendLine("Recent profile views (${recentViews.size} of $totalCount total):")
                                recentViews.forEachIndexed { index, view ->
                                    appendLine()
                                    val timeStr = dateFormat.format(Date(view.timestamp))
                                    val nameStr = view.displayName?.let { " ($it)" } ?: ""
                                    appendLine("${index + 1}. ${view.profileId}$nameStr at $timeStr")
                                }
                            }
                        }

                        val builder = AlertDialog.Builder(context)
                            .setTitle("Recent Profile Views")
                            .setMessage(message)
                            .setPositiveButton("Close", null)
                            .setIcon(android.R.drawable.ic_menu_view)

                        if (recentViews.isNotEmpty()) {
                            builder.setNeutralButton("Copy IDs") { _, _ ->
                                copyToClipboard("Profile IDs", recentViews.joinToString(", ") { it.profileId })
                                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Copied ${recentViews.size} profile IDs")
                            }
                        }
                        builder.show()
                    }
                }
            }
        }
    }

    private fun getGrindrPlusInfo(context: Context): String {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong()
            val coords = (Config.get("forced_coordinates", Config.get("current_location", "")) as String).ifEmpty { "Not Spoofing (stock)" }
            val androidId = (Config.get("android_device_id", "") as? String)?.takeIf { it.isNotEmpty() }?.let { "Spoofing ($it)" } ?: "Not Spoofing (stock)"

            buildString {
                appendLine("GrindrPlus is active and running\n")
                appendLine("App Information:")
                appendLine("• Version: ${pi.versionName} ($versionCode)")
                appendLine("• Package: ${context.packageName}")
                appendLine("• Android ID: $androidId")
                appendLine("• Location: $coords\n")
                appendLine("Module Information:")
                appendLine("• GrindrPlus: ${BuildConfig.VERSION_NAME}")
                appendLine("• Bridge Status: ${if (GrindrPlus.bridgeClient.isConnected()) "Connected" else "Disconnected"}\n")
                appendLine("Device Information:")
                appendLine("• Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("• Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            }
        } catch (e: Exception) {
            "GrindrPlus is active.\n\nError getting details: ${e.message}"
        }
    }

    private fun showGrindrPlusInfoDialog(context: Context) {
        val message: String = getGrindrPlusInfo(context)
        val coords: String = (Config.get("forced_coordinates", Config.get("current_location", "")) as String).ifEmpty { "Not Spoofing (stock)" }

        AlertDialog.Builder(context)
            .setTitle("GrindrPlus")
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("OK", null)
            .setNeutralButton("Filters") { _, _ -> showAdvancedFiltersDialog(context) }
            .setNegativeButton("Copy Coords") { _, _ ->
                copyToClipboard("Coordinates", coords)
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Copied coordinates")
            }
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun showAdvancedFiltersDialog(context: Context) {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        fun createDialogButton(text: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text
                setOnClickListener { onClick() }
            }
        }

        fun showSubDialog(title: String, builder: (LinearLayout) -> Unit) {
            val subLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 20, 40, 20)
            }
            builder(subLayout)
            AlertDialog.Builder(context)
                .setTitle(title)
                .setView(subLayout)
                .setPositiveButton("Close", null)
                .show()
        }

        mainLayout.addView(createDialogButton("General Filters") {
            showSubDialog("General Filters") { layout ->
                layout.addView(Switch(context).apply {
                    text = "Favorites Only"
                    isChecked = Config.get("filter_favorites_only", false) as Boolean
                    setOnCheckedChangeListener { _, isChecked -> Config.put("filter_favorites_only", isChecked) }
                })
                layout.addView(Switch(context).apply {
                    text = "Has Social Networks"
                    isChecked = Config.get("filter_has_social_networks", false) as Boolean
                    setOnCheckedChangeListener { _, isChecked -> Config.put("filter_has_social_networks", isChecked) }
                })
            }
        })

        mainLayout.addView(createDialogButton("Distance Filter") {
            showSubDialog("Distance Filter") { layout ->
                val distanceInput = EditText(context).apply {
                    hint = "Max distance in meters (0 to disable)"
                    setText((Config.get("filter_max_distance", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(distanceInput)
                layout.addView(Button(context).apply {
                    text = "Apply"
                    setOnClickListener {
                        val distance = distanceInput.text.toString().toIntOrNull() ?: 0
                        Config.put("filter_max_distance", distance)
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Max distance set.")
                    }
                })
            }
        })

        mainLayout.addView(createDialogButton("Text Filters (About Me & Tags)") {
            showSubDialog("Text Filters") { layout ->
                val aboutInput = EditText(context).apply {
                    hint = "Text in 'About Me'"
                    setText(Config.get("filter_about_text", "") as String)
                }
                val aboutModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include); addView(exclude)
                    check(if (Config.get("filter_about_mode", "include") == "include") include.id else exclude.id)
                }

                val tagsInput = EditText(context).apply {
                    hint = "Tags (comma-separated)"
                    setText(Config.get("filter_tags", "") as String)
                }
                val tagsModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include (Any)"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include); addView(exclude)
                    check(if (Config.get("filter_tags_mode", "include") == "include") include.id else exclude.id)
                }

                val applyButton = Button(context).apply { text = "Apply" }
                applyButton.setOnClickListener {
                    Config.put("filter_about_text", aboutInput.text.toString())
                    Config.put("filter_about_mode", if (aboutModeGroup.checkedRadioButtonId == aboutModeGroup.getChildAt(0).id) "include" else "exclude")
                    Config.put("filter_tags", tagsInput.text.toString())
                    Config.put("filter_tags_mode", if (tagsModeGroup.checkedRadioButtonId == tagsModeGroup.getChildAt(0).id) "include" else "exclude")
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Text filters applied.")
                }

                layout.addView(TextView(context).apply { text = "About Me Filter"; setTypeface(null, android.graphics.Typeface.BOLD) })
                layout.addView(aboutInput)
                layout.addView(aboutModeGroup)
                layout.addView(TextView(context).apply { text = "Tags Filter"; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 16, 0, 0) })
                layout.addView(tagsInput)
                layout.addView(tagsModeGroup)
                layout.addView(applyButton)
            }
        })

        mainLayout.addView(createDialogButton("Attribute Filters (Age, etc.)") {
            showSubDialog("Attribute Filters") { layout ->
                layout.addView(TextView(context).apply { text = "Age Filter"; setTypeface(null, android.graphics.Typeface.BOLD) })
                val ageMinInput = EditText(context).apply {
                    hint = "Min Age"
                    setText((Config.get("filter_age_min", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(ageMinInput)
                val ageMaxInput = EditText(context).apply {
                    hint = "Max Age"
                    setText((Config.get("filter_age_max", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(ageMaxInput)
                val includeNoAgeSwitch = Switch(context).apply {
                    text = "Include profiles with no age"
                    isChecked = Config.get("filter_age_include_no_age", true) as Boolean
                }
                layout.addView(includeNoAgeSwitch)

                // Add spacing
                layout.addView(TextView(context).apply {
                    height = 30
                })

                // Ethnicity Spinner
                layout.addView(TextView(context).apply {
                    text = "Ethnicity"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 8, 0, 8)
                })
                val ethnicitySpinner = Spinner(context)
                val ethnicityOptions = arrayOf("Any", "Asian", "Black", "Latino", "Middle Eastern", "Mixed", "Native American", "South Asian", "White", "Other")
                val ethnicityAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, ethnicityOptions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                ethnicitySpinner.adapter = ethnicityAdapter
                val savedEthnicity = Config.get("filter_ethnicity", "0") as String
                val ethnicityInt = savedEthnicity.toIntOrNull() ?: 0
                if (ethnicityInt in ethnicityOptions.indices) {
                    ethnicitySpinner.setSelection(ethnicityInt)
                }
                layout.addView(ethnicitySpinner)
                val ethnicityModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include); addView(exclude)
                    check(if (Config.get("filter_ethnicity_mode", "include") == "include") include.id else exclude.id)
                }
                layout.addView(ethnicityModeGroup)

                // Gender Spinner
                layout.addView(TextView(context).apply {
                    text = "Gender"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 16, 0, 8)
                })
                val genderSpinner = Spinner(context)
                val genderOptions = arrayOf("Any", "Man", "Trans", "Non-binary", "Cis Woman", "Trans Woman", "Cis Man", "Trans Man")
                val genderAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, genderOptions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                genderSpinner.adapter = genderAdapter
                val savedGender = Config.get("filter_gender", 0) as Int
                if (savedGender in genderOptions.indices) {
                    genderSpinner.setSelection(savedGender)
                }
                layout.addView(genderSpinner)

                // Tribe Spinner
                layout.addView(TextView(context).apply {
                    text = "Tribe"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 16, 0, 8)
                })
                val tribeSpinner = Spinner(context)
                val tribeOptions = arrayOf("Any", "Bear", "Clean-Cut", "Daddy", "Discreet", "Geek", "Jock", "Leather", "Military", "Otter", "Poz", "Rugged", "Sober", "Trans", "Twink")
                val tribeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, tribeOptions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                tribeSpinner.adapter = tribeAdapter
                val savedTribe = Config.get("filter_tribe", 0) as Int
                if (savedTribe in tribeOptions.indices) {
                    tribeSpinner.setSelection(savedTribe)
                }
                layout.addView(tribeSpinner)

                val applyButton = Button(context).apply { text = "Apply Filters"; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 32 } }
                applyButton.setOnClickListener {
                    Config.put("filter_age_min", ageMinInput.text.toString().toIntOrNull() ?: 0)
                    Config.put("filter_age_max", ageMaxInput.text.toString().toIntOrNull() ?: 0)
                    Config.put("filter_age_include_no_age", includeNoAgeSwitch.isChecked)
                    Config.put("filter_ethnicity", ethnicitySpinner.selectedItemPosition.toString())
                    Config.put("filter_ethnicity_mode", if (ethnicityModeGroup.checkedRadioButtonId == ethnicityModeGroup.getChildAt(0).id) "include" else "exclude")
                    Config.put("filter_gender", genderSpinner.selectedItemPosition)
                    Config.put("filter_tribe", tribeSpinner.selectedItemPosition)
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Attribute filters applied.")
                }
                layout.addView(applyButton)
            }
        })

        val toggleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 10)
        }
        val statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val masterSwitch = Switch(context).apply {
            isChecked = Config.get("custom_filtering_enabled", false) as Boolean
            statusText.text = if (isChecked) "Filtering is ON" else "Filtering is OFF"
            setOnCheckedChangeListener { _, isChecked ->
                Config.put("custom_filtering_enabled", isChecked)
                statusText.text = if (isChecked) "Filtering is ON" else "Filtering is OFF"
            }
        }
        toggleLayout.addView(statusText)
        toggleLayout.addView(masterSwitch)
        mainLayout.addView(toggleLayout)

        AlertDialog.Builder(context)
            .setTitle("Advanced Cascade Filters")
            .setView(mainLayout)
            .setPositiveButton("Close", null)
            .setNegativeButton("Reset All") { _, _ ->
                Config.put("custom_filtering_enabled", false)
                Config.put("filter_max_distance", 0)
                Config.put("filter_favorites_only", false)
                Config.put("filter_has_social_networks", false)
                Config.put("filter_about_text", "")
                Config.put("filter_about_mode", "include")
                Config.put("filter_tags", "")
                Config.put("filter_tags_mode", "include")
                Config.put("filter_age_min", 0)
                Config.put("filter_age_max", 0)
                Config.put("filter_age_include_no_age", true)
                Config.put("filter_ethnicity", "0")
                Config.put("filter_ethnicity_mode", "include")
                Config.put("filter_gender", 0)
                Config.put("filter_tribe", 0)
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "All filters have been reset.")
            }
            .show()
    }
}


    @SuppressLint("SetTextI18n")
    private fun showAdvancedFiltersDialog(context: Context) {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        fun createDialogButton(text: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text
                setOnClickListener { onClick() }
            }
        }

        fun showSubDialog(title: String, builder: (LinearLayout) -> Unit) {
            val subLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 20, 40, 20)
            }
            builder(subLayout)
            AlertDialog.Builder(context)
                .setTitle(title)
                .setView(subLayout)
                .setPositiveButton("Close", null)
                .show()
        }

        mainLayout.addView(createDialogButton("General Filters") {
            showSubDialog("General Filters") { layout ->
                layout.addView(Switch(context).apply {
                    text = "Favorites Only"
                    isChecked = Config.get("filter_favorites_only", false) as Boolean
                    setOnCheckedChangeListener { _, isChecked -> Config.put("filter_favorites_only", isChecked) }
                })
                layout.addView(Switch(context).apply {
                    text = "Has Social Networks"
                    isChecked = Config.get("filter_has_social_networks", false) as Boolean
                    setOnCheckedChangeListener { _, isChecked -> Config.put("filter_has_social_networks", isChecked) }
                })
            }
        })

        mainLayout.addView(createDialogButton("Distance Filter") {
            showSubDialog("Distance Filter") { layout ->
                val distanceInput = EditText(context).apply {
                    hint = "Max distance in meters (0 to disable)"
                    setText((Config.get("filter_max_distance", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(distanceInput)
                layout.addView(Button(context).apply {
                    text = "Apply"
                    setOnClickListener {
                        val distance = distanceInput.text.toString().toIntOrNull() ?: 0
                        Config.put("filter_max_distance", distance)
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Max distance set.")
                    }
                })
            }
        })

        mainLayout.addView(createDialogButton("Text Filters (About Me & Tags)") {
            showSubDialog("Text Filters") { layout ->
                // About Me
                val aboutInput = EditText(context).apply {
                    hint = "Text in 'About Me' (e.g., travel)"
                    setText(Config.get("filter_about_text", "") as String)
                }
                val aboutModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include)
                    addView(exclude)
                    check(if (Config.get("filter_about_mode", "include") == "include") include.id else exclude.id)
                }

                // Tags
                val tagsInput = EditText(context).apply {
                    hint = "Tags (comma-separated, e.g., bear,otter)"
                    setText(Config.get("filter_tags", "") as String)
                }
                val tagsModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include (Any)"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include)
                    addView(exclude)
                    check(if (Config.get("filter_tags_mode", "include") == "include") include.id else exclude.id)
                }

                val applyButton = Button(context).apply { text = "Apply" }
                applyButton.setOnClickListener {
                    Config.put("filter_about_text", aboutInput.text.toString())
                    Config.put("filter_about_mode", if (aboutModeGroup.checkedRadioButtonId == aboutModeGroup.getChildAt(0).id) "include" else "exclude")
                    Config.put("filter_tags", tagsInput.text.toString())
                    Config.put("filter_tags_mode", if (tagsModeGroup.checkedRadioButtonId == tagsModeGroup.getChildAt(0).id) "include" else "exclude")
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Text filters applied.")
                }

                layout.addView(TextView(context).apply { text = "About Me Filter"; setTypeface(null, android.graphics.Typeface.BOLD) })
                layout.addView(aboutInput)
                layout.addView(aboutModeGroup)
                layout.addView(TextView(context).apply { text = "Tags Filter"; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 16, 0, 0) })
                layout.addView(tagsInput)
                layout.addView(tagsModeGroup)
                layout.addView(applyButton)
            }
        })

        mainLayout.addView(createDialogButton("Text Filters (About Me & Tags)") {
            showSubDialog("Text Filters") { layout ->
                // About Me
                val aboutInput = EditText(context).apply {
                    hint = "Text in 'About Me' (e.g., travel)"
                    setText(Config.get("filter_about_text", "") as String)
                }
                val aboutModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include)
                    addView(exclude)
                    check(if (Config.get("filter_about_mode", "include") == "include") include.id else exclude.id)
                }

                // Tags
                val tagsInput = EditText(context).apply {
                    hint = "Tags (comma-separated, e.g., bear,otter)"
                    setText(Config.get("filter_tags", "") as String)
                }
                val tagsModeGroup = RadioGroup(context).apply {
                    orientation = RadioGroup.HORIZONTAL
                    val include = RadioButton(context).apply { text = "Include (Any)"; id = View.generateViewId() }
                    val exclude = RadioButton(context).apply { text = "Exclude"; id = View.generateViewId() }
                    addView(include)
                    addView(exclude)
                    check(if (Config.get("filter_tags_mode", "include") == "include") include.id else exclude.id)
                }

                val applyButton = Button(context).apply { text = "Apply" }
                applyButton.setOnClickListener {
                    Config.put("filter_about_text", aboutInput.text.toString())
                    Config.put("filter_about_mode", if (aboutModeGroup.checkedRadioButtonId == aboutModeGroup.getChildAt(0).id) "include" else "exclude")
                    Config.put("filter_tags", tagsInput.text.toString())
                    Config.put("filter_tags_mode", if (tagsModeGroup.checkedRadioButtonId == tagsModeGroup.getChildAt(0).id) "include" else "exclude")
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Text filters applied.")
                }

                layout.addView(TextView(context).apply { text = "About Me Filter"; setTypeface(null, android.graphics.Typeface.BOLD) })
                layout.addView(aboutInput)
                layout.addView(aboutModeGroup)
                layout.addView(TextView(context).apply { text = "Tags Filter"; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 16, 0, 0) })
                layout.addView(tagsInput)
                layout.addView(tagsModeGroup)
                layout.addView(applyButton)
            }
        })

        mainLayout.addView(createDialogButton("Attribute Filters (Age, etc.)") {
            showSubDialog("Attribute Filters") { layout ->
                // Age Section
                layout.addView(TextView(context).apply {
                    text = "Age Filter"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                val ageMinInput = EditText(context).apply {
                    hint = "Min Age (e.g., 25)"
                    setText((Config.get("filter_age_min", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(ageMinInput)
                val ageMaxInput = EditText(context).apply {
                    hint = "Max Age (e.g., 35)"
                    setText((Config.get("filter_age_max", 0) as Int).toString())
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                layout.addView(ageMaxInput)
                val includeNoAgeSwitch = Switch(context).apply {
                    text = "Include profiles with no age"
                    isChecked = Config.get("filter_age_include_no_age", true) as Boolean
                }
                layout.addView(includeNoAgeSwitch)
                // Add spacing
                layout.addView(TextView(context).apply {
                    height = 30
                })

                // Ethnicity Spinner
                val ethnicityLabel = TextView(context).apply {
                    text = "Ethnicity"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 8)
                }
                layout.addView(ethnicityLabel)

                val ethnicitySpinner = Spinner(context)
                val ethnicityOptions = arrayOf(
                    "Any Ethnicity", "African", "Asian", "Caucasian", "Hispanic/Latino",
                    "Middle Eastern", "Mixed Race", "Native American", "Pacific Islander", "Other"
                )
                val ethnicityAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, ethnicityOptions)
                ethnicityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                ethnicitySpinner.adapter = ethnicityAdapter

                // Set saved ethnicity if exists
                val savedEthnicity = Config.get("filter_ethnicity", "Any Ethnicity") as String
                val ethnicityPosition = ethnicityOptions.indexOfFirst { it == savedEthnicity }
                if (ethnicityPosition != -1) ethnicitySpinner.setSelection(ethnicityPosition)

                layout.addView(ethnicitySpinner)

                // Add spacing
                layout.addView(TextView(context).apply {
                    height = 20
                })

                // Gender Spinner
                val genderLabel = TextView(context).apply {
                    text = "Gender"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 8)
                }
                layout.addView(genderLabel)

                val genderSpinner = Spinner(context)
                val genderOptions = arrayOf(
                    "Any Gender", "Male", "Female", "Non-binary", "Genderqueer",
                    "Genderfluid", "Agender", "Bigender", "Two-spirit", "Trans Man",
                    "Trans Woman", "Cis Man", "Cis Woman", "Other"
                )
                val genderAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, genderOptions)
                genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                genderSpinner.adapter = genderAdapter

                // Set saved gender if exists
                val savedGender = Config.get("filter_gender", "Any Gender") as String
                val genderPosition = genderOptions.indexOfFirst { it == savedGender }
                if (genderPosition != -1) genderSpinner.setSelection(genderPosition)

                layout.addView(genderSpinner)

                // Add spacing
                layout.addView(TextView(context).apply {
                    height = 20
                })

                // Tribe Spinner
                val tribeLabel = TextView(context).apply {
                    text = "Tribe"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 8)
                }
                layout.addView(tribeLabel)

                val tribeSpinner = Spinner(context)
                val tribeOptions = arrayOf(
                    "Any Tribe", "Bear", "Otter", "Trans", "Sober", "Twink", "College",
                    "Jock", "Leather", "Daddy", "Discreet", "Poz", "Negative", "Undetectable",
                    "Geek", "Military", "Rugby", "Gym Rat", "Student", "Professional"
                )
                val tribeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, tribeOptions)
                tribeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                tribeSpinner.adapter = tribeAdapter

                // Set saved tribe if exists
                val savedTribe = Config.get("filter_tribe", "Any Tribe") as String
                val tribePosition = tribeOptions.indexOfFirst { it == savedTribe }
                if (tribePosition != -1) tribeSpinner.setSelection(tribePosition)

                layout.addView(tribeSpinner)

                // Add spacing before apply button
                layout.addView(TextView(context).apply {
                    height = 30
                })

                val applyButton = Button(context).apply { text = "Apply Filters" }
                applyButton.setOnClickListener {
                    // Save age filters
                    Config.put("filter_age_min", ageMinInput.text.toString().toIntOrNull() ?: 0)
                    Config.put("filter_age_max", ageMaxInput.text.toString().toIntOrNull() ?: 0)
                    Config.put("filter_age_include_no_age", includeNoAgeSwitch.isChecked)

                    // Save spinner values
                    Config.put("filter_ethnicity", ethnicitySpinner.selectedItem.toString())
                    Config.put("filter_gender", genderSpinner.selectedItem.toString())
                    Config.put("filter_tribe", tribeSpinner.selectedItem.toString())

                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Attribute filters applied.")
                }

                layout.addView(applyButton)
            }
        })

        val toggleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 10)
        }
        val statusText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val masterSwitch = Switch(context).apply {
            isChecked = Config.get("custom_filtering_enabled", false) as Boolean
            statusText.text = if (isChecked) "Filtering is ON" else "Filtering is OFF"
            setOnCheckedChangeListener { _, isChecked ->
                Config.put("custom_filtering_enabled", isChecked)
                statusText.text = if (isChecked) "Filtering is ON" else "Filtering is OFF"
            }
        }
        toggleLayout.addView(statusText)
        toggleLayout.addView(masterSwitch)
        mainLayout.addView(toggleLayout)

        AlertDialog.Builder(context)
            .setTitle("Advanced Cascade Filters")
            .setView(mainLayout)
            .setPositiveButton("Close", null)
            .setNegativeButton("Reset All") { _, _ ->
                Config.put("custom_filtering_enabled", false)
                Config.put("filter_max_distance", 0)
                Config.put("filter_favorites_only", false)
                Config.put("filter_has_social_networks", false)
                Config.put("filter_about_text", "")
                Config.put("filter_about_mode", "include")
                Config.put("filter_tags", "")
                Config.put("filter_tags_mode", "include")
                Config.put("filter_age_min", 0)
                Config.put("filter_age_max", 0)
                Config.put("filter_age_include_no_age", true)
                // Reset spinner configs as well
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "All filters have been reset.")
            }
            .show()
    }

// Ethnicity, Gender, Tribe (Spinners for selection)
/*
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

            val currentLocationName = if (currentLocationCoords != "Not Spoofing (stock)") {
                val parts = currentLocationCoords.split(",")
                if (parts.size == 2) {
                    val lat = parts[0].trim().toDoubleOrNull()
                    val lon = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lon != null) {
                        runBlocking {
                            val locations =
                                GrindrPlus.database.teleportLocationDao().getLocations()
                            locations.find {
                                abs(it.latitude - lat) < 1e-6 && abs(it.longitude - lon) < 1e-6
                            }?.name ?: "Unnamed Location"
                        }
                    } else {
                        "Invalid Coordinates"
                    }
                } else {
                    "Invalid Coordinates"
                }
            } else {
                "Not Spoofing"
            }


            val androidDeviceIdStatus = (Config.get("android_device_id", "") as String)
                .let { id -> if (id.isNotEmpty()) "Spoofing ($id)" else "Not Spoofing (stock)" }

            val message = buildString {
                appendLine("GrindrPlus is active and running")
                appendLine()
                appendLine("App Information:")
                appendLine("• Version: $appVersionName ($appVersionCode)")
                appendLine("• Package: $packageName")
                appendLine("• Android ID: $androidDeviceIdStatus")
                appendLine("• Current Location Coords: $currentLocationCoords")
                appendLine("• Current Location Name: $currentLocationName")
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
                .setNegativeButton("Copy") { _, _ ->
                    val coords = if (currentLocationCoords == "Not Spoofing (stock)") {
                        try {
                            val locationManager =
                                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            val providers = listOf(
                                LocationManager.GPS_PROVIDER,
                                LocationManager.NETWORK_PROVIDER
                            )
                            var location: Location? = null
                            for (provider in providers) {
                                if (locationManager.isProviderEnabled(provider)) {
                                    location = locationManager.getLastKnownLocation(provider)
                                    if (location != null) break
                                }
                            }
                            if (location != null) "${location.latitude}, ${location.longitude}" else "Unable to get location"
                        } catch (e: Exception) {
                            "Unable to get location: ${e.message}"
                        }
                    } else {
                        currentLocationCoords
                    }
                    copyToClipboard("Coordinates", coords)
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Copied coordinates")
                }

                .setNeutralButton("MORE") { _, _ ->
                    showMoreDialog(context)
                }
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
*/



//    private fun showMoreDialog(context: Context) {
//        val layout = LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(32, 32, 32, 32)
//        }
//
//        val onlineCheck = CheckBox(context).apply {
//            text = "Online Now"
//            isChecked = Config.get("filter_online", false) as Boolean
//        }
//        layout.addView(onlineCheck)
//
//        val hasPhotoCheck = CheckBox(context).apply {
//            text = "Has Photo"
//            isChecked = Config.get("filter_has_photo", false) as Boolean
//        }
//        layout.addView(hasPhotoCheck)
//
//        val minAgeEdit = EditText(context).apply {
//            hint = "Min Age"
//            inputType = android.text.InputType.TYPE_CLASS_NUMBER
//            setText(Config.get("filter_min_age", "") as String)
//        }
//        layout.addView(minAgeEdit)
//
//        val maxAgeEdit = EditText(context).apply {
//            hint = "Max Age"
//            inputType = android.text.InputType.TYPE_CLASS_NUMBER
//            setText(Config.get("filter_max_age", "") as String)
//        }
//        layout.addView(maxAgeEdit)
//
//        AlertDialog.Builder(context)
//            .setTitle("Advanced Filters")
//            .setView(layout)
//            .setPositiveButton("Apply") { _, _ ->
//                Config.put("filter_online", onlineCheck.isChecked)
//                Config.put("filter_has_photo", hasPhotoCheck.isChecked)
//                Config.put("filter_min_age", minAgeEdit.text.toString())
//                Config.put("filter_max_age", maxAgeEdit.text.toString())
//                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Filters applied")
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//}
