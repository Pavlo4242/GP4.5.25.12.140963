package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.persistence.model.ConversationMetadata
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConversationTracker : Hook(
    "Conversation Tracker",
    "Tracks all conversations and messages across multiple accounts"
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val chatMessage = "com.grindrapp.android.chat.model.ChatMessage"
    private val chatConversation = "com.grindrapp.android.persistence.model.ChatConversation"

    override fun init() {
        // Hook message insertion
        findClass("com.grindrapp.android.persistence.dao.ChatMessagesDao")
            .hook("insertMessage", HookStage.AFTER) { param ->
                if (param.args().isNotEmpty()) {
                    val message = param.arg<Any>(0)
                    trackMessage(message)
                }
            }

        // Hook conversation updates
        findClass("com.grindrapp.android.persistence.dao.ChatConversationsDao")
            .hook("upsertConversation", HookStage.AFTER) { param ->
                if (param.args().isNotEmpty()) {
                    val conversation = param.arg<Any>(0)
                    trackConversation(conversation)
                }
            }
    }

    private fun trackMessage(message: Any) {
        scope.launch {
            try {
                val conversationId = getObjectField(message, "conversationId") as? String ?: return@launch
                val timestamp = getObjectField(message, "timestamp") as? Long ?: System.currentTimeMillis()
                
                val dao = GrindrPlus.database.conversationTrackingDao()
                
                // Update message count and timestamp
                dao.incrementMessageCount(conversationId, timestamp)
                
                // Update first message timestamp if this is the first message
                val existing = dao.getConversation(conversationId)
                if (existing != null && existing.firstMessageTimestamp == null) {
                    dao.upsertConversation(
                        existing.copy(firstMessageTimestamp = timestamp)
                    )
                }
                
                logi("Tracked message in conversation $conversationId")
            } catch (e: Exception) {
                loge("Failed to track message: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }

    private fun trackConversation(conversation: Any) {
        scope.launch {
            try {
                val conversationId = getObjectField(conversation, "conversationId") as? String ?: return@launch
                val name = getObjectField(conversation, "name") as? String
                val lastMessageTimestamp = getObjectField(conversation, "lastMessageTimestamp") as? Long
                
                // Extract profile ID from conversation ID (format: "myId:theirId")
                val profileId = conversationId.split(":").getOrNull(1) ?: return@launch
                
                val dao = GrindrPlus.database.conversationTrackingDao()
                val existing = dao.getConversation(conversationId)
                
                val metadata = ConversationMetadata(
                    conversationId = conversationId,
                    profileId = profileId,
                    displayName = name,
                    lastMessageTimestamp = lastMessageTimestamp,
                    firstMessageTimestamp = existing?.firstMessageTimestamp,
                    messageCount = existing?.messageCount ?: 0,
                    isFavorite = false, // Can be updated from profile data
                    isBlocked = false, // Can be updated from blocks
                    accountPackage = GrindrPlus.packageName
                )
                
                dao.upsertConversation(metadata)
                logi("Tracked conversation $conversationId")
                
                // Record profile sighting
                GrindrPlus.database.profileAccountMappingDao()
                    .recordProfileSighting(profileId, GrindrPlus.packageName)
            } catch (e: Exception) {
                loge("Failed to track conversation: ${e.message}")
                Logger.writeRaw(e.stackTraceToString())
            }
        }
    }
}
