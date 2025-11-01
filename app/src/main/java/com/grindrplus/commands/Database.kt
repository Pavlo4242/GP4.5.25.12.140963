package com.grindrplus.commands

import android.app.AlertDialog
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.grindrplus.GrindrPlus
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.DatabaseHelper.execute
import com.grindrplus.core.DatabaseHelper.query
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.persistence.GPDatabase
import com.grindrplus.ui.Utils.copyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context

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

    private val ioScope = CoroutineScope(Dispatchers.IO)

    @Command(name = "populateLocations", aliases = ["tplist_init"], help = "Populates the teleport location database with default locations if empty or configured locations")
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

    @Command("init_db", aliases = ["init"], help = "Initialize universal delete tracking for all tables")
    fun initDb(args: List<String>) {
        try {
            GrindrPlus.executeAsync {
                setupUniversalDeleteTracking()

                // Show success message on main thread
                GrindrPlus.runOnMainThread {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Universal delete tracking initialized!")
                }
            }
        } catch (e: Exception) {
            Logger.e("Error initializing delete tracking: ${e.message}", LogSource.MODULE)
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }

    private fun setupUniversalDeleteTracking() {
        try {
            // Create the universal archive table
            execute("""
                CREATE TABLE IF NOT EXISTS universal_deleted_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    original_table_name TEXT NOT NULL,
                    deleted_at INTEGER DEFAULT (strftime('%s','now')),
                    row_data TEXT,
                    row_primary_key TEXT,
                    delete_trigger TEXT DEFAULT 'unknown'
                )
            """)

            // Create indexes for efficient querying
            execute("CREATE INDEX IF NOT EXISTS idx_universal_archive_table ON universal_deleted_data(original_table_name)")
            execute("CREATE INDEX IF NOT EXISTS idx_universal_archive_time ON universal_deleted_data(deleted_at)")

            // Get all table names and create triggers for each
            val tables = query("""
                SELECT name as table_name 
                FROM sqlite_master 
                WHERE type = 'table' 
                AND name NOT LIKE 'sqlite_%'
                AND name NOT LIKE 'universal_%'
                AND name NOT IN ('android_metadata', 'room_master_table', 'universal_deleted_data')
            """)

            var triggersCreated = 0
            for (table in tables) {
                val tableName = table["table_name"] as String
                if (createEnhancedDeleteTriggerForTable(tableName)) {
                    triggersCreated++
                }
            }

            Logger.d("Universal delete tracking setup complete for $triggersCreated tables", LogSource.MODULE)

        } catch (e: Exception) {
            Logger.e("Error setting up universal delete tracking: ${e.message}", LogSource.MODULE)
            throw e // Re-throw to handle in command
        }
    }

    private fun createEnhancedDeleteTriggerForTable(tableName: String): Boolean {
        return try {
            // Get table schema to build dynamic data capture
            val columns = query("PRAGMA table_info(`$tableName`)")

            if (columns.isEmpty()) {
                Logger.w("No columns found for table: $tableName", LogSource.MODULE)
                return false
            }

            // Build JSON-like string manually
            val jsonBuilder = StringBuilder()
            jsonBuilder.append("'{")

            for ((index, column) in columns.withIndex()) {
                val columnName = column["name"] as String
                if (index > 0) jsonBuilder.append(",")
                jsonBuilder.append("""\"$columnName\":\"' || COALESCE(OLD.$columnName, 'NULL') || '"""")
            }
            jsonBuilder.append("}'")

            val jsonDataSQL = jsonBuilder.toString()

            // Drop existing trigger if any
            execute("DROP TRIGGER IF EXISTS universal_delete_${tableName}")

            // Create enhanced trigger
            val triggerSQL = """
                CREATE TRIGGER universal_delete_${tableName}
                BEFORE DELETE ON `${tableName}`
                FOR EACH ROW
                BEGIN
                    INSERT INTO universal_deleted_data 
                    (original_table_name, row_data, row_primary_key, delete_trigger)
                    VALUES (
                        '${tableName}',
                        $jsonDataSQL,
                        COALESCE(
                            OLD.ROWID, 
                            OLD.id, 
                            OLD._id, 
                            OLD.profile_id, 
                            OLD.message_id,
                            CAST(OLD.ROWID AS TEXT)
                        ),
                        'universal_delete_tracker'
                    );
                END;
            """.trimIndent()

            execute(triggerSQL)
            Logger.d("Created delete trigger for table: $tableName", LogSource.MODULE)
            true

        } catch (e: Exception) {
            Logger.w("Could not create delete trigger for $tableName: ${e.message}", LogSource.MODULE)
            false
        }
    }

    // Add a command to view deleted data
    @Command("view_deleted", aliases = ["vd"], help = "View recently deleted data")
    fun viewDeleted(args: List<String>) {
        try {
            val limit = args.getOrNull(0)?.toIntOrNull() ?: 50
            val deletedData = query("""
                SELECT 
                    id,
                    original_table_name,
                    datetime(deleted_at, 'unixepoch') as deleted_time,
                    row_primary_key,
                    delete_trigger,
                    substr(row_data, 1, 100) as data_preview
                FROM universal_deleted_data 
                ORDER BY deleted_at DESC 
                LIMIT $limit
            """)

            GrindrPlus.runOnMainThreadWithCurrentActivity { activity ->
                val dialogView = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val content = if (deletedData.isEmpty()) {
                    "No deleted data found."
                } else {
                    deletedData.joinToString("\n\n") { row ->
                        """
                        Table: ${row["original_table_name"]}
                        Time: ${row["deleted_time"]}
                        Key: ${row["row_primary_key"]}
                        Preview: ${row["data_preview"]}
                        """.trimIndent()
                    }
                }

                val textView = AppCompatTextView(activity).apply {
                    text = content
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding(20, 20, 20, 20)
                }

                dialogView.addView(textView)

                AlertDialog.Builder(activity)
                    .setTitle("Recently Deleted Data")
                    .setView(dialogView)
                    .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("Copy") { _, _ ->
                        copyToClipboard("Deleted Data", content)
                    }
                    .create()
                    .show()
            }
        } catch (e: Exception) {
            GrindrPlus.showToast(Toast.LENGTH_LONG, "Error: ${e.message}")
        }
    }
    @Command("list_tables", aliases = ["lts"], help = "List all tables in the database")
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

    @Command("list_table", aliases = ["lt"], help = "List all rows from a specific table")
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

    @Command("list_databases", aliases = ["ldbs"], help = "List all database files in the app's files directory")
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
