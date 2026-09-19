package com.glmwebshell.core.data.repository

import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.data.db.dao.ChatDao
import com.glmwebshell.core.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Read/write access to the chats table (TZ §6.3.1, §6.3.6). */
@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatDao,
    private val dispatchers: AppDispatchers,
) {
    fun observeAll(): Flow<List<ChatEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<ChatEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ChatEntity? = withContext(dispatchers.io) {
        dao.getById(id)
    }

    suspend fun search(query: String): List<ChatEntity> = withContext(dispatchers.io) {
        dao.search(query.trim())
    }

    /** Insert a chat keyed by remoteId (or URL when remoteId is null) and return the row id. */
    suspend fun upsert(chat: ChatEntity): Long = withContext(dispatchers.io) {
        dao.upsert(chat)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(dispatchers.io) {
        dao.setPinned(id, pinned)
    }

    suspend fun touch(id: Long, ts: Long = System.currentTimeMillis()) = withContext(dispatchers.io) {
        dao.touch(id, ts)
    }

    suspend fun delete(id: Long) = withContext(dispatchers.io) {
        dao.deleteById(id)
    }

    suspend fun deleteAll() = withContext(dispatchers.io) { dao.deleteAll() }
}
