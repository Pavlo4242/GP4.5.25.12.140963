package com.grindrplus.hooks

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.MediaUtils
import com.grindrplus.utils.hook

class SaveMediaOnLongPress : Hook(
    "Save on Long-Press",
    "Long-press any full-screen media (expiring, album, or chat) to save it to your gallery."
) {
    private val mediaActivities = setOf(
        "com.grindrapp.android.ui.photos.FullScreenExpiringImageActivity",
        "com.grindrapp.android.ui.albums.AlbumsVideoPlayerActivity",
        "com.grindrapp.android.ui.albums.AlbumCruiseActivity",
        "com.grindrapp.android.ui.photos.ChatRoomPhotosActivity"
    )

    override fun init() {
        mediaActivities.forEach { activityClassName ->
            hookActivity(activityClassName)
        }
    }

    private fun hookActivity(activityClassName: String) {
        try {
            findClass(activityClassName).hook("onCreate", HookStage.AFTER) { param ->
                val activity = param.thisObject() as Activity

                activity.window.decorView.post {
                    try {
                        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                        val targetView = findTargetView(rootView)

                        if (targetView == null) {
                            loge("Could not find a suitable media view to attach listener in $activityClassName")
                            return@post
                        }

                        if (targetView.tag == "long_press_hook_applied") return@post
                        targetView.tag = "long_press_hook_applied"

                        targetView.setOnLongClickListener {
                            logd("Long-press detected in $activityClassName")

                            try {
                                val intent = activity.intent

                                // Check all known possible keys for media URLs
                                val url = intent.getStringExtra("IMAGE_URL")
                                    ?: intent.getStringExtra("VIDEO_URL")
                                    ?: intent.getStringExtra("image_url")
                                    ?: intent.getStringExtra("video_url")
                                    ?: intent.getStringExtra("url")
                                    ?: intent.getStringExtra("media_url")
                                    ?: intent.dataString

                                if (url.isNullOrEmpty()) {
                                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Error: Could not find media URL.")
                                    return@setOnLongClickListener true
                                }

                                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Saving media...")

                                val isVideo = url.contains(".mp4") || activityClassName.contains("Video")
                                val contentType = if (isVideo) "video/mp4" else "image/jpeg"
                                val contentId = url.substringAfterLast("/").substringBefore("?")
                                    .substringBefore(".") // Ensure extension isn't part of ID

                                val albumName = when {
                                    activityClassName.contains("Album") -> "Albums"
                                    activityClassName.contains("Expiring") -> "Expiring Media"
                                    activityClassName.contains("Chat") -> "Chat Media"
                                    else -> "Saved Media"
                                }

                                val profileId = intent.getStringExtra("profile_id")
                                    ?: intent.getStringExtra("profileId")
                                    ?: "UnknownProfile"

                                MediaUtils.saveMediaToPublicDirectory(
                                    url = url,
                                    albumName = albumName,
                                    profileId = profileId,
                                    contentId = contentId,
                                    contentType = contentType
                                )

                            } catch (e: Exception) {
                                loge("Failed to save media on long-press: ${e.message}")
                                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
                                Logger.writeRaw(e.stackTraceToString())
                            }

                            true
                        }
                        logd("Successfully attached long-press listener to view in $activityClassName")
                    } catch (e: Exception) {
                        loge("Error in post-onCreate for $activityClassName: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            loge("Failed to find or hook class $activityClassName: ${e.message}")
        }
    }

    private fun findTargetView(view: View): View? {
        val className = view.javaClass.name.lowercase()
        val targetClasses = listOf("photoview", "videoview", "playerview", "touchimageview", "grindrvideoview")

        if (targetClasses.any { className.contains(it) }) {
            return view
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTargetView(view.getChildAt(i))
                if (found != null) return found
            }
        }

        return null
    }
}