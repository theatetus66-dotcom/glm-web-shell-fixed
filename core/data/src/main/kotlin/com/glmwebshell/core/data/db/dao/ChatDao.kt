package com.glmwebshell.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.glmwebshell.core.data.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeById(id: Long): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getById(id: Long): ChatEntity?

    @Query("""
        SELECT * FROM chats
        WHERE title LIKE '%' || :q || '%'
           OR remoteId LIKE '%' || :q || '%'
           OR url LIKE '%' || :q || '%'
        ORDER BY updatedAt DESC
    """)
    suspend fun search(q: String): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity): Long

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE chats SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAll()
}
