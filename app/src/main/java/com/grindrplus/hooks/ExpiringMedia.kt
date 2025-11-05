package com.grindrplus.hooks

import android.content.ContentValues
import android.os.Environment
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookAdapter
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.MediaUtils
import com.grindrplus.utils.MediaUtils.MediaType
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.MediaStore

class ExpiringMedia : Hook(
    "Expiring media",
    "Allow unlimited photo/video viewing and save media permanently"
) {
    private val classMap = mapOf(
        "expiringVideoBody" to "com.grindrapp.android.chat.model.ExpiringVideoBody",
        "expiringImageBody" to "com.grindrapp.android.chat.model.ExpiringImageBody",
        "expiringImageBodyUiData" to "com.grindrapp.android.chat.presentation.model.BodyUiData\$ExpiringImageBodyUiData",
        "expiringStatusResponse" to "com.grindrapp.android.chat.api.model.ExpiringPhotoStatusResponse"
    )

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val filePathCache = mutableMapOf<Long, String>()

    override fun init() {
        findClass(classMap["expiringImageBodyUiData"]!!)
            .hook("hasViewsRemaining", HookStage.BEFORE) { param ->
                param.setResult(true)
            }

        findClass(classMap["expiringImageBody"]!!)
            .hook("getDuration", HookStage.BEFORE) { param ->
                param.setResult(Long.MAX_VALUE)
            }

        findClass(classMap["expiringImageBody"]!!)
            .hook("getViewsRemaining", HookStage.BEFORE) { param ->
                param.setResult(Int.MAX_VALUE)
            }

        findClass(classMap["expiringVideoBody"]!!)
            .hook("getMaxViews", HookStage.BEFORE) { param ->
                param.setResult(Int.MAX_VALUE)
            }

        findClass(classMap["expiringVideoBody"]!!)
            .hook("getViewsRemaining", HookStage.BEFORE) { param ->
                param.setResult(Int.MAX_VALUE)
            }

        findClass(classMap["expiringStatusResponse"]!!)
            .hook("getAvailable", HookStage.BEFORE) { param ->
                param.setResult(Int.MAX_VALUE)
            }

        findClass(classMap["expiringStatusResponse"]!!)
            .hook("getTotal", HookStage.BEFORE) { param ->
                param.setResult(Int.MAX_VALUE)
            }

        findClass(classMap["expiringImageBody"]!!)
            .hook("getUrl", HookStage.AFTER) { param ->
                handleGetUrl(param, MediaType.IMAGE)
            }

        findClass(classMap["expiringVideoBody"]!!)
            .hook("getUrl", HookStage.AFTER) { param ->
                handleGetUrl(param, MediaType.VIDEO)
            }
    }

    private fun handleGetUrl(param: HookAdapter<*>, mediaType: MediaType) {
        val mediaId = getObjectField(param.thisObject(), "mediaId") as Long
        val originalUrl = getObjectField(param.thisObject(), "url")?.toString()
        val mediaTypeStr = if (mediaType == MediaType.IMAGE) "photo" else "video"

        filePathCache[mediaId]?.let { cachedPath ->
            logd("Using cached $mediaTypeStr path: $cachedPath")
            param.setResult(cachedPath)
            return
        }

        MediaUtils.getMediaFileUrl(mediaId, mediaType)?.let { existingFilePath ->
            logd("Using existing saved $mediaTypeStr: $existingFilePath")
            filePathCache[mediaId] = existingFilePath
            param.setResult(existingFilePath)
            return
        }

        if (!originalUrl.isNullOrEmpty() && !originalUrl.startsWith("file://")) {
            coroutineScope.launch {
                try {
                    logd("Downloading $mediaTypeStr from URL: ${originalUrl.take(50)}...")

                    MediaUtils.downloadMedia(originalUrl).fold(
                        onSuccess = { mediaData ->
                            logi("$mediaTypeStr downloaded: ${mediaData.size} bytes")
                            saveMediaAndUpdateUrl(param, mediaId, mediaData, mediaType)
                        },
                        onFailure = { error ->
                            loge("Failed to download $mediaTypeStr: ${error.message}")
                            param.setResult(originalUrl)
                        }
                    )
                } catch (e: Exception) {
                    loge("Error processing expiring $mediaTypeStr: ${e.message}")
                    Logger.writeRaw(e.stackTraceToString())
                    param.setResult(originalUrl)
                }
            }
        }
    }

    private suspend fun saveMediaAndUpdateUrl(
        param: HookAdapter<*>,
        mediaId: Long,
        mediaData: ByteArray,
        mediaType: MediaType
    ) {
        val mediaTypeStr = if (mediaType == MediaType.IMAGE) "photo" else "video"
        val fileExtension = if (mediaType == MediaType.IMAGE) "jpg" else "mp4"

        MediaUtils.saveMedia(mediaId, mediaData, mediaType, fileExtension).fold(
            onSuccess = { filePath ->
                logi("Saved $mediaTypeStr permanently for mediaId: $mediaId")
                filePathCache[mediaId] = filePath

                withContext(Dispatchers.Main) {
                    param.setResult(filePath)
                }

                // Automatically save a copy to user-accessible public storage (Pictures or Movies)
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val resolver = GrindrPlus.context.contentResolver
                            val collection = if (mediaType == MediaType.IMAGE) {
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            } else {
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            }
                            val dir = if (mediaType == MediaType.IMAGE) {
                                Environment.DIRECTORY_PICTURES
                            } else {
                                Environment.DIRECTORY_MOVIES
                            }
                            val fileName = "grindr_${mediaTypeStr}_${mediaId}.${fileExtension}"
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, if (mediaType == MediaType.IMAGE) "image/jpeg" else "video/mp4")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Grindr")
                            }
                            val uri = resolver.insert(collection, values)
                            if (uri != null) {
                                resolver.openOutputStream(uri).use { os ->
                                    os?.write(mediaData)
                                    os?.flush()
                                }
                                logi("Saved $mediaTypeStr to public storage: $fileName")
                            } else {
                                loge("Failed to insert to MediaStore for public save")
                            }
                        } catch (e: Exception) {
                            loge("Failed to save to public storage: ${e.message}")
                            // Fallback: No additional action needed, as local private save already succeeded
                        }
                    }
                }
            },
            onFailure = { error ->
                loge("Failed to save $mediaTypeStr: ${error.message}")

                val originalUrl = getObjectField(param.thisObject(), "url")?.toString()
                if (!originalUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        param.setResult(originalUrl)
                    }
                }
            }
        )
    }
}