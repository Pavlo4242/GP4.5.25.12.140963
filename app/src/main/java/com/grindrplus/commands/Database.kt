package com.grindrplus.commands

import android.app.AlertDialog
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.room.withTransaction
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.persistence.mappers.asAlbumToAlbumEntity
import com.grindrplus.persistence.mappers.toAlbumContentEntities
import com.grindrplus.ui.Utils.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.grindrplus.ui.Utils.formatEpochSeconds
import org.json.JSONObject

private const val ENABLE_LOGGING = true

private fun logOutput(tag: String, message: String) {
    if (ENABLE_LOGGING) {
        android.util.Log.d("GrindrPlusDB", "$tag: $message")
    }
}


class Database(
    recipient: String,
    sender: String
) : CommandModule("Database", recipient, sender) {

    /**
     * Available Commands:
     * /populateLocations [populate_teleports, init_teleports] - Populates the teleport location database with default locations if empty or configured locations
     * /init_archive [setup_archive, init_archival] - Initialize selective data archival (non-invasive)
     * /archive_now [archive, backup_now] - Archive current data from Grindr database
     * /detect_deletions [check_deletions, find_deleted] - Detect deleted data by comparing archives
     * /clear_archive [clear_archival, wipe_archive] - Clear archived data
     * /toggle_http_log [toggle_http, http_log] - Toggle HTTP request/response logging
     * /toggle_wss_log [toggle_wss, wss_log] - Toggle WSS/XMPP auth logging
     * /view_logs [logs, show_logs] - View logs by type (http, credentials, wss_auth)
     * /clear_logs - Clear logs (http, credentials, wss_auth, or all)
     * /list_tables [tables, show_tables] - List all tables in the database
     * /list_table [table, show_table] - List all rows from a specific table
     * /list_databases [databases, dbs, show_dbs] - List all database files in the app's files directory
     */

    private val ioScope = CoroutineScope(Dispatchers.IO)

    @Command(name = "savealbums", aliases = ["archivealbums"], help = "Fetches and saves all albums shared with you to the local database")
    fun saveAllAlbums(args: List<String>) {
        GrindrPlus.runOnMainThread {
            Toast.makeText(it, "Starting album archival... This may take a while.", Toast.LENGTH_SHORT).show()
        }

        GrindrPlus.executeAsync {
            var profileCount = 0
            var albumCount = 0
            val albumDao = GrindrPlus.database.albumDao()

            try {
                // Step 1: Get all profiles that have shared albums with you
                val sharedResponse = GrindrPlus.httpClient.sendRequest(
                    url = "https://grindr.mobi/v2/albums/shares",
                    method = "GET"
                )

                if (!sharedResponse.isSuccessful) {
                    throw Exception("Failed to get initial list of shared albums.")
                }

                val sharedJson = JSONObject(sharedResponse.body?.string())
                val albumBriefs = sharedJson.optJSONArray("albums") ?: org.json.JSONArray()
                val profileIds = (0 until albumBriefs.length())
                    .map { albumBriefs.getJSONObject(it).optString("profileId") }
                    .filter { it.isNotEmpty() }
                    .toSet()

                profileCount = profileIds.size
                Logger.i("Found $profileCount profiles with shared albums. Starting fetch...")

                // Step 2: Iterate through each profile and get their albums
                for (profileId in profileIds) {
                    try {
                        val profileAlbumsResponse = GrindrPlus.httpClient.sendRequest(
                            url = "https://grindr.mobi/v2/albums/shares/$profileId",
                            method = "GET"
                        )
                        if (!profileAlbumsResponse.isSuccessful) continue

                        val profileAlbumsJson = JSONObject(profileAlbumsResponse.body?.string())
                        val profileAlbumBriefs = profileAlbumsJson.optJSONArray("albums") ?: continue

                        // Step 3: Iterate through each album and fetch its full content
                        for (i in 0 until profileAlbumBriefs.length()) {
                            val albumId = profileAlbumBriefs.getJSONObject(i).optLong("albumId")
                            if (albumId == 0L) continue

                            try {
                                val fullAlbumResponse = GrindrPlus.httpClient.sendRequest(
                                    url = "https://grindr.mobi/v2/albums/$albumId",
                                    method = "GET"
                                )
                                if (!fullAlbumResponse.isSuccessful) continue

                                val albumJson = JSONObject(fullAlbumResponse.body?.string())
                                val albumEntity = albumJson.asAlbumToAlbumEntity()
                                val contentEntities = albumJson.toAlbumContentEntities()

                                // Step 4: Save to database
                                GrindrPlus.database.withTransaction {
                                    albumDao.upsertAlbum(albumEntity)
                                    albumDao.upsertAlbumContents(contentEntities)
                                }
                                albumCount++
                                Logger.d("Successfully saved album $albumId from profile $profileId")
                            } catch (e: Exception) {
                                Logger.e("Failed to process album $albumId for profile $profileId: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("Failed to fetch albums for profile $profileId: ${e.message}")
                    }
                }

                GrindrPlus.runOnMainThread {
                    Toast.makeText(it, "Album archival complete! Saved $albumCount albums from $profileCount profiles.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Logger.e("Failed to archive albums: ${e.message}")
                GrindrPlus.runOnMainThread {
                    Toast.makeText(it, "Error during album archival. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Command(name = "populateLocations", aliases = ["populate_teleports", "init_teleports"], help = "Populates the teleport location database with default locations if empty or configured locations")
    fun populateLocations(args: List<String>) {
        ioScope.launch {
            try {
                GPDatabase.prePopulate(GrindrPlus.context)
                GrindrPlus.showToast(Toast.LENGTH_SHORT, "Teleport locations populated successfully.")
            } catch (e: Exception) {
                Logger.e("Failed to pre-populate locations: ${e.message}", LogSource.DB)
                GrindrPlus.showToast(Toast.LENGTH_LONG, "Error populating locations: ${e.message}")
            }
        }
    }

    @Command("init_archive", aliases = ["setup_archive", "init_archival"], help = "Initialize selective data archival (non-invasive)")
    fun initArchive(args: List<String>) {
        try {
            GrindrPlus.executeAsync {
                setupSelectiveArchival()
                GrindrPlus.runOnMainThread {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Selective archival initialized!")
                }
            }
        } catch (e: Exception) {
            Logger.e("Error initializing archival: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    private fun setupSelectiveArchival() {
        // Create archive tables in GrindrPlus database (not Grindr's!)

        // Archive critical tables only
        DatabaseHelper.executeGrindrPlus("""
        CREATE TABLE IF NOT EXISTS archived_messages (
            message_id TEXT PRIMARY KEY,
            sender TEXT NOT NULL,
            recipient TEXT,
            body TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            conversation_id TEXT NOT NULL,
            archived_at INTEGER DEFAULT (strftime('%s','now')),
            deletion_detected INTEGER DEFAULT 0
        )
    """)

        DatabaseHelper.executeGrindrPlus("""
        CREATE TABLE IF NOT EXISTS archived_profiles (
            profile_id TEXT PRIMARY KEY,
            display_name TEXT,
            age INTEGER,
            distance REAL,
            about_me TEXT,
            profile_tags TEXT,
            last_updated INTEGER NOT NULL,
            archived_at INTEGER DEFAULT (strftime('%s','now')),
            deletion_detected INTEGER DEFAULT 0
        )
    """)

        DatabaseHelper.executeGrindrPlus("""
        CREATE TABLE IF NOT EXISTS archived_profile_photos (
            media_hash TEXT PRIMARY KEY,
            profile_id TEXT NOT NULL,
            order_ INTEGER NOT NULL,
            archived_at INTEGER DEFAULT (strftime('%s','now')),
            deletion_detected INTEGER DEFAULT 0
        )
    """)

        // Indexes for efficient querying
        DatabaseHelper.executeGrindrPlus(
            "CREATE INDEX IF NOT EXISTS idx_archived_messages_conv ON archived_messages(conversation_id)"
        )
        DatabaseHelper.executeGrindrPlus(
            "CREATE INDEX IF NOT EXISTS idx_archived_messages_time ON archived_messages(archived_at)"
        )
        DatabaseHelper.executeGrindrPlus(
            "CREATE INDEX IF NOT EXISTS idx_archived_profiles_name ON archived_profiles(display_name)"
        )

        Logger.i("Selective archival tables created in GrindrPlus database", LogSource.MODULE)
    }

    @Command("archive_now", aliases = ["archive", "backup_now"], help = "Archive current data from Grindr database")
    fun archiveNow(args: List<String>) {
        try {
            GrindrPlus.executeAsync {
                val count = performArchival()
                GrindrPlus.runOnMainThread {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Archived $count records")
                }
            }
        } catch (e: Exception) {
            Logger.e("Error during archival: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    private fun performArchival(): Int {
        var totalArchived = 0

        // Archive messages
        try {
            val messages = DatabaseHelper.query("""
            SELECT message_id, sender, recipient, body, timestamp, conversation_id
            FROM chat_messages
            WHERE timestamp > (strftime('%s','now') - 2592000)
        """)

            messages.forEach { msg ->
                try {
                    DatabaseHelper.executeGrindrPlus("""
                    INSERT OR REPLACE INTO archived_messages 
                    (message_id, sender, recipient, body, timestamp, conversation_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, arrayOf(
                        msg["message_id"],
                        msg["sender"],
                        msg["recipient"],
                        msg["body"],
                        msg["timestamp"],
                        msg["conversation_id"]
                    ))
                    totalArchived++
                } catch (e: Exception) {
                    Logger.w("Failed to archive message: ${e.message}", LogSource.MODULE)
                }
            }
        } catch (e: Exception) {
            Logger.e("Error archiving messages: ${e.message}", LogSource.MODULE)
        }

        // Archive profiles
        try {
            val profiles = DatabaseHelper.query("""
            SELECT profile_id, display_name, age, distance, about_me, 
                   profile_tags, last_updated_time
            FROM profile
            WHERE last_updated_time > (strftime('%s','now') - 2592000)
        """)

            profiles.forEach { prof ->
                try {
                    DatabaseHelper.executeGrindrPlus("""
                    INSERT OR REPLACE INTO archived_profiles 
                    (profile_id, display_name, age, distance, about_me, 
                     profile_tags, last_updated)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, arrayOf(
                        prof["profile_id"],
                        prof["display_name"],
                        prof["age"],
                        prof["distance"],
                        prof["about_me"],
                        prof["profile_tags"],
                        prof["last_updated_time"]
                    ))
                    totalArchived++
                } catch (e: Exception) {
                    Logger.w("Failed to archive profile: ${e.message}", LogSource.MODULE)
                }
            }
        } catch (e: Exception) {
            Logger.e("Error archiving profiles: ${e.message}", LogSource.MODULE)
        }

        // Archive profile photos
        try {
            val photos = DatabaseHelper.query("""
            SELECT media_hash, profile_id, order_
            FROM profile_photo
        """)

            photos.forEach { photo ->
                try {
                    DatabaseHelper.executeGrindrPlus("""
                    INSERT OR REPLACE INTO archived_profile_photos 
                    (media_hash, profile_id, order_)
                    VALUES (?, ?, ?)
                """, arrayOf(
                        photo["media_hash"],
                        photo["profile_id"],
                        photo["order_"]
                    ))
                    totalArchived++
                } catch (e: Exception) {
                    Logger.w("Failed to archive photo: ${e.message}", LogSource.MODULE)
                }
            }
        } catch (e: Exception) {
            Logger.e("Error archiving photos: ${e.message}", LogSource.MODULE)
        }

        Logger.i("Archived $totalArchived records", LogSource.MODULE)
        return totalArchived
    }

    @Command("detect_deletions", aliases = ["check_deletions", "find_deleted"], help = "Detect deleted data by comparing archives")
    fun detectDeletions(args: List<String>) {
        try {
            GrindrPlus.executeAsync {
                val deletions = findDeletions()

                GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                    val content = if (deletions.isEmpty()) {
                        "No deletions detected."
                    } else {
                        "DETECTED DELETIONS:\n\n" + deletions.joinToString("\n\n") { deletion ->
                            """
                        Type: ${deletion["type"]}
                        ID: ${deletion["id"]}
                        Deleted: ${deletion["time"]}
                        Data: ${deletion["preview"]}
                        """.trimIndent()
                        }
                    }

                    val textView = AppCompatTextView(activity).apply {
                        text = content
                        textSize = 12f
                        setTextColor(Color.WHITE)
                        setPadding(20, 20, 20, 20)
                    }

                    val scrollView = android.widget.ScrollView(activity).apply {
                        addView(textView)
                    }

                    AlertDialog.Builder(activity)
                        .setTitle("Deletion Detection")
                        .setView(scrollView)
                        .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Copy") { _, _ ->
                            copyToClipboard("Deletions", content)
                        }
                        .create()
                        .show()
                }
            }
        } catch (e: Exception) {
            Logger.e("Error detecting deletions: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    private fun findDeletions(): List<Map<String, String>> {
        val deletions = mutableListOf<Map<String, String>>()

        // Check for deleted messages
        try {
            val deletedMessages = DatabaseHelper.queryGrindrPlus("""
            SELECT am.message_id, am.body, am.sender, am.conversation_id, am.archived_at
            FROM archived_messages am
            LEFT JOIN chat_messages cm ON am.message_id = cm.message_id
            WHERE cm.message_id IS NULL 
              AND am.deletion_detected = 0
            LIMIT 50
        """)

            deletedMessages.forEach { msg ->
                val messageId = msg["message_id"] as String
                val body = msg["body"] as String
                val sender = msg["sender"] as String
                val convId = msg["conversation_id"] as String
                val archivedAt = msg["archived_at"] as Int

                deletions.add(mapOf(
                    "type" to "Message",
                    "id" to messageId,
                    "time" to formatEpochSeconds(archivedAt.toLong()),
                    "preview" to "From: $sender\nConv: $convId\nBody: ${body.take(100)}"
                ))

                // Mark as detected
                DatabaseHelper.executeGrindrPlus(
                    "UPDATE archived_messages SET deletion_detected = 1 WHERE message_id = ?",
                    arrayOf(messageId)
                )
            }
        } catch (e: Exception) {
            Logger.e("Error checking deleted messages: ${e.message}", LogSource.MODULE)
        }

        // Check for deleted profiles
        try {
            val deletedProfiles = DatabaseHelper.queryGrindrPlus("""
            SELECT ap.profile_id, ap.display_name, ap.about_me, ap.archived_at
            FROM archived_profiles ap
            LEFT JOIN profile p ON ap.profile_id = p.profile_id
            WHERE p.profile_id IS NULL 
              AND ap.deletion_detected = 0
            LIMIT 50
        """)

            deletedProfiles.forEach { prof ->
                val profileId = prof["profile_id"] as String
                val displayName = prof["display_name"] as? String
                val aboutMe = prof["about_me"] as? String
                val archivedAt = prof["archived_at"] as Int

                deletions.add(mapOf(
                    "type" to "Profile",
                    "id" to profileId,
                    "time" to formatEpochSeconds(archivedAt.toLong()),
                    "preview" to "Name: $displayName\nAbout: ${aboutMe?.take(100) ?: "N/A"}"
                ))

                DatabaseHelper.executeGrindrPlus(
                    "UPDATE archived_profiles SET deletion_detected = 1 WHERE profile_id = ?",
                    arrayOf(profileId)
                )
            }
        } catch (e: Exception) {
            Logger.e("Error checking deleted profiles: ${e.message}", LogSource.MODULE)
        }

        return deletions
    }

    @Command("clear_archive", aliases = ["clear_archival", "wipe_archive"], help = "Clear archived data")
    fun clearArchive(args: List<String>) {
        try {
            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                AlertDialog.Builder(activity)
                    .setTitle("Clear Archive?")
                    .setMessage("This will permanently delete all archived data. Continue?")
                    .setPositiveButton("Yes") { _, _ ->
                        GrindrPlus.executeAsync {
                            DatabaseHelper.executeGrindrPlus("DELETE FROM archived_messages")
                            DatabaseHelper.executeGrindrPlus("DELETE FROM archived_profiles")
                            DatabaseHelper.executeGrindrPlus("DELETE FROM archived_profile_photos")

                            GrindrPlus.showToast(Toast.LENGTH_SHORT, "Archive cleared")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            Logger.e("Error clearing archive: ${e.message}", LogSource.MODULE)
        }
    }

    @Command("init_db", aliases = ["init_databases", "setup_db"], help = "Initialize all databases (teleports and archival)")
    fun initDatabases(args: List<String>) {
        try {
            GrindrPlus.executeAsync {
                populateLocations(emptyList()) // Run teleport population
                initArchive(emptyList()) // Run archival setup
                GrindrPlus.runOnMainThread {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "All databases initialized!")
                }
            }
        } catch (e: Exception) {
            Logger.e("Error initializing databases: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("toggle_logging", aliases = ["logging", "log_toggle"], help = "Toggle all logging (HTTP and WSS)")
    fun toggleAllLogging(args: List<String>) {
        try {
            toggleHttpLog(emptyList()) // Toggle HTTP logging
            toggleWssLog(emptyList()) // Toggle WSS logging
            val httpStatus = if (Config.get("enable_http_logging", false) as Boolean) "ON" else "OFF"
            val wssStatus = if (Config.get("enable_wss_logging", false) as Boolean) "ON" else "OFF"
            GrindrPlus.showToast(Toast.LENGTH_SHORT, "HTTP: $httpStatus, WSS: $wssStatus")
        } catch (e: Exception) {
            Logger.e("Error toggling logging: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("toggle_http_log", aliases = ["toggle_http", "http_log"], help = "Toggle HTTP request/response logging")
    fun toggleHttpLog(args: List<String>) {
        val current = Config.get("enable_http_logging", false) as Boolean
        Config.put("enable_http_logging", !current)
        GrindrPlus.showToast(Toast.LENGTH_SHORT, "HTTP logging: ${if (!current) "ON" else "OFF"}")
    }

    @Command("toggle_wss_log", aliases = ["toggle_wss", "wss_log"], help = "Toggle WSS/XMPP auth logging")
    fun toggleWssLog(args: List<String>) {
        val current = Config.get("enable_wss_logging", false) as Boolean
        Config.put("enable_wss_logging", !current)
        GrindrPlus.showToast(Toast.LENGTH_SHORT, "WSS logging: ${if (!current) "ON" else "OFF"}")
    }

    @Command("view_logs", aliases = ["logs", "show_logs"], help = "View logs by type (http, credentials, wss_auth)")
    fun viewLogs(args: List<String>) {
        val type = args.getOrNull(0) ?: "all"
        val limit = args.getOrNull(1)?.toIntOrNull() ?: 50

        GrindrPlus.executeAsync {
            val logs = when (type.lowercase()) {
                "http" -> GrindrPlus.database.logDao().getLogsByType("http", limit)
                "credentials", "creds" -> GrindrPlus.database.logDao().getLogsByType("credentials", limit)
                "wss", "wss_auth" -> GrindrPlus.database.logDao().getLogsByType("wss_auth", limit)
                "all" -> {
                    val httpCount = GrindrPlus.database.logDao().getCountByType("http")
                    val credCount = GrindrPlus.database.logDao().getCountByType("credentials")
                    val wssCount = GrindrPlus.database.logDao().getCountByType("wss_auth")

                    GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                        val summary = """
                    LOG SUMMARY:
                    
                    HTTP Logs: $httpCount
                    Credentials: $credCount
                    WSS Auth: $wssCount
                    
                    Use: /view_logs http
                         /view_logs credentials
                         /view_logs wss
                    """.trimIndent()

                        android.app.AlertDialog.Builder(activity)
                            .setTitle("Logs")
                            .setMessage(summary)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@executeAsync
                }
                else -> emptyList()
            }

            val content = logs.joinToString("\n\n") { log ->
                when (log.log_type) {
                    "http" -> "URL: ${log.url}\nMethod: ${log.method}\nCode: ${log.response_code}\nTime: ${formatEpochSeconds(log.timestamp / 1000)}"
                    "credentials" -> "Profile: ${log.profile_id}\nToken: ${log.auth_token?.take(20)}...\nTime: ${formatEpochSeconds(log.timestamp / 1000)}"
                    "wss_auth" -> "Profile: ${log.profile_id}\nXMPP Token: ${log.xmpp_token?.take(20)}...\nTime: ${formatEpochSeconds(log.timestamp / 1000)}"
                    else -> "Unknown log type"
                }
            }

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val textView = AppCompatTextView(activity).apply {
                    text = content.ifEmpty { "No logs found" }
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                }

                val scrollView = android.widget.ScrollView(activity).apply {
                    addView(textView)
                }

                android.app.AlertDialog.Builder(activity)
                    .setTitle("${type.uppercase()} Logs")
                    .setView(scrollView)
                    .setPositiveButton("Close", null)
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Logs", content)
                    }
                    .show()
            }
        }
    }

    @Command("clear_logs", aliases = ["cl"], help = "Clear logs (http, credentials, wss_auth, or all)")
    fun clearLogs(args: List<String>) {
        val type = args.getOrNull(0) ?: "all"

        GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
            android.app.AlertDialog.Builder(activity)
                .setTitle("Clear Logs?")
                .setMessage("Clear $type logs?")
                .setPositiveButton("Yes") { _, _ ->
                    GrindrPlus.executeAsync {
                        when (type.lowercase()) {
                            "http" -> GrindrPlus.database.logDao().clearLogsByType("http")
                            "credentials", "creds" -> GrindrPlus.database.logDao().clearLogsByType("credentials")
                            "wss", "wss_auth" -> GrindrPlus.database.logDao().clearLogsByType("wss_auth")
                            "all" -> GrindrPlus.database.logDao().clearAllLogs()
                        }
                        GrindrPlus.showToast(Toast.LENGTH_SHORT, "Logs cleared")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @Command("list_tables", aliases = ["tables", "show_tables"], help = "List all tables in the database")
    fun listTables(args: List<String>) {
        try {
            val query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"
            val tables = DatabaseHelper.query(query).map { it["name"].toString() }



            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tableList = if (tables.isEmpty()) "No tables found."
                else tables.joinToString("\n")

                val textView = AppCompatTextView(activity).apply {
                    text = tableList
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }

                logOutput("LIST_TABLES", tableList)

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Database Tables")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Database Tables", tableList)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            logOutput("LIST_TABLES_ERROR", errorMsg)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("list_table", aliases = ["table", "show_table"], help = "List all rows from a specific table")
    fun listTable(args: List<String>) {
        if (args.isEmpty()) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Please provide a table name.")
            return
        }

        val tableName = args[0]
        try {
            val query = "SELECT * FROM $tableName;"
            val rows = DatabaseHelper.query(query)

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tableContent = if (rows.isEmpty()) {
                    "No rows found in table $tableName."
                } else {
                    rows.joinToString("\n\n") { row ->
                        row.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    }
                }

                val textView = AppCompatTextView(activity).apply {
                    text = tableContent
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }
                logOutput("LIST_TABLE_$tableName", tableContent)

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Table Content: $tableName")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Table Content: $tableName", tableContent)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            logOutput("LIST_TABLE_ERROR", errorMsg)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    @Command("list_databases", aliases = ["databases", "dbs", "show_dbs"], help = "List all database files in the app's files directory")
    fun listDatabases(args: List<String>) {
        try {
            val context = GrindrPlus.context
            val databases = context.databaseList()

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val dbList = if (databases.isEmpty()) "No databases found." else databases.joinToString("\n")

                val textView = AppCompatTextView(activity).apply {
                    text = dbList
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 20, 0, 0)
                    }
                }
                logOutput("LIST_DATABASES", dbList)
                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Database Files")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Database Files", dbList)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            logOutput("LIST_DATABASES_ERROR", errorMsg)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }
}