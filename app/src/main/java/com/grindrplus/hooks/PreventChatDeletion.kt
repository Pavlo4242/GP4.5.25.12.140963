package com.grindrplus.hooks

import android.database.sqlite.SQLiteDatabase
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import kotlin.collections.joinToString
import kotlin.jvm.java

class PreventChatDeletion : Hook(
    "Prevent Chat Deletion",
    "Prevents chats from being deleted from the local database."
) {
    // A set of all chat-related tables to protect from deletion.
    private val tablesToProtect = setOf(
        "chat_messages",
        "chat_conversations",
        "chat_conversation_participants",
        "chat_conversation_previews"
    )

    // This is the network service class responsible for making the API call to delete messages.
    // We intercept the call here to prevent it from ever reaching Grindr's servers.
    private val chatRestService = "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"

    // This is the specific method that sends the deletion request.
    private val deleteMessagesMethod = "deleteMessages"


    override fun init() {
        // We hook the main delete method of SQLiteDatabase.
        SQLiteDatabase::class.java.hook(
            "delete",
            HookStage.BEFORE
        ) { param ->
            // First, check if our feature is enabled in the settings.
            if (!isHookEnabled()) return@hook

            // The first argument is the table name.
            val table = param.arg<String>(0)

            // Check if the table is one we want to protect.
            if (table in tablesToProtect) {
                logi("Intercepted a delete request for the '$table' table.")

                // Log the details for debugging purposes.
                val whereClause = param.argNullable<String>(1)
                val whereArgs = param.argNullable<Array<String>>(2)
                logi("WHERE clause: $whereClause, Args: ${whereArgs?.joinToString()}")

                // By setting a result, we prevent the original method from being called.
                // Returning 0 means "0 rows were deleted".
                param.setResult(0)

                logi("Prevented deletion from '$table'.")
            }
        }
    }
}