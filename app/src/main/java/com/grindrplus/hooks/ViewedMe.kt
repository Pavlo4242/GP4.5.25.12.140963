package com.grindrplus.hooks

import com.grindrplus.core.Logger
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
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

    // Helper to safely get the hash, handling different naming conventions
    private fun getMediaHash(profileObj: Any): String? {
        return try {
            // Try the name derived from the DB schema (media_hash -> getMediaHash)
            callMethod(profileObj, "getMediaHash") as? String
        } catch (e: NoSuchMethodError) {
            try {
                // Try the legacy name
                callMethod(profileObj, "getProfileImageMediaHash") as? String
            } catch (e2: NoSuchMethodError) {
                // Try accessing the field directly
                try {
                    getObjectField(profileObj, "mediaHash") as? String
                } catch (e3: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun init() {

        // -------------------------------------------------------------------------
        // 1. CACHE BUILDER & INJECTOR
        // -------------------------------------------------------------------------

        // Hook setProfileId to associate the current ID with the current Hash
        findClass(profileClass).hook("setProfileId", HookStage.AFTER) { param ->
            val profile = param.thisObject()
            val id = param.argNullable<Any>(0)?.toString() ?: return@hook

            val hash = getMediaHash(profile)
            if (!hash.isNullOrEmpty()) {
                idCache[hash] = id
            }
        }

        // Hook setMediaHash (or legacy setProfileImageMediaHash) to associate Hash with ID
        // We hook both potential names to be safe.
        val methods = try {
            findClass(profileClass).declaredMethods.map { it.name }
        } catch (e: Exception) { emptyList<String>() }

        val setterName = if (methods.contains("setMediaHash")) "setMediaHash" else "setProfileImageMediaHash"

        findClass(profileClass).hook(setterName, HookStage.AFTER) { param ->
            val profile = param.thisObject()
            val hash = param.argNullable<String>(0) ?: return@hook

            val currentId = try {
                callMethod(profile, "getProfileId") as? String
            } catch (e: Exception) { null }

            if (currentId != null) {
                idCache[hash] = currentId
            } else {
                val cachedId = idCache[hash]
                if (cachedId != null) {
                    Logger.i("ViewedMe: Resolved hidden profile! Hash: ${hash.take(8)}... -> ID: $cachedId")
                    callMethod(profile, "setProfileId", cachedId)
                }
            }
        }

        // -------------------------------------------------------------------------
        // 2. UNCAP DISPLAY LIMITS
        // -------------------------------------------------------------------------

        try {
            findClass(profileClass).hook("setViewedCountMax", HookStage.BEFORE) { param ->
                if (param.args().isNotEmpty()) {
                    param.args()[0] = 99999
                }
            }
        } catch (e: Throwable) {
            Logger.e("Could not hook setViewedCountMax: ${e.message}")
        }

        // -------------------------------------------------------------------------
        // 3. LOGGING FOR DEBUG
        // -------------------------------------------------------------------------
        findClass(profileClass).hook("setLastViewed", HookStage.AFTER) { param ->
            val profile = param.thisObject()

            // FIX: Use argNullable to prevent NullPointerException if DB passes null
            val time = param.argNullable<Long>(0) ?: 0L

            if (time > 0) {
                val id = try { callMethod(profile, "getProfileId") } catch(e:Exception) { null }
                val hash = getMediaHash(profile)

                if (id != null) {
                    Logger.i("ViewedMe Entry: ID=$id Hash=${hash?.toString()?.take(8)}")
                } else {
                    Logger.w("ViewedMe Hidden Entry: Hash=${hash?.toString()?.take(8)} (Not in cache yet)")
                }
            }
        }
    }
}