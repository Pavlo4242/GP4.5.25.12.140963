package com.grindrplus.hooks

import com.grindrplus.core.Logger
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import java.util.concurrent.ConcurrentHashMap

class ViewedMe : Hook(
    "Viewed Me Enhancer",
    "Uncap views, reveal hidden profiles via cache, and capture eyeball data"
) {
    private val profileClass = "com.grindrapp.android.persistence.model.Profile"

    // Global cache to map MediaHashes to ProfileIDs
    companion object {
        val idCache = ConcurrentHashMap<String, String>()
    }

    override fun init() {

        // -------------------------------------------------------------------------
        // 1. CACHE BUILDER & INJECTOR
        // -------------------------------------------------------------------------

        // When the app sets a Profile ID, we check if we already have a Hash for this object
        // and update our cache.
        findClass(profileClass).hook("setProfileId", HookStage.AFTER) { param ->
            val profile = param.thisObject()
            val id = param.arg<Any>(0)?.toString() ?: return@hook

            // Try to get the hash from the object
            val hash = callMethod(profile, "getProfileImageMediaHash") as? String
            if (!hash.isNullOrEmpty()) {
                idCache[hash] = id
            }
        }

        // When the app sets a Media Hash (this happens during JSON parsing of Viewed Me list),
        // we do two things:
        // A. If we already know the ID (from this object), update the cache.
        // B. If the ID is MISSING (which happens in Viewed Me Previews), try to find it in cache and INJECT IT.
        findClass(profileClass).hook("setProfileImageMediaHash", HookStage.AFTER) { param ->
            val profile = param.thisObject()
            val hash = param.arg<String>(0) ?: return@hook

            val currentId = try {
                callMethod(profile, "getProfileId") as? String
            } catch (e: Exception) { null }

            if (currentId != null) {
                // A. Normal case: We have both. Update cache.
                idCache[hash] = currentId
            } else {
                // B. Hidden case (Viewed Me Preview): We have hash, but no ID.
                val cachedId = idCache[hash]
                if (cachedId != null) {
                    Logger.i("ViewedMe: Resolved hidden profile! Hash: ${hash.take(8)}... -> ID: $cachedId")

                    // INJECT THE ID!
                    // This tricks the Profile object into thinking it has the ID,
                    // allowing the UI (and your tracker) to treat it as a full profile.
                    callMethod(profile, "setProfileId", cachedId)
                }
            }
        }

        // -------------------------------------------------------------------------
        // 2. UNCAP DISPLAY LIMITS
        // -------------------------------------------------------------------------

        try {
            // The JSON has "viewedCount": { "maxDisplayCount": 3 }
            // We intercept this setter and force it to 99999.
            findClass(profileClass).hook("setViewedCountMax", HookStage.BEFORE) { param ->
                // param.args() returns the arguments array. We modify the first argument.
                param.args()[0] = 99999
            }
        } catch (e: Throwable) {
            Logger.e("Could not hook setViewedCountMax directly: ${e.message}")
        }

        // -------------------------------------------------------------------------
        // 3. LOGGING FOR DEBUG
        // -------------------------------------------------------------------------
        findClass(profileClass).hook("setLastViewed", HookStage.AFTER) { param ->
            val profile = param.thisObject()
            val time = param.arg<Long>(0) // Safe cast using helper

            if (time > 0) {
                val id = callMethod(profile, "getProfileId")
                val hash = callMethod(profile, "getProfileImageMediaHash")

                if (id != null) {
                    Logger.i("ViewedMe Entry: ID=$id Hash=${hash?.toString()?.take(8)}")
                } else {
                    Logger.w("ViewedMe Hidden Entry: Hash=${hash?.toString()?.take(8)} (Not in cache yet)")
                }
            }
        }
    }
}