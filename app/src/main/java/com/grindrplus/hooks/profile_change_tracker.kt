package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.persistence.model.ProfileSnapshot
import com.grindrplus.persistence.model.ProfilePhotoHistory
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.callMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileChangeTracker : Hook(
    "Profile Change Tracker",
    "Tracks all profile changes and photo updates across accounts"
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val profileModel = "com.grindrapp.android.persistence.model.Profile"
    private val profilePhoto = "com.grindrapp.android.persistence.model.ProfilePhoto"

    override fun init() {
        // Hook profile updates
        findClass(profileModel).hook("setDisplayName", HookStage.AFTER) { param ->
            val profile = param.thisObject()
            captureProfileSnapshot(profile)
        }

        // Hook when profiles are loaded/updated
        findClass("com.grindrapp.android.persistence.dao.ProfileDao")
            .hook("upsertProfile", HookStage.AFTER) { param ->
                if (param.args().isNotEmpty()) {
                    val profile = param.arg<Any>(0)
                    captureProfileSnapshot(profile)
                }
            }

        // Hook photo changes
        findClass("com.grindrapp.android.persistence.dao.ProfilePhotoDao")
            .hook("upsertProfilePhoto", HookStage.AFTER) { param ->
                if (param.args().isNotEmpty()) {
                    val photo = param.arg<Any>(0)
                    capturePhotoHistory(photo)
                }
            }
    }

    private fun captureProfileSnapshot(profile: Any) {
        scope.launch {
            try {
                val profileId = getObjectField(profile, "profileId") as? String ?: return@launch
                val displayName = getObjectField(profile, "displayName") as? String
                val aboutMe = getObjectField(profile, "aboutMe") as? String
                val headline = getObjectField(profile, "headline") as? String
                val age = getObjectField(profile, "age") as? Int
                val height = getObjectField(profile, "height") as? String
                val weight = getObjectField(profile, "weight") as? String
                val bodyType = getObjectField(profile, "bodyType") as? String
                val ethnicity = getObjectField(profile, "ethnicity") as? String

                // Convert lists to JSON strings
                val tribes = try {
                    val tribesList = getObjectField(profile, "tribes") as? List<*>
                    org.json.JSONArray(tribesList ?: emptyList<String>()).toString()
                } catch (e: Exception) { null }

                val lookingFor = try {
                    val lookingForList = getObjectField(profile, "lookingFor") as? List<*>
                    org.json.JSONArray(lookingForList ?: emptyList<String>()).toString()
                } catch (e: Exception) { null }

                val snapshot = ProfileSnapshot(
                    profileId = profileId,
                    displayName = displayName,
                    aboutMe = aboutMe,
                    headline = headline,
                    age = age,
                    height = height,
                    weight = weight,
                    bodyType = bodyType,
                    ethnicity = ethnicity,
                    tribes = tribes,
                    lookingFor = lookingFor,
                    accountPackage = GrindrPlus.packageName
                )

                // Check if profile has changed before saving
                val dao = GrindrPlus.database.profileTrackingDao()
                val latest = dao.getLatestSnapshot(profileId)
                
                if (latest == null || hasProfileChanged(latest, snapshot)) {
                    dao.insertSnapshot(snapshot)
                    logi("Captured profile snapshot for $profileId")
                    
                    // Record profile sighting
                    GrindrPlus.database.profileAccountMappingDao()
                        .recordProfileSighting(profileId, GrindrPlus.packageName)
                }
            } catch (e: Exception) {
                loge("Failed to capture profile snapshot: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    private fun capturePhotoHistory(photo: Any) {
        scope.launch {
            try {
                val profileId = getObjectField(photo, "profileId") as? String ?: return@launch
                val photoHash = getObjectField(photo, "photoHash") as? String ?: return@launch
                val photoUrl = getObjectField(photo, "photoUrl") as? String
                val isPrimary = try {
                    callMethod(photo, "isPrimary") as? Boolean ?: false
                } catch (e: Exception) { false }

                val photoHistory = ProfilePhotoHistory(
                    profileId = profileId,
                    photoHash = photoHash,
                    photoUrl = photoUrl,
                    localPath = null, // Will be set when saved permanently
                    isPrimary = isPrimary,
                    accountPackage = GrindrPlus.packageName
                )

                GrindrPlus.database.profileTrackingDao().insertPhotoHistory(photoHistory)
                logi("Captured photo history for $profileId (hash: $photoHash)")
            } catch (e: Exception) {
                loge("Failed to capture photo history: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    private fun hasProfileChanged(old: ProfileSnapshot, new: ProfileSnapshot): Boolean {
        return old.displayName != new.displayName ||
                old.aboutMe != new.aboutMe ||
                old.headline != new.headline ||
                old.age != new.age ||
                old.height != new.height ||
                old.weight != new.weight ||
                old.bodyType != new.bodyType ||
                old.ethnicity != new.ethnicity ||
                old.tribes != new.tribes ||
                old.lookingFor != new.lookingFor
    }
}
