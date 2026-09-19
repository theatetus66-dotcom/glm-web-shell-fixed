package com.glmwebshell.core.data.repository

import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.data.db.dao.PromptDao
import com.glmwebshell.core.data.db.entity.PromptEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptRepository @Inject constructor(
    private val dao: PromptDao,
    private val dispatchers: AppDispatchers,
) {
    fun observeAll(): Flow<List<PromptEntity>> = dao.observeAll()
    suspend fun getById(id: Long): PromptEntity? = withContext(dispatchers.io) { dao.getById(id) }
    suspend fun upsert(prompt: PromptEntity): Long = withContext(dispatchers.io) { dao.upsert(prompt) }
    suspend fun delete(id: Long) = withContext(dispatchers.io) { dao.delete(id) }

    /** Seed library on first install with a small, safe starter set. */
    suspend fun seedIfEmpty() = withContext(dispatchers.io) {
        if (dao.count() > 0) return@withContext
        val seeds = listOf(
            PromptEntity(
                title = "Summarize",
                text = "Summarize the previous answer in 3 bullets.",
                tags = listOf("summary"),
            ),
            PromptEntity(
                title = "Code review",
                text = "Review the following code for bugs, style, and security. List issues by severity.",
                tags = listOf("code"),
            ),
            PromptEntity(
                title = "Translate to EN",
                text = "Translate the following text to English. Preserve tone.",
                tags = listOf("translate"),
            ),
            PromptEntity(
                title = "Reasoning persona",
                text = "You are a careful reasoning assistant. Think step-by-step before answering. Always show your reasoning before the final answer.",
                tags = listOf("persona"),
                isPerson = true,
            ),
        )
        seeds.forEach { dao.upsert(it) }
    }
}
