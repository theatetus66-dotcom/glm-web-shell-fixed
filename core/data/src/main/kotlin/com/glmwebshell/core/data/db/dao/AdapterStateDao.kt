package com.glmwebshell.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glmwebshell.core.data.db.entity.AdapterStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdapterStateDao {
    @Query("SELECT * FROM adapter_state")
    fun observeAll(): Flow<List<AdapterStateEntity>>

    @Query("SELECT * FROM adapter_state WHERE capabilityId = :id")
    suspend fun getById(id: String): AdapterStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AdapterStateEntity)

    @Query("UPDATE adapter_state SET failCount = failCount + 1 WHERE capabilityId = :id")
    suspend fun incrementFail(id: String)

    @Query("UPDATE adapter_state SET failCount = 0 WHERE capabilityId = :id")
    suspend fun resetFail(id: String)

    @Query("DELETE FROM adapter_state")
    suspend fun deleteAll()
}
