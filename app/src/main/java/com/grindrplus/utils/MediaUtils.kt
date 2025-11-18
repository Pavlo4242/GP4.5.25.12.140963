package com.grindrplus.utils

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import com.grindrplus.GrindrPlus
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MediaUtils {
    // Use the same spoofed User-Agent as the main Interceptor to ensure CDN allows the download
    private const val SPOOFED_USER_AGENT = "grindr3/25.16.0.144399;144399;Free;Android 13;SM-S908U;Samsung"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val mediaDir: File by lazy {
        File(GrindrPlus.context.filesDir, "saved_media").apply {
            if (!exists()) mkdirs()
        }
    }

    private val imageDir: File by lazy {
        File(mediaDir, "images").apply {
            if (!exists()) mkdirs()
        }
    }

    private val videoDir: File by lazy {
        File(mediaDir, "videos").apply {
            if (!exists()) mkdirs()
        }
    }

    fun saveMediaToPublicDirectory(
        url: String,
        albumName: String,
        profileId: String,
        contentId: String,
        contentType: String
    ) {
        GrindrPlus.executeAsync {
            val isVideo = contentType.contains("video") || url.endsWith(".mp4")
            val fileExtension = if (isVideo) "mp4" else "jpg"
            val directoryType = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

            // Sanitize folder names
            val safeAlbumName = albumName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val safeProfileId = profileId.replace(Regex("[^a-zA-Z0-9.-]"), "_")

            val relativePath = "${directoryType}/GrindrPlus/Albums/${safeProfileId} - ${safeAlbumName}"
            val fileName = "$contentId.$fileExtension"

            try {
                val resolver = GrindrPlus.context.contentResolver

                // Check if file exists
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                // Note: RELATIVE_PATH matches strictly, usually requires trailing slash in DB
                val selectionArgs = arrayOf("$relativePath/", fileName)

                val queryUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                resolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        Logger.d("Media file already exists, skipping download: $fileName", LogSource.MODULE)
                        return@executeAsync
                    }
                }

                Logger.d("Downloading media: $fileName from $url")
                val mediaData = downloadMediaSync(url).getOrThrow()

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(queryUri, values)
                if (uri != null) {
                    resolver.openOutputStream(uri).use { os ->
                        os?.write(mediaData)
                        os?.flush()
                    }

                    // Mark as finished
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    Logger.i("Successfully saved media to gallery: $fileName", LogSource.MODULE)

                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(GrindrPlus.context, "Saved: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw IOException("Failed to create MediaStore entry.")
                }
            } catch (e: Exception) {
                Logger.e("Failed to save public media for $contentId: ${e.message}", LogSource.MODULE)
            }
        }
    }

    fun downloadMedia(url: String): Result<ByteArray> = runCatching {
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", SPOOFED_USER_AGENT) // Fix: Add UA
                .get()
                .build()

            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(IOException("HTTP ${response.code}"))
                            return
                        }
                        response.body?.bytes()?.let { continuation.resume(it) }
                            ?: continuation.resumeWithException(IOException("Empty body"))
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        response.close()
                    }
                }
            })
        }
    }

    fun downloadMediaSync(url: String): Result<ByteArray> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SPOOFED_USER_AGENT) // Fix: Add UA
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Empty body")
        }
    }

    // ... [Keep existing methods: getMediaType, toBase64, fromBase64, saveToFile, saveMedia, getMediaFileUrl, mediaExists, getAllSavedMediaIds, MediaType enum, extensions] ...
    // The important fix is the SPOOFED_USER_AGENT added to the requests above.

    // (Re-include the rest of the file content here unchanged)

    fun getMediaType(url: String, contentType: String? = null): MediaType {
        if (!contentType.isNullOrBlank()) {
            if (contentType.startsWith("image/")) return MediaType.IMAGE
            if (contentType.startsWith("video/")) return MediaType.VIDEO
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(url)?.lowercase() ?: ""
        return when {
            extension in imageExtensions -> MediaType.IMAGE
            extension in videoExtensions -> MediaType.VIDEO
            url.contains("image", ignoreCase = true) -> MediaType.IMAGE
            url.contains("video", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.UNKNOWN
        }
    }

    fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
    fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.DEFAULT)
    fun ByteArray.saveToFile(file: File): Result<Boolean> = runCatching {
        FileOutputStream(file).use { it.write(this) }
        Timber.d("Successfully saved Media")
        true
    }

    suspend fun saveMedia(
        mediaId: Long,
        mediaData: ByteArray,
        mediaType: MediaType = MediaType.IMAGE,
        extension: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = when (mediaType) {
                MediaType.IMAGE -> imageDir
                MediaType.VIDEO -> videoDir
                MediaType.UNKNOWN -> mediaDir
            }

            val fileExtension = extension ?: when (mediaType) {
                MediaType.IMAGE -> "jpg"
                MediaType.VIDEO -> "mp4"
                MediaType.UNKNOWN -> "bin"
            }

            val mediaFile = File(directory, "$mediaId.$fileExtension")

            if (!mediaFile.exists()) {
                mediaData.saveToFile(mediaFile).getOrThrow()
                Logger.d("Saved ${mediaType.name.lowercase()} for mediaId $mediaId to ${mediaFile.absolutePath}")
            }

            "file://${mediaFile.absolutePath}"
        }.onFailure {
            Logger.e("Error saving ${mediaType.name.lowercase()} file: ${it.message}")
        }
    }

    fun getMediaFileUrl(
        mediaId: Long,
        mediaType: MediaType = MediaType.IMAGE,
        extension: String? = null
    ): String? {
        val directory = when (mediaType) {
            MediaType.IMAGE -> imageDir
            MediaType.VIDEO -> videoDir
            MediaType.UNKNOWN -> mediaDir
        }

        if (extension != null) {
            val specificFile = File(directory, "$mediaId.$extension")
            if (specificFile.exists()) {
                return "file://${specificFile.absolutePath}"
            }
            return null
        }

        val defaultExt = when (mediaType) {
            MediaType.IMAGE -> "jpg"
            MediaType.VIDEO -> "mp4"
            MediaType.UNKNOWN -> null
        }

        if (defaultExt != null) {
            val defaultFile = File(directory, "$mediaId.$defaultExt")
            if (defaultFile.exists()) {
                return "file://${defaultFile.absolutePath}"
            }
        }

        if (mediaType == MediaType.UNKNOWN) {
            val extensions = imageExtensions + videoExtensions
            for (ext in extensions) {
                val file = File(directory, "$mediaId.$ext")
                if (file.exists()) {
                    return "file://${file.absolutePath}"
                }
            }
        }

        return null
    }

    fun mediaExists(
        mediaId: Long,
        mediaType: MediaType = MediaType.UNKNOWN,
        extension: String? = null
    ): Boolean {
        val directories = when (mediaType) {
            MediaType.IMAGE -> listOf(imageDir)
            MediaType.VIDEO -> listOf(videoDir)
            MediaType.UNKNOWN -> listOf(imageDir, videoDir, mediaDir)
        }

        if (extension != null) {
            for (dir in directories) {
                if (File(dir, "$mediaId.$extension").exists()) {
                    return true
                }
            }
            return false
        }

        if (mediaType == MediaType.UNKNOWN) {
            val extensions = imageExtensions + videoExtensions
            for (dir in directories) {
                for (ext in extensions) {
                    if (File(dir, "$mediaId.$ext").exists()) {
                        return true
                    }
                }
            }
            return false
        }

        // Default checks
        return getMediaFileUrl(mediaId, mediaType, extension) != null
    }

    fun getAllSavedMediaIds(mediaType: MediaType = MediaType.UNKNOWN): List<Long> {
        val directories = when (mediaType) {
            MediaType.IMAGE -> listOf(imageDir)
            MediaType.VIDEO -> listOf(videoDir)
            MediaType.UNKNOWN -> listOf(imageDir, videoDir, mediaDir)
        }

        return directories.flatMap { dir ->
            dir.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { file ->
                    file.nameWithoutExtension.toLongOrNull()
                } ?: emptyList()
        }.distinct()
    }

    enum class MediaType { IMAGE, VIDEO, UNKNOWN }
    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp")
    private val videoExtensions = listOf("mp4", "mkv", "mov", "avi", "webm")
}

object ExpiringPhotoUtils {
    suspend fun downloadImage(url: String): Result<ByteArray> = MediaUtils.downloadMedia(url)
    suspend fun saveImage(mediaId: Long, imageData: ByteArray, extension: String? = null): Result<String> =
        MediaUtils.saveMedia(mediaId, imageData, MediaUtils.MediaType.IMAGE, extension)
    fun getImageFileUrl(mediaId: Long, extension: String? = null): String? =
        MediaUtils.getMediaFileUrl(mediaId, MediaUtils.MediaType.IMAGE, extension)
    fun imageFileExists(mediaId: Long, extension: String? = null): Boolean =
        MediaUtils.mediaExists(mediaId, MediaUtils.MediaType.IMAGE, extension)
    fun getAllSavedImageIds(): List<Long> =
        MediaUtils.getAllSavedMediaIds(MediaUtils.MediaType.IMAGE)
}