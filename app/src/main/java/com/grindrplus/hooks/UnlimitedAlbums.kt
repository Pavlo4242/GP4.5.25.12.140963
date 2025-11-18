package com.grindrplus.hooks


import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.room.withTransaction
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.logw
import com.grindrplus.persistence.mappers.asAlbumBriefToAlbumEntity
import com.grindrplus.persistence.mappers.asAlbumToAlbumEntity
import com.grindrplus.persistence.mappers.toAlbumContentEntity
import com.grindrplus.persistence.mappers.toGrindrAlbum
import com.grindrplus.persistence.mappers.toGrindrAlbumBrief
import com.grindrplus.persistence.mappers.toGrindrAlbumContent
import com.grindrplus.persistence.mappers.toGrindrAlbumWithoutContent
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.MediaUtils
import com.grindrplus.utils.RetrofitUtils
import com.grindrplus.utils.RetrofitUtils.createSuccess
import com.grindrplus.utils.RetrofitUtils.getSuccessValue
import com.grindrplus.utils.RetrofitUtils.isFail
import com.grindrplus.utils.RetrofitUtils.isGET
import com.grindrplus.utils.RetrofitUtils.isPUT
import com.grindrplus.utils.RetrofitUtils.isSuccess
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import com.grindrplus.utils.withSuspendResult
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import java.io.Closeable
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class UnlimitedAlbums : Hook("Unlimited albums", "Allow to be able to view unlimited albums") {
    private val albumsService = "T4.a" // search for 'v1/albums/red-dot'
    private val albumModel = "com.grindrapp.android.model.Album"
    private val filteredSpankBankAlbumContent =
        "com.grindrapp.android.albums.spankbank.domain.model.FilteredSpankBankAlbumContent"
    private val spankBankAlbumModel =
        "com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbum"
    private val spankBankAlbumContentModel =
        "com.grindrapp.android.albums.spankbank.domain.model.SpankBankAlbumContent"
    private val httpExceptionResponse = "com.grindrapp.android.network.http.HttpExceptionResponse"
    private val sharedAlbumsBrief = "com.grindrapp.android.model.albums.SharedAlbumsBrief"
    private val albumsList = "com.grindrapp.android.model.AlbumsList"

    private val albumThumbView = "com.grindrapp.android.view.albums.AlbumThumbView"
    private val albumCruiseActivity = "com.grindrapp.android.ui.albums.AlbumCruiseActivity"

    override fun init() {
        // Bypass album paywall by overwriting the click listener on album thumbnails
        try {
            findClass(albumThumbView).hookConstructor(HookStage.AFTER) { param ->
                val thumbView = param.thisObject() as View
                val albumDataField = thumbView.javaClass.declaredFields.find {
                    it.type.name.contains("AlbumBrief")
                } ?: return@hookConstructor

                albumDataField.isAccessible = true
                val albumBrief = albumDataField.get(thumbView) ?: return@hookConstructor

                val albumId = getObjectField(albumBrief, "albumId") as Long
                val profileId = getObjectField(albumBrief, "profileId") as String

                thumbView.setOnClickListener {
                    try {
                        val context = it.context
                        val intent = Intent(context, findClass(albumCruiseActivity))
                        intent.putExtra("album_id", albumId)
                        intent.putExtra("profile_id", profileId)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        loge("Failed to launch AlbumCruiseActivity: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            loge("Failed to hook AlbumThumbView: ${e.message}")
        }

        // Hook API calls to save album data and provide offline access
        val albumsServiceClass = findClass(albumsService)
        RetrofitUtils.hookService(
            albumsServiceClass,
        ) { originalHandler, proxy, method, args ->
            val result = originalHandler.invoke(proxy, method, args)
            try {
                when {
                    method.isGET("v2/albums/{albumId}") -> handleGetAlbum(args, result)
                    method.isGET("v1/albums") -> handleGetAlbums(args, result)
                    method.isGET("v2/albums/shares") -> handleGetAlbumsShares(args, result)
                    method.isGET("v2/albums/shares/{profileId}") ->
                        handleGetAlbumsSharesProfileId(args, result)
                    method.isGET("v3/albums/{albumId}/view") ->
                        handleGetAlbumsViewAlbumId(args, result)
                    method.isPUT("v1/albums/{albumId}/shares/remove") ->
                        handleRemoveAlbumShares(args, result)
                    else -> result
                }
            } catch (e: Exception) {
                loge("Error handling album request: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
                result
            }
        }

        // Generic hooks to make various album-related objects always appear viewable
        findClass(albumModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
                setObjectField(param.thisObject(), "viewableUntil", Long.MAX_VALUE)
            } catch (e: Exception) {
                loge("Error making album viewable: ${e.message}")
            }
        }
        findClass(spankBankAlbumModel).hookConstructor(HookStage.AFTER) { param ->
            try {
                setObjectField(param.thisObject(), "albumViewable", true)
                setObjectField(param.thisObject(), "expiresAt", Long.MAX_VALUE)
            } catch (e: Exception) {
                loge("Error making spank bank album viewable: ${e.message}")
            }
        }
        listOf(spankBankAlbumContentModel, filteredSpankBankAlbumContent).forEach { clazz ->
            findClass(clazz).hookConstructor(HookStage.AFTER) { param ->
                try {
                    setObjectField(param.thisObject(), "albumViewable", true)
                } catch (e: Exception) {
                    loge("Error making spank bank content viewable: ${e.message}")
                }
            }
        }
        findClass(albumModel).hook("isValid", HookStage.BEFORE) { param -> param.setResult(true) }
    }

    private suspend fun archiveAlbumData(album: AlbumEntity, contents: List<AlbumContentEntity>) {
        GrindrPlus.database.withTransaction {
            GrindrPlus.database.albumDao().upsertAlbum(album)
            GrindrPlus.database.albumDao().upsertAlbumContents(contents)
        }
        logi("Archiving ${contents.size} media items for album '${album.albumName}'")
        for (content in contents) {
            val url = content.url ?: continue
            MediaUtils.saveMediaToPublicDirectory(
                url = url,
                albumName = album.albumName ?: "album_${album.id}",
                profileId = album.profileId.toString(),
                contentId = content.id.toString(),
                contentType = content.contentType ?: ""
            )
        }
    }

    private fun handleRemoveAlbumShares(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            val albumId = args[0] as? Long ?: return@withSuspendResult result
            logi("Removing album shares for ID: $albumId")
            if (result.isFail()) {
                try {
                    runBlocking {
                        val dao = GrindrPlus.database.albumDao()
                        if (dao.getAlbum(albumId) != null) {
                            dao.deleteAlbum(albumId)
                            createSuccess(albumId)
                        } else {
                            result
                        }
                    }
                } catch (e: Exception) {
                    loge("Failed to delete album $albumId: ${e.message}")
                    result
                }
            } else {
                result
            }
        }

    private fun handleGetAlbumsViewAlbumId(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            val albumId = args[0] as? Long ?: return@withSuspendResult result
            if (!result.isSuccess()) {
                runBlocking {
                    if (GrindrPlus.database.albumDao().albumExists(albumId)) {
                        logd("Album $albumId is viewable via database, returning success")
                        createSuccess(true)
                    } else {
                        result
                    }
                }
            } else {
                result
            }
        }

    private fun handleGetAlbum(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, initialResult ->
            val albumId = args[0] as? Long ?: return@withSuspendResult initialResult

            if (initialResult.isSuccess()) {
                logd("v2/albums/$albumId succeeded. Archiving result.")
                val grindrAlbum = initialResult.getSuccessValue()
                runBlocking {
                    val albumEntity = grindrAlbum.asAlbumToAlbumEntity()
                    val contentList = (getObjectField(grindrAlbum, "content") as? List<Any> ?: emptyList())
                        .map { it.toAlbumContentEntity(albumId) }
                    archiveAlbumData(albumEntity, contentList)
                }
                return@withSuspendResult initialResult
            }

            logw("v2/albums/$albumId failed. Trying v1 fallback and then database.")

            try {
                GrindrPlus.httpClient
                    .sendRequest(url = "https://grindr.mobi/v1/albums/$albumId", method = "GET")
                    .use { response ->
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                            logi("v1/albums/$albumId fallback SUCCEEDED. Parsing and archiving.")
                            val json = JSONObject(responseBody)
                            val albumEntity = jsonToAlbumEntity(albumId, json)
                            val contentEntities = jsonToAlbumContentEntities(albumId, json)

                            runBlocking { archiveAlbumData(albumEntity, contentEntities) }

                            val grindrAlbumObject = albumEntity.toGrindrAlbum(contentEntities)
                            return@withSuspendResult createSuccess(grindrAlbumObject)
                        }
                    }
            } catch (e: Exception) {
                loge("v1/albums/$albumId fallback failed: ${e.message}")
            }

            logd("All network fallbacks failed for album $albumId. Checking local database.")
            return@withSuspendResult fetchAlbumFromDatabase(albumId, initialResult)
        }

    private fun jsonToAlbumEntity(albumId: Long, json: JSONObject): AlbumEntity {
        return AlbumEntity(
            id = albumId,
            albumName = json.optString("albumName", "Unknown Album"),
            createdAt = json.optString("createdAt", System.currentTimeMillis().toString()),
            profileId = json.optLong("profileId", 0L),
            updatedAt = json.optString("updatedAt", System.currentTimeMillis().toString())
        )
    }

    private fun jsonToAlbumContentEntities(albumId: Long, json: JSONObject): List<AlbumContentEntity> {
        val contentList = mutableListOf<AlbumContentEntity>()
        json.optJSONArray("content")?.let { contentArray ->
            for (i in 0 until contentArray.length()) {
                val contentJson = contentArray.getJSONObject(i)
                contentList.add(AlbumContentEntity(
                    id = contentJson.optLong("contentId"),
                    albumId = albumId,
                    contentType = contentJson.optString("contentType"),
                    coverUrl = contentJson.optString("coverUrl"),
                    thumbUrl = contentJson.optString("thumbUrl"),
                    url = contentJson.optString("url")
                ))
            }
        }
        return contentList
    }

    private fun fetchAlbumFromDatabase(albumId: Long, originalResult: Any): Any {
        return try {
            runBlocking {
                val dao = GrindrPlus.database.albumDao()
                val album = dao.getAlbum(albumId)
                if (album != null) {
                    val content = dao.getAlbumContent(albumId)
                    val albumObject = album.toGrindrAlbum(content)
                    createSuccess(albumObject)
                } else {
                    logw("Album $albumId not found in database")
                    originalResult
                }
            }
        } catch (e: Exception) {
            loge("Failed to load album $albumId from database: ${e.message}")
            originalResult
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbums(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            if (result.isSuccess()) {
                try {
                    val albums = getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                    if (albums != null) {
                        runBlocking {
                            albums.forEach { album ->
                                try {
                                    val albumEntity = album.asAlbumToAlbumEntity()
                                    val contentList = (getObjectField(album, "content") as? List<Any> ?: emptyList())
                                        .map { it.toAlbumContentEntity(albumEntity.id) }
                                    archiveAlbumData(albumEntity, contentList)
                                } catch (e: Exception) {
                                    loge("Error saving album: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error processing albums: ${e.message}")
                }
            }

            try {
                val albums = runBlocking {
                    GrindrPlus.database.albumDao().getAlbums().mapNotNull {
                        try {
                            val dbContent = GrindrPlus.database.albumDao().getAlbumContent(it.id)
                            it.toGrindrAlbum(dbContent)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val newValue = findClass(albumsList).getConstructor(List::class.java).newInstance(albums)
                createSuccess(newValue)
            } catch (e: Exception) {
                loge("Error creating albums list from database: ${e.message}")
                result
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbumsShares(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            if (result.isSuccess()) {
                try {
                    runBlocking {
                        val albumBriefs = getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                        albumBriefs?.forEach { albumBrief ->
                            try {
                                val albumEntity = albumBrief.asAlbumBriefToAlbumEntity()
                                val content = getObjectField(albumBrief, "content")
                                val contentList = if (content != null) listOf(content.toAlbumContentEntity(albumEntity.id)) else emptyList()
                                archiveAlbumData(albumEntity, contentList)
                            } catch (e: Exception) {
                                loge("Error processing album brief: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error saving album briefs: ${e.message}")
                }
            }
            try {
                val albumBriefs = runBlocking {
                    GrindrPlus.database.albumDao().getAlbums().mapNotNull {
                        try {
                            val dbContent = GrindrPlus.database.albumDao().getAlbumContent(it.id)
                            if (dbContent.isNotEmpty()) it.toGrindrAlbumBrief(dbContent.first()) else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val newValue = findClass(sharedAlbumsBrief).getConstructor(List::class.java).newInstance(albumBriefs)
                createSuccess(newValue)
            } catch (e: Exception) {
                loge("Error creating shared albums brief from database: ${e.message}")
                result
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun handleGetAlbumsSharesProfileId(args: Array<Any?>, result: Any) =
        withSuspendResult(args, result) { _, result ->
            val profileId = args[0] as? Long ?: return@withSuspendResult result
            logd("Fetching shared albums for profile ID: $profileId")

            if (result.isSuccess()) {
                try {
                    val albumBriefs = getObjectField(result.getSuccessValue(), "albums") as? List<Any>
                    if (!albumBriefs.isNullOrEmpty()) {
                        logd("Received ${albumBriefs.size} album(s) from network. Checking for locked albums...")
                        runBlocking {
                            for (albumBrief in albumBriefs) {
                                val isViewable = getObjectField(albumBrief, "albumViewable") as? Boolean ?: false
                                val currentAlbumId = getObjectField(albumBrief, "albumId") as Long

                                val albumEntity = albumBrief.asAlbumBriefToAlbumEntity()
                                val content = getObjectField(albumBrief, "content")
                                val contentList = if (content != null) listOf(content.toAlbumContentEntity(albumEntity.id)) else emptyList()

                                if (isViewable) {
                                    archiveAlbumData(albumEntity, contentList)
                                } else {
                                    logi("Found locked album $currentAlbumId. Attempting v1 fallback unlock.")
                                    try {
                                        GrindrPlus.httpClient.sendRequest(
                                            url = "https://grindr.mobi/v1/albums/$currentAlbumId", method = "GET"
                                        ).use { v1Response ->
                                            val v1Body = v1Response.body?.string()
                                            if (v1Response.isSuccessful && !v1Body.isNullOrEmpty()) {
                                                logi("v1 fallback for $currentAlbumId SUCCEEDED. Archiving.")
                                                val json = JSONObject(v1Body)
                                                val unlockedAlbumEntity = jsonToAlbumEntity(currentAlbumId, json)
                                                val unlockedContentEntities = jsonToAlbumContentEntities(currentAlbumId, json)
                                                archiveAlbumData(unlockedAlbumEntity, unlockedContentEntities)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        loge("Exception during v1 fallback for $currentAlbumId: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    loge("Error during proactive album unlocking: ${e.message}")
                }
            }

            try {
                val albumBriefsFromDb = runBlocking {
                    GrindrPlus.database.albumDao().getAlbums(profileId).mapNotNull {
                        try {
                            val dbContent = GrindrPlus.database.albumDao().getAlbumContent(it.id)
                            if (dbContent.isNotEmpty()) it.toGrindrAlbumBrief(dbContent.first()) else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val newValue = findClass(sharedAlbumsBrief).getConstructor(List::class.java).newInstance(albumBriefsFromDb)
                return@withSuspendResult createSuccess(newValue)
            } catch (e: Exception) {
                loge("FATAL: Error creating final shared albums brief from database: ${e.message}")
                return@withSuspendResult result
            }
        }

    private inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
        var closed = false
        try {
            return block(this)
        } catch (e: Exception) {
            closed = true
            try {
                this?.close()
            } catch (closeException: IOException) {
                e.addSuppressed(closeException)
            }
            throw e
        } finally {
            if (!closed) {
                this?.close()
            }
        }
    }
}