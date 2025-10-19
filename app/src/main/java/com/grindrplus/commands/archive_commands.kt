package com.grindrplus.commands

import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.utils.MediaDehasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.apply
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.fold
import kotlin.io.writeText
import kotlin.stackTraceToString
import kotlin.text.format
import kotlin.text.toLongOrNull
import kotlin.text.trimIndent

class Archive(
    recipient: String,
    sender: String
) : CommandModule("Archive", recipient, sender) {

    private val scope = CoroutineScope(Dispatchers.Main)

    @Command("archive_profile", aliases = ["ap"], help = "Archive all photos from current or specified profile")
    fun archiveProfile(args: List<String>) {
        val profileId = if (args.isNotEmpty()) args[0] else sender

        scope.launch {
            try {
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Archiving profile $profileId...")

                val result = MediaDehasher.archiveProfilePhotos(profileId)

                result.fold(
                    onSuccess = { photos ->
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Archived ${photos.size} photos for profile $profileId"
                        )
                    },
                    onFailure = { error ->
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Archive failed: ${error.message}"
                        )
                        Logger.e("Archive failed: ${error.message}")
                        Logger.writeRaw(error.stackTraceToString())
                    }
                )
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    @Command("archive_album", aliases = ["aa"], help = "Archive entire album by ID")
    fun archiveAlbum(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: archive_album <album_id>")
            return
        }

        val albumId = args[0].toLongOrNull()
        if (albumId == null) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Invalid album ID")
            return
        }

        scope.launch {
            try {
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Archiving album $albumId...")

                val result = MediaDehasher.archiveAlbum(albumId)

                result.fold(
                    onSuccess = { media ->
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Archived ${media.size} items from album $albumId"
                        )
                    },
                    onFailure = { error ->
                        GrindrPlus.showToast(
                            Toast.LENGTH_LONG,
                            "Archive failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    @Command("archive_stats", aliases = ["as"], help = "Show archive statistics")
    fun archiveStats(args: List<String>) {
        scope.launch {
            try {
                val stats = MediaDehasher.getArchiveStats()
                val sizeInMB = stats.totalSize / (1024.0 * 1024.0)

                val message = """
                    Archive Statistics:
                    • Total files: ${stats.totalFiles}
                    • Total size: ${"%.2f".format(sizeInMB)} MB
                    • Location: ${stats.storageLocation}
                """.trimIndent()

                GrindrPlus.showToast(Toast.LENGTH_LONG, message)
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    @Command("profile_history", aliases = ["ph"], help = "View profile change history")
    fun profileHistory(args: List<String>) {
        val profileId = if (args.isNotEmpty()) args[0] else sender

        scope.launch {
            try {
                val dao = GrindrPlus.database.profileTrackingDao()
                val history = dao.getProfileHistory(profileId)

                if (history.isEmpty()) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "No history found for profile $profileId")
                    return@launch
                }

                val changes = kotlin.text.StringBuilder()
                changes.append("Profile History for $profileId:\n\n")

                history.forEachIndexed { index, snapshot ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(Date(snapshot.timestamp))

                    changes.append("${index + 1}. $date\n")
                    changes.append("   Name: ${snapshot.displayName ?: "N/A"}\n")
                    changes.append("   Age: ${snapshot.age ?: "N/A"}\n")

                    if (index < history.size - 1) {
                        val prev = history[index + 1]
                        if (snapshot.displayName != prev.displayName) {
                            changes.append("   ⚠ Name changed from: ${prev.displayName}\n")
                        }
                        if (snapshot.age != prev.age) {
                            changes.append("   ⚠ Age changed from: ${prev.age}\n")
                        }
                    }
                    changes.append("\n")
                }

                GrindrPlus.showToast(Toast.LENGTH_LONG, changes.toString())
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    @Command("photo_reuse", aliases = ["pr"], help = "Find profiles reusing the same photo")
    fun photoReuse(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Usage: photo_reuse <photo_hash>")
            return
        }

        val photoHash = args[0]

        scope.launch {
            try {
                val dao = GrindrPlus.database.profileTrackingDao()
                val profiles = dao.findProfilesWithPhoto(photoHash)

                if (profiles.isEmpty()) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "No profiles found with this photo")
                    return@launch
                }

                val message = "Photo used by ${profiles.size} profile(s):\n" +
                        profiles.joinToString("\n") { "• $it" }

                GrindrPlus.showToast(Toast.LENGTH_LONG, message)
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    @Command("cross_account", aliases = ["ca"], help = "See which accounts have interacted with this profile")
    fun crossAccount(args: List<String>) {
        val profileId = if (args.isNotEmpty()) args[0] else sender

        scope.launch {
            try {
                val dao = GrindrPlus.database.profileAccountMappingDao()
                val mappings = dao.getAccountsForProfile(profileId)

                if (mappings.isEmpty()) {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "No cross-account data for profile $profileId")
                    return@launch
                }

                val message = kotlin.text.StringBuilder()
                message.append("Profile $profileId seen by:\n\n")

                mappings.forEach { mapping ->
                    val firstSeen = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(Date(mapping.firstSeen))
                    val lastSeen = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(Date(mapping.lastSeen))

                    message.append("• ${mapping.accountPackage}\n")
                    message.append("  First: $firstSeen\n")
                    message.append("  Last: $lastSeen\n")
                    message.append("  Interactions: ${mapping.interactionCount}\n\n")
                }

                GrindrPlus.showToast(Toast.LENGTH_LONG, message.toString())
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
            }
        }
    }

    @Command("export_profile_data", aliases = ["epd"], help = "Export all data for a profile as JSON")
    fun exportProfileData(args: List<String>) {
        val profileId = if (args.isNotEmpty()) args[0] else sender

        scope.launch {
            try {
                val json = JSONObject()

                // Profile snapshots
                val snapshotsDao = GrindrPlus.database.profileTrackingDao()
                val snapshots = snapshotsDao.getProfileHistory(profileId)
                val snapshotsArray = JSONArray()
                snapshots.forEach { snapshot ->
                    snapshotsArray.put(JSONObject().apply {
                        put("timestamp", snapshot.timestamp)
                        put("displayName", snapshot.displayName)
                        put("aboutMe", snapshot.aboutMe)
                        put("age", snapshot.age)
                        put("height", snapshot.height)
                        put("weight", snapshot.weight)
                    })
                }
                json.put("snapshots", snapshotsArray)

                // Photo history
                val photos = snapshotsDao.getPhotoHistory(profileId)
                val photosArray = JSONArray()
                photos.forEach { photo ->
                    photosArray.put(JSONObject().apply {
                        put("photoHash", photo.photoHash)
                        put("photoUrl", photo.photoUrl)
                        put("localPath", photo.localPath)
                        put("isPrimary", photo.isPrimary)
                        put("timestamp", photo.timestamp)
                    })
                }
                json.put("photos", photosArray)

                // Cross-account data
                val mappingDao = GrindrPlus.database.profileAccountMappingDao()
                val mappings = mappingDao.getAccountsForProfile(profileId)
                val mappingsArray = JSONArray()
                mappings.forEach { mapping ->
                    mappingsArray.put(JSONObject().apply {
                        put("accountPackage", mapping.accountPackage)
                        put("firstSeen", mapping.firstSeen)
                        put("lastSeen", mapping.lastSeen)
                        put("interactionCount", mapping.interactionCount)
                    })
                }
                json.put("accounts", mappingsArray)

                // Conversations
                val convDao = GrindrPlus.database.conversationTrackingDao()
                val conversations = convDao.getConversationsForProfile(profileId)
                val convsArray = JSONArray()
                conversations.forEach { conv ->
                    convsArray.put(JSONObject().apply {
                        put("conversationId", conv.conversationId)
                        put("accountPackage", conv.accountPackage)
                        put("messageCount", conv.messageCount)
                        put("firstMessage", conv.firstMessageTimestamp)
                        put("lastMessage", conv.lastMessageTimestamp)
                    })
                }
                json.put("conversations", convsArray)

                // Save to file
                val file = File(
                    GrindrPlus.context.getExternalFilesDir(null),
                    "profile_${profileId}_export.json"
                )
                file.writeText(json.toString(2))

                GrindrPlus.showToast(
                    Toast.LENGTH_LONG,
                    "Profile data exported to: ${file.absolutePath}"
                )
            } catch (e: Exception) {
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Export failed: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }
}