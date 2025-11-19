package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.persistence.model.ProfileViewEntity
import com.grindrplus.utils.Hook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ProfileViewsTracker : Hook("Profile views tracker", "Track who viewed your profile") {

    data class ViewedProfileInfo(
        val profileId: String,
        val timestamp: Long,
        val displayName: String? = null,
        val distance: Double? = null,
        val mediaHash: String? = null,
        val source: String
    )

    companion object {
        private fun getDao() = GrindrPlus.database.profileViewDao()

        suspend fun getRecentViewedProfiles(limit: Int = 10): List<ViewedProfileInfo> {
            return getDao().getMostRecentViews(limit).map {
                ViewedProfileInfo(it.profileId, it.viewedAt, it.displayName, it.distance, it.mediaHash, it.source)
            }
        }

        suspend fun getViewedProfilesCount(): Int = getDao().getCount()

        suspend fun clearViewedProfiles() {
            getDao().clearAll()
            Logger.i("Cleared profile views history", LogSource.MODULE)
        }

        suspend fun refreshProfileViews() {
            if (!(Config.get("track_profile_views", true) as Boolean)) return
            Logger.d("Refreshing profile views from API...", LogSource.MODULE)
            try {
                val endpoints = listOf(
                    "https://grindr.mobi/v7/views/list",
                    "https://grindr.mobi/v6/views/list",
                    "https://grindr.mobi/v5/views/list"
                )

                var success = false
                for (endpoint in endpoints) {
                    try {
                        val response = GrindrPlus.httpClient.sendRequest(endpoint, "GET")
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string()
                            if (!bodyString.isNullOrEmpty()) {
                                processViewsListResponse(bodyString, endpoint)
                                success = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w("Endpoint $endpoint error: ${e.message}", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error refreshing profile views: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun processViewsListResponse(bodyString: String, endpoint: String) {
            try {
                val jsonResponse = JSONObject(bodyString)

                val profiles = jsonResponse.optJSONArray("profiles")
                if (profiles != null) {
                    for (i in 0 until profiles.length()) {
                        processProfileView(profiles.getJSONObject(i), "v7_full")
                    }
                }

                val previews = jsonResponse.optJSONArray("previews")
                if (previews != null) {
                    for (i in 0 until previews.length()) {
                        processPreviewView(previews.getJSONObject(i), "v7_preview")
                    }
                }

                crossReferenceUnresolvedProfiles()

            } catch (e: Exception) {
                Logger.e("Error processing views list: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun processProfileView(profile: JSONObject, source: String) {
            try {
                val profileId = profile.optString("profileId").takeIf { it.isNotEmpty() } ?: return
                val lastViewed = profile.optLong("lastViewed", System.currentTimeMillis())
                var displayName = profile.optString("displayName").takeIf { it.isNotEmpty() }
                val distance = profile.optDouble("distance").takeIf { !it.isNaN() && it > 0 }
                val mediaHash = profile.optString("profileImageMediaHash").takeIf { it.isNotEmpty() }

                if (displayName == null) {
                    val enhancedInfo = getEnhancedProfileInfo(profileId)
                    displayName = enhancedInfo?.displayName
                }

                val entity = ProfileViewEntity(
                    profileId = profileId,
                    viewedAt = lastViewed,
                    displayName = displayName,
                    distance = distance,
                    mediaHash = mediaHash,
                    source = source
                )
                upsertView(entity)
            } catch (e: Exception) {
                Logger.e("Error processing profile view: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun processPreviewView(preview: JSONObject, source: String) {
            try {
                val mediaHash = preview.optString("profileImageMediaHash").takeIf { it.isNotEmpty() } ?: return
                val lastViewed = preview.optLong("lastViewed", System.currentTimeMillis())
                val distance = preview.optDouble("distance").takeIf { !it.isNaN() && it > 0 }

                val profileInfo = resolveMediaHashToProfileInfo(mediaHash)

                if (profileInfo != null) {
                    val entity = ProfileViewEntity(
                        profileId = profileInfo.profileId,
                        viewedAt = lastViewed,
                        displayName = profileInfo.displayName,
                        distance = distance,
                        mediaHash = mediaHash,
                        source = source
                    )
                    upsertView(entity)
                    Logger.i("Identified hidden viewer: ${profileInfo.displayName} (${profileInfo.profileId})", LogSource.MODULE)
                } else {
                    val placeholderEntity = ProfileViewEntity(
                        profileId = "unresolved_${mediaHash.take(8)}",
                        viewedAt = lastViewed,
                        displayName = "Locked Profile",
                        distance = distance,
                        mediaHash = mediaHash,
                        source = "${source}_unresolved"
                    )
                    upsertView(placeholderEntity)
                }

            } catch (e: Exception) {
                Logger.e("Error processing preview view: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun crossReferenceUnresolvedProfiles() {
            try {
                val unresolvedViews = getDao().getUnresolvedViews()
                if (unresolvedViews.isEmpty()) return

                for (view in unresolvedViews) {
                    val mediaHash = view.mediaHash ?: continue
                    val profileInfo = resolveMediaHashToProfileInfo(mediaHash)

                    if (profileInfo != null) {
                        val resolvedEntity = ProfileViewEntity(
                            profileId = profileInfo.profileId,
                            viewedAt = view.viewedAt,
                            displayName = profileInfo.displayName,
                            distance = view.distance,
                            mediaHash = mediaHash,
                            source = view.source.replace("_unresolved", "_resolved")
                        )
                        getDao().delete(view)
                        getDao().upsert(resolvedEntity)
                        Logger.i("Resolved previously unknown viewer: ${profileInfo.displayName}", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error cross-referencing: ${e.message}", LogSource.MODULE)
            }
        }

        private data class ProfileInfo(
            val profileId: String,
            val displayName: String?,
            val mediaHash: String?
        )

        private suspend fun resolveMediaHashToProfileInfo(mediaHash: String): ProfileInfo? = withContext(Dispatchers.IO) {
            try {
                var profileId: String? = null

                // 1. Try ViewedMe Cache (fastest)
                profileId = ViewedMe.idCache[mediaHash]

                // 2. Try 'profile' table (column media_hash)
                if (profileId == null) {
                    val result = DatabaseHelper.query(
                        "SELECT profile_id FROM profile WHERE media_hash = ?",
                        arrayOf(mediaHash)
                    ).firstOrNull()
                    profileId = result?.get("profile_id") as? String
                }

                // 3. Try 'profile_photo' table (FIXED COLUMN NAMES)
                if (profileId == null) {
                    val result = DatabaseHelper.query(
                        "SELECT profile_id FROM profile_photo WHERE media_hash = ?",
                        arrayOf(mediaHash)
                    ).firstOrNull()
                    profileId = result?.get("profile_id") as? String
                }

                if (profileId == null) return@withContext null

                // Get display name
                val profileResult = DatabaseHelper.query(
                    "SELECT display_name FROM profile WHERE profile_id = ?",
                    arrayOf(profileId)
                ).firstOrNull()

                val displayName = profileResult?.get("display_name") as? String
                ProfileInfo(profileId, displayName, mediaHash)

            } catch (e: Exception) {
                Logger.e("Error resolving media hash in DB: ${e.message}", LogSource.MODULE)
                null
            }
        }

        private suspend fun getEnhancedProfileInfo(profileId: String): ProfileInfo? = withContext(Dispatchers.IO) {
            try {
                val profileResult = DatabaseHelper.query(
                    "SELECT display_name FROM profile WHERE profile_id = ?",
                    arrayOf(profileId)
                ).firstOrNull()

                val displayName = profileResult?.get("display_name") as? String
                ProfileInfo(profileId, displayName, null)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun upsertView(entity: ProfileViewEntity) {
            try {
                val existing = getDao().getView(entity.profileId)
                if (existing == null || entity.viewedAt > existing.viewedAt) {
                    getDao().upsert(entity)
                }
            } catch (e: Exception) {
                Logger.e("Error upserting: ${e.message}", LogSource.MODULE)
            }
        }
    }

    override fun init() {
        if (!(Config.get("track_profile_views", true) as Boolean)) return
        GrindrPlus.executeAsync { refreshProfileViews() }
    }
}