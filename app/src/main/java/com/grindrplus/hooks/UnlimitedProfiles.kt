package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Utils.openProfile
import com.grindrplus.core.Logger
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import java.lang.reflect.Proxy

class UnlimitedProfiles : Hook(
    "Unlimited profiles",
    "Allow unlimited profiles"
) {
    private val function2 = "kotlin.jvm.functions.Function2"
    private val onProfileClicked = "com.grindrapp.android.ui.browse.E"
    private val profileWithPhoto = "com.grindrapp.android.persistence.pojo.ProfileWithPhoto"
    private val serverDrivenCascadeCachedState =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
    private val serverDrivenCascadeCachedProfile =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"
    private val profileTagCascadeFragment = "com.grindrapp.android.ui.tagsearch.ProfileTagCascadeFragment"

    override fun init() {
        findClass(serverDrivenCascadeCachedState)
            .hook("getItems", HookStage.AFTER) { param ->
                val items = (param.getResult() as List<*>).filter {
                    it?.javaClass?.name == serverDrivenCascadeCachedProfile
                }

                param.setResult(items)
            }

        findClass(profileTagCascadeFragment)
            .hook("R", HookStage.BEFORE) { param ->
                param.setResult(true)
            }

        findClass(serverDrivenCascadeCachedProfile)
            .hook("getUpsellType", HookStage.BEFORE) { param ->
                param.setResult(null)
            }

        val profileClass = findClass("com.grindrapp.android.persistence.model.Profile")
        val profileWithPhotoClass = findClass(profileWithPhoto)
        val function2Class = findClass(function2)
        val flowKtClass = findClass("kotlinx.coroutines.flow.FlowKt")
        val profileRepoClass = findClass("com.grindrapp.android.persistence.repository.ProfileRepo")

        profileRepoClass.hook("getProfilesWithPhotosFlow", HookStage.AFTER) { param ->
            val requestedProfileIds = param.arg<List<String>>(0)
            if (requestedProfileIds.isEmpty()) return@hook

            val originalFlow = param.getResult()
            val profileWithPhotoConstructor = profileWithPhotoClass
                .getConstructor(profileClass, List::class.java)
            val profileConstructor = profileClass.getConstructor()

            val proxy = Proxy.newProxyInstance(
                GrindrPlus.classLoader,
                arrayOf(function2Class)
            ) { _, _, args ->
                @Suppress("UNCHECKED_CAST")
                val profilesWithPhoto = args[0] as List<Any>

                if (requestedProfileIds.size > profilesWithPhoto.size) {
                    val profileIds = ArrayList<String>(profilesWithPhoto.size)

                    for (profileWithPhoto in profilesWithPhoto) {
                        val profile = callMethod(profileWithPhoto, "getProfile")
                        profileIds.add(callMethod(profile, "getProfileId") as String)
                    }

                    val profileIdSet = profileIds.toHashSet()

                    val missingProfiles = ArrayList<Any>()
                    for (profileId in requestedProfileIds) {
                        if (profileId !in profileIdSet) {
                            val profile = profileConstructor.newInstance()
                            callMethod(profile, "setProfileId", profileId)
                            callMethod(profile, "setRemoteUpdatedTime", 1L)
                            callMethod(profile, "setLocalUpdatedTime", 0L)
                            missingProfiles.add(
                                profileWithPhotoConstructor.newInstance(profile, emptyList<Any>())
                            )
                        }
                    }

                    if (missingProfiles.isNotEmpty()) {
                        val result = ArrayList<Any>(profilesWithPhoto.size + missingProfiles.size)
                        result.addAll(profilesWithPhoto)
                        result.addAll(missingProfiles)
                        return@newProxyInstance result
                    }
                }

                profilesWithPhoto
            }

            val transformedFlow = callStaticMethod(flowKtClass, "mapLatest", originalFlow, proxy)
            param.setResult(transformedFlow)
        }

        // FIXED: Only hook swipe gestures, not all profile interactions
        // This was breaking ProfileDetails.kt click handlers
        findClass(onProfileClicked).hook("invokeSuspend", HookStage.BEFORE) { param ->
            if (Config.get("disable_profile_swipe", false) as Boolean) {
                getObjectField(param.thisObject(), param.thisObject().javaClass.declaredFields
                    .firstOrNull { it.type.name.contains("ServerDrivenCascadeCachedProfile") }?.name
                )?.let { cachedProfile ->
                    runCatching { getObjectField(cachedProfile, "profileIdLong").toString() }
                        .onSuccess { profileId ->
                            openProfile(profileId)
                            param.setResult(null)
                        }
                        .onFailure { loge("Profile ID not found in cached profile") }
                }
            }
        }


        // Apply custom filters if enabled
        findClass(serverDrivenCascadeCachedState)
            .hook("getItems", HookStage.AFTER) { param ->
                if (!(Config.get("custom_filtering_enabled", false) as Boolean)) {
                    return@hook
                }

                val items = param.getResult() as? List<*> ?: return@hook
                val filteredItems = applyCustomFilters(items)

                if (filteredItems.size != items.size) {
                    Logger.d("Filtered ${items.size - filteredItems.size} profiles")
                }

                param.setResult(filteredItems)
            }
    }

    private fun safeGetField(obj: Any?, fieldName: String): Any? {
        return try {
            if (obj == null) return null
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyCustomFilters(profiles: List<Any?>): List<Any?> {
        val maxDistance = Config.get("filter_max_distance", 0) as Int
        val favoritesOnly = Config.get("filter_favorites_only", false) as Boolean
        val genderFilter = Config.get("filter_gender", 0) as Int
        val tribeFilter = Config.get("filter_tribe", 0) as Int
        val ethnicityFilter = Config.get("filter_ethnicity", "0") as String
        val ethnicityMode = Config.get("filter_ethnicity_mode", "include") as String
        val requireSocial = Config.get("filter_has_social_networks", false) as Boolean
        val tagsFilter = Config.get("filter_tags", "") as String
        val tagsMode = Config.get("filter_tags_mode", "include") as String
        val ageMin = Config.get("filter_age_min", 0) as Int
        val ageMax = Config.get("filter_age_max", 0) as Int
        val includeNoAge = Config.get("filter_age_include_no_age", true) as Boolean
        val aboutText = Config.get("filter_about_text", "") as String
        val aboutMode = Config.get("filter_about_mode", "include") as String

        val targetTags = if (tagsFilter.isNotEmpty()) {
            tagsFilter.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        } else {
            emptySet()
        }

        return profiles.filter { profileItem ->
            if (profileItem == null) return@filter false

            try {
                val profileId = safeGetField(profileItem, "profileIdLong")?.toString() ?: "unknown"

                // Filter 1: Maximum Distance
                if (maxDistance > 0) {
                    val distanceMeters = (safeGetField(profileItem, "distanceMeters") as? Double)?.toInt()
                    if (distanceMeters != null && distanceMeters > maxDistance) {
                        return@filter false
                    }
                }

                // Filter 2: Favorites Only
                if (favoritesOnly) {
                    val isFavorite = safeGetField(profileItem, "isFavorite") as? Boolean
                    if (isFavorite != true) {
                        return@filter false
                    }
                }

                // Filter 3: Gender
                if (genderFilter > 0) {
                    @Suppress("UNCHECKED_CAST")
                    val genders = safeGetField(profileItem, "genders") as? List<Int>
                    val hasMatchingGender = genders?.contains(genderFilter) == true
                    if (!hasMatchingGender) {
                        return@filter false
                    }
                }

                // Filter 4: Tribe
                if (tribeFilter > 0) {
                    @Suppress("UNCHECKED_CAST")
                    val tribes = safeGetField(profileItem, "tribes") as? List<Int>
                    val hasMatchingTribe = tribes?.contains(tribeFilter) == true
                    if (!hasMatchingTribe) {
                        return@filter false
                    }
                }

                // Filter 5: Ethnicity
                if (ethnicityFilter != "0") {
                    val targetEthnicities = ethnicityFilter.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()

                    if (targetEthnicities.isNotEmpty()) {
                        val profileEthnicity = safeGetField(profileItem, "ethnicity") as? Int
                        val matches = profileEthnicity != null && profileEthnicity in targetEthnicities

                        when (ethnicityMode) {
                            "include" -> if (!matches) return@filter false
                            "exclude" -> if (matches) return@filter false
                        }
                    }
                }

                // Filter 6: Social Networks
                if (requireSocial) {
                    @Suppress("UNCHECKED_CAST")
                    val socialNetworks = safeGetField(profileItem, "socialNetworks") as? List<*>
                    if (socialNetworks.isNullOrEmpty()) {
                        return@filter false
                    }
                }

                // Filter 7: About Me Text
                if (aboutText.isNotEmpty()) {
                    val aboutMe = safeGetField(profileItem, "aboutMe") as? String ?: ""
                    val containsText = aboutMe.contains(aboutText, ignoreCase = true)

                    when (aboutMode) {
                        "include" -> if (!containsText) return@filter false
                        "exclude" -> if (containsText) return@filter false
                    }
                }

                // Filter 8: Tags
                if (targetTags.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    val profileTags = safeGetField(profileItem, "tags") as? List<String>
                    val profileTagSet = profileTags?.map { it.lowercase() }?.toSet() ?: emptySet()

                    val hasMatchingTag = targetTags.any { targetTag ->
                        profileTagSet.any { profileTag -> profileTag.contains(targetTag) }
                    }

                    when (tagsMode) {
                        "include" -> if (!hasMatchingTag) return@filter false
                        "exclude" -> if (hasMatchingTag) return@filter false
                    }
                }

                // Filter 9: Age
                if (ageMin > 0 || ageMax > 0) {
                    val profileAge = safeGetField(profileItem, "age") as? Int

                    if (profileAge == null) {
                        if (!includeNoAge) {
                            return@filter false
                        }
                    } else {
                        val ageInRange = when {
                            ageMin == ageMax -> profileAge == ageMin
                            else -> profileAge in ageMin..ageMax
                        }

                        if (!ageInRange) {
                            return@filter false
                        }
                    }
                }

                true

            } catch (e: Exception) {
                Logger.e("Error filtering profile: ${e.message}")
                true
            }
        }
    }
}