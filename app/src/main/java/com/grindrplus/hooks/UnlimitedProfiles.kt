package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Utils.openProfile
import com.grindrplus.core.Logger
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.logd
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import java.lang.reflect.Proxy

class UnlimitedProfiles : Hook(
    "Unlimited profiles",
    "Allow unlimited profiles"
) {
    private val function2 = "kotlin.jvm.functions.Function2"
    private val onProfileClicked = "com.grindrapp.android.ui.browse.h\$a"
    private val profileWithPhoto = "com.grindrapp.android.persistence.pojo.ProfileWithPhoto"
    private val serverDrivenCascadeCachedState =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
    private val serverDrivenCascadeCachedProfile =
        "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"
    private val profileTagCascadeFragment = "com.grindrapp.android.ui.tagsearch.ProfileTagCascadeFragment"

    private val cascadeFragment = "com.grindrapp.android.ui.browse.CascadeFragment"
    private val profileRepoClass = "com.grindrapp.android.persistence.repository.ProfileRepo"
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
        findClass(serverDrivenCascadeCachedProfile) // Also force isBlockable to true for paywalled profiles
            .hook("isBlockable", HookStage.BEFORE) { param ->
                param.setResult(true)
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
                            callMethod(profile, "setRemoteUpdatedTime", 0L)
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
            try {
                logd("🔍 Starting profile click interception...")

                val clickHandler = param.thisObject()

                // Get the position from field 'j'
                val positionField = clickHandler.javaClass.getDeclaredField("j")
                positionField.isAccessible = true
                val position = positionField.get(clickHandler) as Int
                logd("📊 Found position: $position")

                // Get the CascadeFragment from field 'k'
                val fragmentField = clickHandler.javaClass.getDeclaredField("k")
                fragmentField.isAccessible = true
                val cascadeFragment = fragmentField.get(clickHandler)
                logd("🏢 CascadeFragment: ${cascadeFragment.javaClass.name}")

                // Get profile ID from position using the fragment
                val profileId = getProfileIdFromPositionAndFragment(position, cascadeFragment)
                if (profileId != null) {
                    logd("🎯 Success! Opening profile: $profileId")
                    openProfile(profileId)
                    param.setResult(null)
                    return@hook
                } else {
                    loge("❌ Could not find profile ID at position $position")
                }

            } catch (t: Throwable) {
                loge("Error in onProfileClicked interceptor: ${t.message}")
                Logger.writeRaw(t.stackTraceToString())
            }
        }

        private fun getProfileIdFromPositionAndFragment(position: Int, fragment: Any): String? {
            return try {
                // Get ViewModel from fragment
                val viewModel = getViewModelFromFragment(fragment)
                if (viewModel != null) {
                    // Get items from ViewModel
                    val items = getItemsFromViewModel(viewModel)
                    if (items != null && position < items.size) {
                        val item = items[position]
                        logd("📦 Item at position $position: ${item.javaClass.name}")
                        return extractProfileIdFromObject(item)
                    } else {
                        loge("❌ No item at position $position (total items: ${items?.size ?: 0})")
                    }
                }
                null
            } catch (e: Exception) {
                loge("Error getting profile from position: ${e.message}")
                null
            }
        }

        // FIXED: Only hook swipe gestures, not all profile interactions
        // This was breaking ProfileDetails.kt click handlers
        /*findClass(onProfileClicked).hook("invokeSuspend", HookStage.BEFORE) { param ->
            try {
                // Find the field that holds the profile data within the click handler's scope.
                val profileDataField = param.thisObject().javaClass.declaredFields
                    .firstOrNull { it.type.name == serverDrivenCascadeCachedProfile }

                if (profileDataField == null) {
                    loge("Could not find ServerDrivenCascadeCachedProfile field in click handler.")
                    return@hook
                }

                profileDataField.isAccessible = true
                val cachedProfile = profileDataField.get(param.thisObject())

                // Extract the profileId directly from this reliable object.
                // The field name can vary slightly, so we try a few common ones.
                val profileId = getObjectField(cachedProfile, "profileId") as? Long
                    ?: getObjectField(cachedProfile, "profileIdLong") as? Long

                if (profileId != null) {
                    logi("Intercepted click for profile ID $profileId. Opening profile manually.")
                    openProfile(profileId.toString())

                    // This is the most important part: prevent the original, crash-inducing method from running.
                    param.setResult(null)
                } else {
                    loge("Successfully intercepted click, but could not extract profileId from cachedProfile object.")
                }
            } catch (t: Throwable) {
                loge("Error in onProfileClicked interceptor: ${t.message}")
                Logger.writeRaw(t.stackTraceToString())
            }
        }*/

  /*      findClass(onProfileClicked).hook("invokeSuspend", HookStage.BEFORE) { param ->
            try {
                logd("🔍 Starting profile click interception...")

                // Method 1: Discover all fields in the click handler
                val clickHandler = param.thisObject()
                val fields = clickHandler.javaClass.declaredFields

                logd("📋 Fields found in click handler:")
                fields.forEach { field ->
                    field.isAccessible = true
                    val fieldName = field.name
                    val fieldType = field.type.name
                    val fieldValue = field.get(clickHandler)

                    logd("   - $fieldName ($fieldType): $fieldValue")

                    // Look for profile-related data
                    if (fieldValue != null) {
                        // Check if this field contains profile ID or profile data
                        when {
                            // Direct profile ID
                            fieldName.contains("profile", ignoreCase = true) &&
                                    fieldValue is String -> {
                                logd("🎯 Found profile ID in field: $fieldName = $fieldValue")
                                openProfile(fieldValue.toString())
                                param.setResult(null)
                                return@hook
                            }

                            // Profile data object - try to extract ID from it
                            fieldType.contains("Profile", ignoreCase = true) -> {
                                logd("🔍 Found profile object in field: $fieldName")
                                val profileId = extractProfileIdFromObject(fieldValue)
                                if (profileId != null) {
                                    logd("🎯 Extracted profile ID: $profileId")
                                    openProfile(profileId)
                                    param.setResult(null)
                                    return@hook
                                }
                            }

                            // Position/index that we can use with ViewModel
                            fieldValue is Int && fieldName.contains("position", ignoreCase = true) -> {
                                logd("📊 Found position in field: $fieldName = $fieldValue")
                                val profileId = getProfileIdFromPosition(fieldValue, clickHandler)
                                if (profileId != null) {
                                    logd("🎯 Got profile ID from position: $profileId")
                                    openProfile(profileId)
                                    param.setResult(null)
                                    return@hook
                                }
                            }
                        }
                    }
                }

                // Method 2: Check the method arguments - FIXED: use args() not args
                logd("📦 Checking method arguments:")
                param.args().forEachIndexed { index, arg ->
                    logd("   Arg $index: $arg (${arg?.javaClass?.name})")

                    if (arg != null) {
                        val profileId = extractProfileIdFromObject(arg)
                        if (profileId != null) {
                            logd("🎯 Found profile ID in argument $index: $profileId")
                            openProfile(profileId)
                            param.setResult(null)
                            return@hook
                        }
                    }
                }

                // Method 3: Check outer class (this$0 field)
                try {
                    val this0Field = clickHandler.javaClass.getDeclaredField("this\$0")
                    this0Field.isAccessible = true
                    val outerInstance = this0Field.get(clickHandler)
                    logd("🏢 Outer instance: ${outerInstance.javaClass.name}")

                    // Try to extract profile data from outer instance
                    val profileId = extractProfileIdFromViewModel(outerInstance)
                    if (profileId != null) {
                        logd("🎯 Found profile ID in outer instance: $profileId")
                        openProfile(profileId)
                        param.setResult(null)
                        return@hook
                    }
                } catch (e: NoSuchFieldException) {
                    logd("No outer instance found")
                }

                loge("❌ Could not find profile data in click handler after exhaustive search")

            } catch (t: Throwable) {
                loge("Error in onProfileClicked interceptor: ${t.message}")
                Logger.writeRaw(t.stackTraceToString())
            }
        }
*/
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

    private fun extractProfileIdFromObject(obj: Any): String? {
        return try {
            // Try common profile ID field names
            val possibleIdFields = listOf("profileId", "id", "profileIdLong", "userId", "uid")

            for (fieldName in possibleIdFields) {
                try {
                    val field = obj.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val value = field.get(obj)
                    if (value != null) {
                        logd("✅ Found profile ID in field '$fieldName': $value")
                        return value.toString()
                    }
                } catch (e: NoSuchFieldException) {
                    logd("Field '$fieldName' not found in ${obj.javaClass.name}")
                    // Continue to next field name
                } catch (e: Exception) {
                    logd("Error accessing field '$fieldName': ${e.message}")
                    // Continue to next field name
                }
            }

            // Try calling getProfileId method
            try {
                val method = obj.javaClass.getMethod("getProfileId")
                val result = method.invoke(obj)
                if (result != null) {
                    logd("✅ Found profile ID via getProfileId(): $result")
                    return result.toString()
                }
            } catch (e: NoSuchMethodException) {
                logd("getProfileId() method not found in ${obj.javaClass.name}")
            } catch (e: Exception) {
                logd("Error calling getProfileId(): ${e.message}")
            }

            null
        } catch (e: Exception) {
            loge("Error in extractProfileIdFromObject: ${e.message}")
            null
        }
    }

    private fun getProfileIdFromPosition(position: Int, clickHandler: Any): String? {
        return try {
            logd("🔍 Looking for profile at position $position")

            // Get the outer instance (usually the Fragment or ViewModel)
            val this0Field = clickHandler.javaClass.getDeclaredField("this\$0")
            this0Field.isAccessible = true
            val outerInstance = this0Field.get(clickHandler)
            logd("🏢 Outer instance type: ${outerInstance.javaClass.name}")

            // Try to get ViewModel from outer instance
            val viewModel = getViewModelFromFragment(outerInstance)
            if (viewModel != null) {
                // Try to get items list from ViewModel
                val items = getItemsFromViewModel(viewModel)
                if (items != null) {
                    logd("📋 Found ${items.size} items in ViewModel")
                    if (position < items.size) {
                        val item = items[position]
                        logd("📦 Item at position $position: ${item.javaClass.name}")
                        return extractProfileIdFromObject(item)
                    } else {
                        loge("❌ Position $position out of bounds (max: ${items.size - 1})")
                    }
                } else {
                    logd("❌ No items list found in ViewModel")
                }
            } else {
                logd("❌ No ViewModel found in outer instance")
            }

            null
        } catch (e: Exception) {
            loge("Error getting profile ID from position: ${e.message}")
            null
        }
    }

    private fun extractProfileIdFromViewModel(viewModel: Any): String? {
        return try {
            // The ViewModel might have a method to get the current profile
            try {
                val method = viewModel.javaClass.getMethod("getCurrentProfileId")
                val result = method.invoke(viewModel)
                if (result != null) {
                    logd("✅ Found profile ID via getCurrentProfileId(): $result")
                    return result.toString()
                }
            } catch (e: NoSuchMethodException) {
                logd("getCurrentProfileId() method not found in ${viewModel.javaClass.name}")
            } catch (e: Exception) {
                logd("Error calling getCurrentProfileId(): ${e.message}")
            }

            null
        } catch (e: Exception) {
            loge("Error in extractProfileIdFromViewModel: ${e.message}")
            null
        }
    }

    private fun getViewModelFromFragment(fragment: Any): Any? {
        return try {
            // Try common ViewModel field names
            val possibleViewModelFields = listOf("viewModel", "vm", "mViewModel", "viewmodel", "L0", "M0")

            for (fieldName in possibleViewModelFields) {
                try {
                    val field = fragment.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val viewModel = field.get(fragment)
                    if (viewModel != null) {
                        logd("✅ Found ViewModel in field '$fieldName': ${viewModel.javaClass.name}")
                        return viewModel
                    } else {
                        logd("Field '$fieldName' exists but is null")
                    }
                } catch (e: NoSuchFieldException) {
                    logd("Field '$fieldName' not found in ${fragment.javaClass.name}")
                    // Continue to next field name
                } catch (e: Exception) {
                    logd("Error accessing field '$fieldName': ${e.message}")
                    // Continue to next field name
                }
            }
            logd("❌ No ViewModel fields found in ${fragment.javaClass.name}")
            null
        } catch (e: Exception) {
            loge("Error in getViewModelFromFragment: ${e.message}")
            null
        }
    }

    private fun getItemsFromViewModel(viewModel: Any): List<Any>? {
        return try {
            // Try common items field names
            val possibleItemsFields = listOf("items", "profiles", "data", "mItems", "itemList", "L0", "M0")

            for (fieldName in possibleItemsFields) {
                try {
                    val field = viewModel.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val items = field.get(viewModel)
                    if (items is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        logd("✅ Found items list in field '$fieldName' with ${items.size} items")
                        return items as List<Any>
                    } else {
                        logd("Field '$fieldName' is not a List (type: ${items?.javaClass?.name})")
                    }
                } catch (e: NoSuchFieldException) {
                    logd("Field '$fieldName' not found in ${viewModel.javaClass.name}")
                    // Continue to next field name
                } catch (e: Exception) {
                    logd("Error accessing field '$fieldName': ${e.message}")
                    // Continue to next field name
                }
            }
            logd("❌ No items list found in ViewModel")
            null
        } catch (e: Exception) {
            loge("Error in getItemsFromViewModel: ${e.message}")
            null
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