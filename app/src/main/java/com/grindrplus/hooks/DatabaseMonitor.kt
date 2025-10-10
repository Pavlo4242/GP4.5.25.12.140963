package com.grindrplus.hooks

import android.content.ContentValues
import com.grindrplus.core.SqlTransactionLogger
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import kotlinx.coroutines.runBlocking


class DatabaseMonitor : Hook("Database Monitor", "Logs all database write transactions.") {
    override fun init() {
        val dbClass = findClass("android.database.sqlite.SQLiteDatabase")

        val objectArrayClass = Class.forName("[Ljava.lang.Object;")
        val stringArrayClass = Class.forName("[Ljava.lang.String;")

        // Hook for execSQL(String)
        try {
            dbClass.hook("execSQL", String::class.java, HookStage.BEFORE) { param ->
                val sql = param.args[0] as String
                runBlocking { SqlTransactionLogger.log(sql) }
            }
        } catch (e: Throwable) {
            loge("Failed to hook execSQL(String): ${e.message}")
        }

        // Hook for execSQL(String, Object[])
        try {
            dbClass.hook("execSQL", String::class.java, objectArrayClass, HookStage.BEFORE) { param ->
                val sql = param.args[0] as String
                val args = param.args[1] as Array<Any?>
                runBlocking { SqlTransactionLogger.log(sql, args) }
            }
        } catch (e: Throwable) {
            loge("Failed to hook execSQL(String, Object[]): ${e.message}")
        }

        // Hook for insert(String, String, ContentValues)
        try {
            dbClass.hook("insert", String::class.java, String::class.java, ContentValues::class.java, HookStage.BEFORE) { param ->
                val table = param.args[0] as String
                val values = param.args[2] as ContentValues
                val sql = "INSERT INTO $table (${values.keySet().joinToString(", ")}) VALUES (${values.keySet().map { "?" }.joinToString(", ")});"
                runBlocking { SqlTransactionLogger.log(sql, values.keySet().map { values.get(it) }.toTypedArray()) }
            }
        } catch (e: Throwable) {
            loge("Failed to hook insert(...): ${e.message}")
        }

        // Hook for update(String, ContentValues, String, String[])
        try {
            dbClass.hook("update", String::class.java, ContentValues::class.java, String::class.java, stringArrayClass, HookStage.BEFORE) { param ->
                val table = param.args[0] as String
                val values = param.args[1] as ContentValues
                val whereClause = param.args[2] as? String
                val whereArgs = param.args[3] as? Array<String>

                val setClause = values.keySet().joinToString(", ") { "$it = ?" }
                val sql = "UPDATE $table SET $setClause WHERE $whereClause;"
                val allArgs = (values.keySet().map { values.get(it) } + whereArgs.orEmpty()).toTypedArray()
                runBlocking { SqlTransactionLogger.log(sql, allArgs) }
            }
        } catch (e: Throwable) {
            loge("Failed to hook update(...): ${e.message}")
        }

        // Hook for updateWithOnConflict(String, ContentValues, String, String[], int)
        try {
            dbClass.hook("updateWithOnConflict", String::class.java, ContentValues::class.java, String::class.java, stringArrayClass, Int::class.javaPrimitiveType, HookStage.BEFORE) { param ->
                val table = param.args[0] as String
                val values = param.args[1] as ContentValues
                val whereClause = param.args[2] as? String
                val whereArgs = param.args[3] as? Array<String>

                val setClause = values.keySet().joinToString(", ") { "$it = ?" }
                val sql = "UPDATE $table SET $setClause WHERE $whereClause;"
                val allArgs = (values.keySet().map { values.get(it) } + whereArgs.orEmpty()).toTypedArray()
                runBlocking { SqlTransactionLogger.log(sql, allArgs) }
            }
        } catch(e: Throwable) {
            loge("Failed to hook updateWithOnConflict(...): ${e.message}")
        }

        // Hook for delete(String, String, String[])
        try {
            dbClass.hook("delete", String::class.java, String::class.java, stringArrayClass, HookStage.BEFORE) { param ->
                val table = param.args[0] as String
                val whereClause = param.args[1] as? String
                val whereArgs = param.args[2] as? Array<String>

                val sql = "DELETE FROM $table WHERE $whereClause;"
                runBlocking { SqlTransactionLogger.log(sql, whereArgs) }
            }
        } catch (e: Throwable) {
            loge("Failed to hook delete(...): ${e.message}")
        }
    }
}