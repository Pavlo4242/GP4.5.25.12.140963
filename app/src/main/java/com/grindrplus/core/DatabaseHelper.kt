package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.grindrplus.GrindrPlus

object DatabaseHelper {
    private fun getGrindrPlusDatabase(): SupportSQLiteDatabase {
        return GrindrPlus.database.openHelper.writableDatabase
    }

    // NEW: Query GrindrPlus database
    fun queryGrindrPlus(query: String, args: Array<String>? = null): List<Map<String, Any>> {
        val database: SupportSQLiteDatabase = getGrindrPlusDatabase()
        val sqlQuery: SupportSQLiteQuery = if (args != null) {
            SimpleSQLiteQuery(query, args)
        } else {
            SimpleSQLiteQuery(query)
        }

        val cursor = database.query(sqlQuery) as Cursor
        return cursor.use {
            buildList {
                if (cursor.moveToFirst()) {
                    do {
                        val row: Map<String, Any> = buildMap {
                            for (i in 0 until cursor.columnCount) {
                                val columnName = cursor.getColumnName(i)
                                put(columnName, when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(i)
                                    Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(i)
                                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                                    Cursor.FIELD_TYPE_NULL -> "NULL"
                                    else -> "UNKNOWN"
                                })
                            }
                        }
                        add(row)
                    } while (cursor.moveToNext())
                }
            }
        }
    }


    // NEW: Execute on GrindrPlus database
    fun executeGrindrPlus(sql: String, args: Array<Any?>? = null) {
        val database = getGrindrPlusDatabase()
        if (args != null) {
            database.execSQL(sql, args)
        } else {
            database.execSQL(sql)
        }
    }

    // NEW: Helper for single integer queries
    fun querySingleInt(query: String): Int =
        getDatabase().use { database ->
            database.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }

    private fun getDatabase(): SQLiteDatabase {
        val context = GrindrPlus.context
        val databases = context.databaseList()
        val grindrUserDb = databases.firstOrNull {
            it.contains("grindr_user") && it.endsWith(".db") }
            ?: throw IllegalStateException("No Grindr user database found!").also {
                Logger.apply {
                    e(it.message!!)
                    writeRaw("Available databases:\n" +
                            "${databases.joinToString("\n") { "  $it" }}\n")
                }
            }
        return context.openOrCreateDatabase(grindrUserDb.also {
            Logger.d("Using database: $it") }, Context.MODE_PRIVATE, null)
    }

    fun query(query: String, args: Array<String>? = null): List<Map<String, Any>> =
        getDatabase().use { database ->
            database.rawQuery(query, args).use { cursor ->
                buildList {
                    if (cursor.moveToFirst()) {
                        do {
                            val row = buildMap<String, Any> {
                                cursor.columnNames.forEach { column ->
                                    val idx = cursor.getColumnIndexOrThrow(column)
                                    put(column, when (cursor.getType(idx)) {
                                        Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(idx)
                                        Cursor.FIELD_TYPE_FLOAT   -> cursor.getFloat(idx)
                                        Cursor.FIELD_TYPE_STRING  -> cursor.getString(idx)
                                        Cursor.FIELD_TYPE_BLOB    -> cursor.getBlob(idx)
                                        Cursor.FIELD_TYPE_NULL    -> "NULL"
                                        else                      -> "UNKNOWN"
                                    })
                                }
                            }
                            add(row)
                        } while (cursor.moveToNext())
                    }
                }
            }
        }


    fun insert(table: String, values: ContentValues): Long {
        val database = getDatabase()
        val rowId = database.insert(table, null, values)
        database.close()
        return rowId
    }

    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int {
        val database = getDatabase()
        val rowsAffected = database.update(table, values, whereClause, whereArgs)
        database.close()
        return rowsAffected
    }

    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int {
        val database = getDatabase()
        val rowsDeleted = database.delete(table, whereClause, whereArgs)
        database.close()
        return rowsDeleted
    }

    fun execute(sql: String) {
        val database = getDatabase()
        database.execSQL(sql)
        database.close()
    }

    fun getRecentDeletions(limit: Int = 50): List<Map<String, Any>> {
        return query("""
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
    """, emptyArray())
    }

    fun getDeletionStats(): List<Map<String, Any>> {
        return query("""
        SELECT 
            original_table_name,
            COUNT(*) as deletion_count,
            datetime(MAX(deleted_at), 'unixepoch') as latest_deletion
        FROM universal_deleted_data 
        GROUP BY original_table_name 
        ORDER BY deletion_count DESC
    """, emptyArray())
    }

    fun clearOldDeletions(daysToKeep: Int = 30) {
        execute("""
        DELETE FROM universal_deleted_data 
        WHERE deleted_at < strftime('%s','now') - (${daysToKeep} * 24 * 60 * 60)
    """)
    }
}
