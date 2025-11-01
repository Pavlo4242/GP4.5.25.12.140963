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
                // Try multiple possible endpoints
                val endpoints = listOf(
                    "https://grindr.mobi/v7/views/list",
                    "https://grindr.mobi/v6/views/list",
                    "https://grindr.mobi/v5/views/list"
                )

                var success = false
                for (endpoint in endpoints) {
                    try {
                        Logger.d("Trying endpoint: $endpoint", LogSource.MODULE)
                        val response = GrindrPlus.httpClient.sendRequest(endpoint, "GET")
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string()
                            if (!bodyString.isNullOrEmpty()) {
                                processViewsListResponse(bodyString, endpoint)
                                success = true
                                Logger.d("Successfully fetched profile views from: $endpoint", LogSource.MODULE)
                                break
                            }
                        } else {
                            Logger.w("Endpoint $endpoint failed: HTTP ${response.code}", LogSource.MODULE)
                        }
                    } catch (e: Exception) {
                        Logger.w("Endpoint $endpoint error: ${e.message}", LogSource.MODULE)
                    }
                }

                if (!success) {
                    Logger.e("All profile views endpoints failed", LogSource.MODULE)
                }
            } catch (e: Exception) {
                Logger.e("Error refreshing profile views: ${e.message}", LogSource.MODULE)
                Logger.writeRaw(e.stackTraceToString())
            }
        }

        private suspend fun processViewsListResponse(bodyString: String, endpoint: String) {
            try {
                val jsonResponse = JSONObject(bodyString)
                Logger.d("Processing views response from: $endpoint", LogSource.MODULE)

                // Process full profiles first
                val profiles = jsonResponse.optJSONArray("profiles")
                if (profiles != null) {
                    Logger.d("Found ${profiles.length()} full profiles", LogSource.MODULE)
                    for (i in 0 until profiles.length()) {
                        val profile = profiles.getJSONObject(i)
                        processProfileView(profile, "v7_full")
                    }
                }

                // Process previews and cross-reference with database
                val previews = jsonResponse.optJSONArray("previews")
                if (previews != null) {
                    Logger.d("Found ${previews.length()} preview entries", LogSource.MODULE)
                    for (i in 0 until previews.length()) {
                        val preview = previews.getJSONObject(i)
                        processPreviewView(preview, "v7_preview")
                    }
                }

                // Cross-reference any unresolved profiles
                crossReferenceUnresolvedProfiles()

            } catch (e: Exception) {
                Logger.e("Error processing views list: ${e.message}", LogSource.MODULE)
                throw e
            }
        }

        private suspend fun processProfileView(profile: JSONObject, source: String) {
            try {
                val profileId = profile.optString("profileId").takeIf { it.isNotEmpty() } ?: return
                val lastViewed = profile.optLong("lastViewed", System.currentTimeMillis())
                val displayName = profile.optString("displayName").takeIf { it.isNotEmpty() }
                val distance = profile.optDouble("distance").takeIf { !it.isNaN() && it > 0 }
                val mediaHash = profile.optString("profileImageMediaHash").takeIf { it.isNotEmpty() }

                // Enhanced database cross-reference - get additional info from local DB
                val enhancedInfo = getEnhancedProfileInfo(profileId)
                val finalDisplayName = enhancedInfo?.displayName ?: displayName
                val finalMediaHash = enhancedInfo?.mediaHash ?: mediaHash

                val entity = ProfileViewEntity(
                    profileId = profileId,
                    viewedAt = lastViewed,
                    displayName = finalDisplayName,
                    distance = distance,
                    mediaHash = finalMediaHash,
                    source = source
                )

                upsertView(entity)
                Logger.d("Processed profile view: $profileId - $finalDisplayName", LogSource.MODULE)

            } catch (e: Exception) {
                Logger.e("Error processing profile view: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun processPreviewView(preview: JSONObject, source: String) {
            try {
                val mediaHash = preview.optString("profileImageMediaHash").takeIf { it.isNotEmpty() } ?: return
                val lastViewed = preview.optLong("lastViewed", System.currentTimeMillis())
                val distance = preview.optDouble("distance").takeIf { !it.isNaN() && it > 0 }
                val isFavorite = preview.optBoolean("isFavorite")

                // Try to resolve media hash to profile ID from database
                val profileInfo = resolveMediaHashToProfileInfo(mediaHash)

                if (profileInfo != null) {
                    // We found a match in the database
                    val entity = ProfileViewEntity(
                        profileId = profileInfo.profileId,
                        viewedAt = lastViewed,
                        displayName = profileInfo.displayName,
                        distance = distance,
                        mediaHash = mediaHash,
                        source = source
                    )
                    upsertView(entity)
                    Logger.d("Resolved preview to profile: ${profileInfo.profileId}", LogSource.MODULE)
                } else {
                    // No match found - create unresolved entry
                    val placeholderEntity = ProfileViewEntity(
                        profileId = "unresolved_${mediaHash.take(8)}",
                        viewedAt = lastViewed,
                        displayName = if (isFavorite) "⭐ Favorite (Unresolved)" else "🔒 Locked Profile",
                        distance = distance,
                        mediaHash = mediaHash,
                        source = "${source}_unresolved"
                    )
                    upsertView(placeholderEntity)
                    Logger.w("Added unresolved profile with media hash: $mediaHash", LogSource.MODULE)
                }

            } catch (e: Exception) {
                Logger.e("Error processing preview view: ${e.message}", LogSource.MODULE)
            }
        }

        private suspend fun crossReferenceUnresolvedProfiles() {
            try {
                // Get all unresolved entries
                val unresolvedViews = getDao().getUnresolvedViews()
                Logger.d("Cross-referencing ${unresolvedViews.size} unresolved profiles", LogSource.MODULE)

                for (view in unresolvedViews) {
                    val mediaHash = view.mediaHash ?: continue

                    // Try to resolve again
                    val profileInfo = resolveMediaHashToProfileInfo(mediaHash)
                    if (profileInfo != null) {
                        // Update the unresolved entry with real profile info
                        val resolvedEntity = ProfileViewEntity(
                            profileId = profileInfo.profileId,
                            viewedAt = view.viewedAt,
                            displayName = profileInfo.displayName,
                            distance = view.distance,
                            mediaHash = mediaHash,
                            source = view.source.replace("_unresolved", "_resolved")
                        )

                        // Delete the unresolved entry and add resolved one
                        getDao().delete(view)
                        getDao().upsert(resolvedEntity)

                        Logger.d("Resolved previously unresolved profile: ${profileInfo.profileId}", LogSource.MODULE)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Error cross-referencing unresolved profiles: ${e.message}", LogSource.MODULE)
            }
        }

        private data class ProfileInfo(
            val profileId: String,
            val displayName: String?,
            val mediaHash: String?
        )

        private suspend fun resolveMediaHashToProfileInfo(mediaHash: String): ProfileInfo? = withContext(Dispatchers.IO) {
            try {
                // Query profile_photo table to get profile ID from media hash
                val profilePhotoResult = DatabaseHelper.query(
                    "SELECT profile_id FROM profile_photo WHERE media_hash = ?",
                    arrayOf(mediaHash)
                ).firstOrNull()

                val profileId = profilePhotoResult?.get("profile_id") as? String
                if (profileId == null) {
                    return@withContext null
                }

                // Query profile table to get display name
                val profileResult = DatabaseHelper.query(
                    "SELECT display_name FROM profile WHERE profile_id = ?",
                    arrayOf(profileId)
                ).firstOrNull()

                val displayName = profileResult?.get("display_name") as? String

                ProfileInfo(profileId, displayName, mediaHash)

            } catch (e: Exception) {
                Logger.e("Error resolving media hash to profile info: ${e.message}", LogSource.MODULE)
                null
            }
        }

        private suspend fun getEnhancedProfileInfo(profileId: String): ProfileInfo? = withContext(Dispatchers.IO) {
            try {
                // Get display name from profile table
                val profileResult = DatabaseHelper.query(
                    "SELECT display_name FROM profile WHERE profile_id = ?",
                    arrayOf(profileId)
                ).firstOrNull()

                val displayName = profileResult?.get("display_name") as? String

                // Get latest media hash from profile_photo table
                val mediaHashResult = DatabaseHelper.query(
                    "SELECT media_hash FROM profile_photo WHERE profile_id = ? ORDER BY created_at DESC LIMIT 1",
                    arrayOf(profileId)
                ).firstOrNull()

                val mediaHash = mediaHashResult?.get("media_hash") as? String

                ProfileInfo(profileId, displayName, mediaHash)

            } catch (e: Exception) {
                Logger.e("Error getting enhanced profile info: ${e.message}", LogSource.MODULE)
                null
            }
        }

        private suspend fun upsertView(entity: ProfileViewEntity) {
            try {
                val existing = getDao().getView(entity.profileId)
                if (existing == null || entity.viewedAt > existing.viewedAt) {
                    getDao().upsert(entity)
                    Logger.d("Upserted profile view: ${entity.profileId}", LogSource.MODULE)
                }
            } catch (e: Exception) {
                Logger.e("Error upserting profile view: ${e.message}", LogSource.MODULE)
            }
        }
    }

    override fun init() {
        if (!(Config.get("track_profile_views", true) as Boolean)) {
            Logger.i("Profile views tracking is disabled", LogSource.MODULE)
            return
        }

        GrindrPlus.executeAsync {
            refreshProfileViews()
        }

        Logger.i("ProfileViewsTracker initialized", LogSource.MODULE)
    }
}