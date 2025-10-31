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
                val allItems = param.getResult() as List<*>

                // Filter to only profile items
                val profileItems = allItems.filter {
                    it?.javaClass?.name == serverDrivenCascadeCachedProfile
                }

                // Apply custom filtering if enabled
                val filteredItems = if (Config.get("custom_filtering_enabled", false) as Boolean) {
                    applyCustomFilters(profileItems)
                } else {
                    profileItems
                }

                param.setResult(filteredItems)
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
    }

    private fun applyCustomFilters(profiles: List<Any?>): List<Any?> {
        return profiles.filter { profileItem ->
            try {
                if (profileItem == null) return@filter false

                // Get the data object from cascade item
                val profileData = try {
                    getObjectField(profileItem, "data")
                } catch (e: Exception) {
                    profileItem // If no "data" field, assume profileItem IS the data
                }

                // Filter 1: Maximum Distance
                val maxDistance = Config.get("filter_max_distance", 0) as Int
                if (maxDistance > 0) {
                    val distanceMeters = (getObjectField(profileData, "distanceMeters") as? Int)
                        ?: (getObjectField(profileData, "distance") as? Double)?.let { (it * 1000).toInt() }
                    if (distanceMeters != null && distanceMeters > maxDistance) {
                        return@filter false
                    }
                }

                // Filter 2: Favorites Only
                val favoritesOnly = Config.get("filter_favorites_only", false) as Boolean
                if (favoritesOnly) {
                    val isFavorite = getObjectField(profileData, "isFavorite") as? Boolean
                    if (isFavorite != true) {
                        return@filter false
                    }
                }

                // Filter 3: Gender
                val genderFilter = Config.get("filter_gender", 0) as Int
                if (genderFilter > 0) {
                    val genders = getObjectField(profileData, "genders") as? List<*>
                    if (genders == null || genders.isEmpty() || !genders.any {
                            (it as? Int) == genderFilter
                        }) {
                        return@filter false
                    }
                }

                // Filter 4: Tribe
                val tribeFilter = Config.get("filter_tribe", 0) as Int
                if (tribeFilter > 0) {
                    // Try both field names (cascade uses "tribes", v7 uses "grindrTribes")
                    val tribes = getObjectField(profileData, "tribes") as? List<*>
                        ?: getObjectField(profileData, "grindrTribes") as? List<*>
                    if (tribes == null || !tribes.any {
                            (it as? Int) == tribeFilter
                        }) {
                        return@filter false
                    }
                }

                // Filter 5: Ethnicity (single value, not array!)
                val ethnicityFilter = Config.get("filter_ethnicity", "0") as String
                if (ethnicityFilter != "0") {
                    val filterMode = Config.get("filter_ethnicity_mode", "include") as String
                    val targetEthnicities = ethnicityFilter.split(",")
                        .mapNotNull { it.toIntOrNull() }
                        .toSet()

                    // Ethnicity is a single Int, not an array
                    val profileEthnicity = getObjectField(profileData, "ethnicity") as? Int

                    when (filterMode) {
                        "include" -> {
                            // Show only if profile ethnicity matches one of the target ethnicities
                            if (profileEthnicity == null || profileEthnicity !in targetEthnicities) {
                                return@filter false
                            }
                        }
                        "exclude" -> {
                            // Hide if profile ethnicity matches any target ethnicity
                            if (profileEthnicity != null && profileEthnicity in targetEthnicities) {
                                return@filter false
                            }
                        }
                    }
                }

                // Filter 6: Social Networks
                val requireSocial = Config.get("filter_has_social_networks", false) as Boolean
                if (requireSocial) {
                    val socialNetworks = getObjectField(profileData, "socialNetworks")
                    if (socialNetworks == null) {
                        return@filter false
                    }
                    // Check if socialNetworks is not empty (can be array or map)
                    val hasSocial = when (socialNetworks) {
                        is List<*> -> socialNetworks.isNotEmpty()
                        is Map<*, *> -> socialNetworks.isNotEmpty()
                        else -> false
                    }
                    if (!hasSocial) {
                        return@filter false
                    }
                }

                // Profile passed all filters
                true
            } catch (e: Exception) {
                // If there's an error accessing fields, keep the profile by default
                Logger.e("Error filtering profile: ${e.message}")
                true
            }
        }
    }
}