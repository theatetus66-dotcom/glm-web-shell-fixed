package com.glmwebshell.core.data.repository

import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.data.db.dao.AdapterStateDao
import com.glmwebshell.core.data.db.entity.AdapterStateEntity
import com.glmwebshell.core.data.db.entity.AdapterStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdapterStateRepository @Inject constructor(
    private val dao: AdapterStateDao,
    private val dispatchers: AppDispatchers,
) {
    fun observeAll(): Flow<List<AdapterStateEntity>> = dao.observeAll()

    suspend fun set(
        capabilityId: String,
        status: String,
        activeStrategy: String?,
        adapterVersion: Int,
        resetFail: Boolean,
    ) = withContext(dispatchers.io) {
        val current = dao.getById(capabilityId)
        val newFail = when {
            status == AdapterStatus.FAILED -> (current?.failCount ?: 0) + 1
            else -> 0
        }
        dao.upsert(
            AdapterStateEntity(
                capabilityId = capabilityId,
                status = status,
                lastCheckedAt = System.currentTimeMillis(),
                failCount = if (resetFail) 0 else newFail,
                activeStrategy = activeStrategy,
                adapterVersion = adapterVersion,
            )
        )
    }

    suspend fun get(capabilityId: String): AdapterStateEntity? =
        withContext(dispatchers.io) { dao.getById(capabilityId) }

    suspend fun deleteAll() = withContext(dispatchers.io) { dao.deleteAll() }
}
