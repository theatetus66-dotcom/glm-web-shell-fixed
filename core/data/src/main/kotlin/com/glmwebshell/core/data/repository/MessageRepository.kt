package com.glmwebshell.core.data.repository

import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.data.db.dao.MessageDao
import com.glmwebshell.core.data.db.entity.CaptureSource
import com.glmwebshell.core.data.db.entity.MessageEntity
import com.glmwebshell.core.data.db.entity.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val dao: MessageDao,
    private val dispatchers: AppDispatchers,
) {
    fun observeByChat(chatId: Long): Flow<List<MessageEntity>> = dao.observeByChat(chatId)

    suspend fun getByChat(chatId: Long): List<MessageEntity> = withContext(dispatchers.io) {
        dao.getByChat(chatId)
    }

    suspend fun lastInChat(chatId: Long): MessageEntity? = withContext(dispatchers.io) {
        dao.lastInChat(chatId)
    }

    suspend fun upsert(
        chatId: Long,
        role: MessageRole,
        content: String,
        capturedBy: CaptureSource,
        createdAt: Long = System.currentTimeMillis(),
    ): Long = withContext(dispatchers.io) {
        val entity = MessageEntity(
            chatId = chatId,
            role = role,
            content = content,
            createdAt = createdAt,
            capturedBy = capturedBy,
            contentHash = sha1(content + ":" + chatId),
        )
        dao.insert(entity)
    }

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
