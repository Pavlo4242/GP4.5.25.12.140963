package com.grindrplus.hooks

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.children
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Constants
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.core.Utils
import com.grindrplus.core.Utils.calculateBMI
import com.grindrplus.core.Utils.h2n
import com.grindrplus.core.Utils.w2n
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logw
import com.grindrplus.ui.Utils.copyToClipboard
import com.grindrplus.ui.Utils.formatEpochSeconds
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList
import kotlin.math.roundToInt

class ProfileDetails : Hook("Profile details", "Add extra fields and details to profiles") {
    private val profileId = emptyList<String>()
    private var boostedProfilesList = emptyList<String>()
    private val blockedProfilesObserver = "cf.p"
    private val profileViewHolder = "cf.F\$b"
    private val distanceUtils = "com.grindrapp.android.utils.DistanceUtils"
    private val profileBarView = "com.grindrapp.android.ui.profileV2.ProfileBarView"
    private val profileViewState = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
   // private val profileFragment = "com.grindrapp.android.ui.profileV2.ProfileFragment" // ADDED

    private val serverDrivenCascadeCachedState =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
    private val serverDrivenCascadeCachedProfile =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"

    private val profileQuickBarView = "com.grindrapp.android.ui.profileV2.ProfileQuickbarView"
    private val profileQuickBar = "com.grindrapp.android.ui.profileV2.ProfileQuickbarView"

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("DefaultLocale")
    override fun init() {
        // Hook for boosted profiles detection
        findClass(serverDrivenCascadeCachedState).hook("getItems", HookStage.AFTER) { param ->
            (param.getResult() as List<*>)
                .filter { (it?.javaClass?.name ?: "") == serverDrivenCascadeCachedProfile }
                .forEach {
                    if (getObjectField(it, "isBoosting") as Boolean) {
                        boostedProfilesList += callMethod(it, "getProfileId") as String
                    }
                }
        }

        // Hook for blocked profiles list - show profile ID
        findClass(blockedProfilesObserver).hook("onChanged", HookStage.AFTER) { param ->
            val profileList = getObjectField(
                getObjectField(param.thisObject(), "a"), "o"
            ) as ArrayList<*>
            for (profile in profileList) {
                val profileId = callMethod(profile, "getProfileId") as String
                val displayName =
                    (callMethod(profile, "getDisplayName") as? String)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { "$it ($profileId)" } ?: profileId
                setObjectField(profile, "displayName", displayName)
            }
        }

        // Hook for blocked profiles view holder - copy on long click
        findClass(profileViewHolder).hookConstructor(HookStage.AFTER) { param ->
            val textView = getObjectField(param.thisObject(), "b") as TextView

            textView.setOnLongClickListener {
                val text = textView.text.toString()
                val profileId = if ("(" in text && ")" in text)
                    text.substringAfter("(").substringBefore(")")
                else text

                copyToClipboard("Profile ID", profileId)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Profile ID: $profileId")
                true
            }
        }

        // ADDED: Hook for main profile fragment to attach click listeners to display name
   /*     findClass(profileFragment).hook("onViewCreated", HookStage.AFTER) { param ->
            try {
                val fragment = param.thisObject()
                val view = param.arg<View>(0)

                // Wait for the view to be fully laid out
                view.post {
                    try {
                        // Look for the display name TextView in the profile fragment
                        // Common IDs for profile name: profile_name, display_name, etc.
                        val displayNameId = view.resources.getIdentifier(
                            "profile_name", "id", Constants.GRINDR_PACKAGE_NAME
                        )

                        if (displayNameId == 0) {
                            loge("Could not find profile_name ID in profile fragment")
                            return@post
                        }

                        val displayNameTextView = view.findViewById<TextView>(displayNameId)
                        if (displayNameTextView != null) {
                            attachProfileDetailsHandlers(displayNameTextView, fragment)
                            logd("SUCCESS: Attached profile details handlers to main profile fragment")
                        } else {
                            loge("Could not find profile name TextView in profile fragment")
                        }
                    } catch (e: Exception) {
                        loge("Error attaching handlers to profile fragment: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                loge("Error in profile fragment hook: ${e.message}")
            }
        }
*/
        // REVISED HOOK: Attach listeners in the data binding method for reliability.
        findClass(profileQuickBarView).hookConstructor(stage = HookStage.AFTER) { param ->
            val profileQuickBarViewInstance = param.thisObject() // Remove the LinearLayout cast

            // Get the context from the view
            val context = callMethod(profileQuickBarViewInstance, "getContext") as Context

            // Post the logic to run after the view has been initialized and laid out
            val view = profileQuickBarViewInstance as android.view.View
            view.post {
                logd("ProfileDetails: post() block executed for ProfileQuickbarView.")

                // Check if button already exists using View.findViewWithTag
                val existingButton = view.findViewWithTag<View>("profile_deets")
                if (existingButton != null) {
                    logd("Button already exists. Skipping.")
                    return@post
                }

                // Get the first child to use as template (this should work with ViewGroup)
                val exampleButton = if (view is android.view.ViewGroup && view.childCount > 0) {
                    view.getChildAt(0)
                } else {
                    loge("FAILURE: ProfileQuickbarView has no child buttons to use as a template.")
                    return@post
                }

                val grindrContext: Context
                try {
                    grindrContext = GrindrPlus.context.createPackageContext(Constants.GRINDR_PACKAGE_NAME, 0)
                } catch (e: Exception) {
                    loge("FAILURE: Could not create package context. Button cannot be added. Error: ${e.message}")
                    return@post
                }

                val rippleDrawableId = com.grindrplus.ui.Utils.getId("image_button_ripple", "drawable", grindrContext)
                val infoIconId = com.grindrplus.ui.Utils.getId("ic_info", "drawable", grindrContext)

                if (rippleDrawableId == 0 || infoIconId == 0) {
                    loge("FAILURE: Required resources for details button not found.")
                    return@post
                }

                val customDeetsButton = ImageButton(context).apply {
                    layoutParams = exampleButton.layoutParams // Copy layout params from existing button
                    focusable = ImageButton.FOCUSABLE
                    scaleType = ImageView.ScaleType.CENTER
                    isClickable = true
                    tag = "profile_deets"
                    contentDescription = "profile details"
                    setBackgroundResource(rippleDrawableId)
                    setImageResource(infoIconId)
                    setPadding(
                        exampleButton.paddingLeft,
                        exampleButton.paddingTop,
                        exampleButton.paddingRight,
                        exampleButton.paddingBottom
                    )
                    val grindrGray = "#9e9ea8".toColorInt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        drawable.colorFilter = BlendModeColorFilter(grindrGray, BlendMode.SRC_IN)
                    } else {
                        @Suppress("DEPRECATION")
                        drawable.colorFilter = PorterDuffColorFilter(grindrGray, PorterDuff.Mode.SRC_IN)
                    }
                }

                customDeetsButton.setOnClickListener {
                    coroutineScope.launch {
                        try {
                            showProfileDetailsDialog(it.context, profileQuickBarViewInstance)
                        } catch (e: Exception) {
                            loge("Error showing profile details: ${e.message}")
                            Toast.makeText(it.context, "Failed to load details", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Add the button to the quickbar view
                if (view is android.view.ViewGroup) {
                    val desiredPosition = 1
                    if (view.childCount >= desiredPosition) {
                        view.addView(customDeetsButton, desiredPosition)
                    } else {
                        view.addView(customDeetsButton)
                    }
                    logd("SUCCESS: Details button added to the quickbar.")
                } else {
                    loge("FAILURE: ProfileQuickbarView is not a ViewGroup, cannot add button")
                }
            }
        }

        findClass(profileBarView).hook("a", HookStage.AFTER) { param ->
            val profileBarViewInstance = param.thisObject()
            val profileViewState = param.arg<Any>(0)

            try {
                val viewBinding = getObjectField(profileBarViewInstance, "c")
                val displayNameTextView = getObjectField(viewBinding, "c") as TextView
                val profile = getObjectField(profileViewState, "profile") ?: return@hook

                attachProfileDetailsHandlers(displayNameTextView, profile)
            } catch (e: Exception) {
                Logger.e(
                    "Error setting ProfileBarView handlers: ${e.message}",
                    source = LogSource.HOOK
                )
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        // Hook for distance display precision
        findClass(distanceUtils).hook("c", HookStage.AFTER) { param ->
            val distance = param.arg<Double>(0)
            val isFeet = param.arg<Boolean>(2)

            param.setResult(
                if (isFeet) {
                    val feet = (distance * 3.280839895).roundToInt()
                    if (feet < 5280) {
                        String.format("%d feet", feet)
                    } else {
                        String.format("%d miles %d feet", feet / 5280, feet % 5280)
                    }
                } else {
                    val meters = distance.roundToInt()
                    if (meters < 1000) {
                        String.format("%d meters", meters)
                    } else {
                        String.format("%d km %d m", meters / 1000, meters % 1000)
                    }
                }
            )
        }

        // Hook for BMI display in profile
        findClass(profileViewState).hook("getWeight", HookStage.AFTER) { param ->
            if (Config.get("show_bmi_in_profile", true) as Boolean) {
                val weight = param.getResult()
                val height = callMethod(param.thisObject(), "getHeight")

                if (weight != null && height != null) {
                    val BMI = calculateBMI(
                        "kg" in weight.toString(),
                        w2n("kg" in weight.toString(), weight.toString()),
                        h2n("kg" in weight.toString(), height.toString())
                    )
                    if (Config.get("do_gui_safety_checks", true) as Boolean) {
                        if (weight.toString().contains("(")) {
                            logw("BMI details are already present")
                            return@hook
                        }
                    }
                    param.setResult(
                        "$weight - ${String.format("%.1f", BMI)} (${
                            mapOf(
                                "Underweight" to 18.5,
                                "Normal weight" to 24.9,
                                "Overweight" to 29.9,
                                "Obese" to Double.MAX_VALUE
                            ).entries.first { it.value > BMI }.key
                        })"
                    )
                }
            }
        }
    }

    // ADDED: Helper function to attach profile details handlers to any TextView
    private fun attachProfileDetailsHandlers(textView: TextView, sourceObject: Any) {
        try {
            // Extract profile ID from the source object
            val profile = when {
                // If it's a profile object directly
                callMethod(sourceObject, "getProfileId") != null -> sourceObject
                // If it's a fragment or view that contains a profile
                getObjectField(sourceObject, "profile") != null -> getObjectField(sourceObject, "profile")
                // If it's a quickbar view with profileViewState
                getObjectField(sourceObject, "profileViewState") != null ->
                    getObjectField(getObjectField(sourceObject, "profileViewState"), "profile")
                else -> null
            }

            val profileId = profile?.let { callMethod(it, "getProfileId") as? String } ?: run {
                loge("Could not extract profile ID from source object")
                return
            }

            // Click listener to copy profile ID
            textView.setOnClickListener {
                logd("Display name short-clicked for profile ID: $profileId. Attempting to copy ID.")
                copyToClipboard("Profile ID", profileId)
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Copied Profile ID: $profileId")
            }

            // Long-click listener to show hidden details
            textView.setOnLongClickListener { v ->
                try {
                    logd("Display name LONG-clicked for profile ID: $profileId. Attempting to show deets.")
                    showProfileDetailsDialog(v.context, sourceObject)
                    true // Consume the long click
                } catch (e: Exception) {
                    Logger.e(
                        "Error showing profile details: ${e.message}",
                        source = LogSource.HOOK
                    )
                    false
                }
            }

            logd("SUCCESS: Attached profile details handlers to TextView for profile $profileId")
        } catch (e: Exception) {
            loge("Error attaching profile details handlers: ${e.message}")
        }
    }

    // ADDED: Helper function to show profile details dialog
    private fun showProfileDetailsDialog(context: Context, sourceObject: Any) {
        coroutineScope.launch {
            try {
                // Extract profile from various source object types
                val profile = when {
                    // Direct profile object
                    callMethod(sourceObject, "getProfileId") != null -> sourceObject
                    // ProfileViewState object
                    getObjectField(sourceObject, "profile") != null -> getObjectField(sourceObject, "profile")
                    // Quickbar view with profileViewState
                    getObjectField(sourceObject, "profileViewState") != null ->
                        getObjectField(getObjectField(sourceObject, "profileViewState"), "profile")
                    else -> null
                }

                if (profile == null) {
                    Toast.makeText(context, "No profile data available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val profileId = callMethod(profile, "getProfileId") as String
                val accountCreationTime = formatEpochSeconds(
                    GrindrPlus.spline.invert(profileId.toDouble()).toLong()
                )

                val properties = mapOf(
                    "Estimated creation" to accountCreationTime,
                    "Profile ID" to profileId,
                    "Approximate distance" to Utils.safeGetField(
                        profile,
                        "approximateDistance"
                    ) as? Boolean,
                    "Favorite" to Utils.safeGetField(profile, "isFavorite") as? Boolean,
                    "From viewed me" to Utils.safeGetField(
                        profile,
                        "isFromViewedMe"
                    ) as? Boolean,
                    "JWT boosting" to Utils.safeGetField(
                        profile,
                        "isJwtBoosting"
                    ) as? Boolean,
                    "New" to Utils.safeGetField(profile, "isNew") as? Boolean,
                    "Teleporting" to Utils.safeGetField(
                        profile,
                        "isTeleporting"
                    ) as? Boolean,
                    "Online now" to Utils.safeGetField(
                        profile,
                        "onlineNow"
                    ) as? Boolean,
                    "Is roaming" to Utils.safeGetField(
                        profile,
                        "isRoaming"
                    ) as? Boolean,
                    "Found via roam" to Utils.safeGetField(
                        profile,
                        "foundViaRoam"
                    ) as? Boolean,
                    "Is top pick" to Utils.safeGetField(
                        profile,
                        "isTopPick"
                    ) as? Boolean,
                    "Is visiting" to Utils.safeGetField(
                        profile,
                        "isVisiting"
                    ) as? Boolean
                ).filterValues { it != null }

                val detailsText = if (properties.isNotEmpty()) {
                    properties.map { (key, value) -> "• $key: $value" }
                        .joinToString("\n")
                } else {
                    "No hidden details available for this profile"
                }

                AlertDialog.Builder(context)
                    .setTitle("Hidden profile details - $profileId")
                    .setMessage(detailsText)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setNeutralButton("Copy Details") { _, _ ->
                        copyToClipboard("Profile Details", detailsText)
                        GrindrPlus.showToast(
                            Toast.LENGTH_SHORT,
                            "Profile details copied"
                        )
                    }
                    .setNegativeButton("Copy ID") { _, _ ->
                        copyToClipboard("Profile ID", profileId)
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Profile ID copied")
                    }
                    .show()
                    .apply {
                        setOnShowListener {
                            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.WHITE)
                            getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(android.graphics.Color.WHITE)
                            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.WHITE)
                        }
                    }
            } catch (e: Exception) {
                loge("Error showing profile details: ${e.message}")
                Toast.makeText(context, "Failed to load details", Toast.LENGTH_SHORT).show()
            }
        }
    }
}