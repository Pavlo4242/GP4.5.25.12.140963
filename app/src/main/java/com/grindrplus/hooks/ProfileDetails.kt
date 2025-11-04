package com.grindrplus.hooks

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.core.Utils
import com.grindrplus.core.Utils.calculateBMI
import com.grindrplus.core.Utils.h2n
import com.grindrplus.core.Utils.w2n
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
import java.util.ArrayList
import kotlin.math.roundToInt

class ProfileDetails : Hook("Profile details", "Add extra fields and details to profiles") {
    private var boostedProfilesList = emptyList<String>()
    private val blockedProfilesObserver = "cf.p"
    private val profileViewHolder = "cf.F\$b"
    private val distanceUtils = "com.grindrapp.android.utils.DistanceUtils"
    private val profileBarView = "com.grindrapp.android.ui.profileV2.ProfileBarView"
    private val profileViewState = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
    private val serverDrivenCascadeCachedState =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
    private val serverDrivenCascadeCachedProfile =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"

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
                getObjectField(param.thisObject(), "a"), "o") as ArrayList<*>
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
        // REVISED HOOK: Attach listeners in the data binding method for reliability.
        // This method is called whenever the profile bar is updated with new data.
        findClass(profileBarView).hook("a", HookStage.AFTER) { param ->
            val profileBarViewInstance = param.thisObject()
            val profileViewState = param.arg<Any>(0) // The ProfileViewState object passed to the method

            try {
                val viewBinding = getObjectField(profileBarViewInstance, "c")
                val displayNameTextView = getObjectField(viewBinding, "c") as TextView
                val profile = getObjectField(profileViewState, "profile") ?: return@hook
                val profileId = callMethod(profile, "getProfileId") as String

                // ADDED: Click listener to copy profile ID
                displayNameTextView.setOnClickListener {
                    copyToClipboard("Profile ID", profileId)
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Copied Profile ID: $profileId")
                }

                // REVISED: Long-click listener to show hidden details
                displayNameTextView.setOnLongClickListener { v ->
                    try {
                        val accountCreationTime =
                            formatEpochSeconds(GrindrPlus.spline.invert(profileId.toDouble()).toLong())

                        val properties = mapOf(
                            "Estimated creation" to accountCreationTime,
                            "Profile ID" to profileId,
                            "Approximate distance" to Utils.safeGetField(profile, "approximateDistance") as? Boolean,
                            "Favorite" to Utils.safeGetField(profile, "isFavorite") as? Boolean,
                            "From viewed me" to Utils.safeGetField(profile, "isFromViewedMe") as? Boolean,
                            "JWT boosting" to Utils.safeGetField(profile, "isJwtBoosting") as? Boolean,
                            "New" to Utils.safeGetField(profile, "isNew") as? Boolean,
                            "Teleporting" to Utils.safeGetField(profile, "isTeleporting") as? Boolean,
                            "Online now" to Utils.safeGetField(profile, "onlineNow") as? Boolean,
                            "Is roaming" to Utils.safeGetField(profile, "isRoaming") as? Boolean,
                            "Found via roam" to Utils.safeGetField(profile, "foundViaRoam") as? Boolean,
                            "Is top pick" to Utils.safeGetField(profile, "isTopPick") as? Boolean,
                            "Is visiting" to Utils.safeGetField(profile, "isVisiting") as? Boolean
                        ).filterValues { it != null }

                        val detailsText = if (properties.isNotEmpty()) {
                            properties.map { (key, value) -> "• $key: $value" }.joinToString("\n")
                        } else {
                            "No hidden details available for this profile"
                        }

                        AlertDialog.Builder(v.context)
                            .setTitle("Hidden profile details - $profileId")
                            .setMessage(detailsText)
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .setNeutralButton("Copy Details") { _, _ ->
                                copyToClipboard("Profile Details", detailsText)
                                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Profile details copied")
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
                        true // Consume the long click
                    } catch (e: Exception) {
                        Logger.e("Error showing profile details: ${e.message}", source = LogSource.HOOK)
                        false
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error setting ProfileBarView handlers: ${e.message}", source = LogSource.HOOK)
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
}