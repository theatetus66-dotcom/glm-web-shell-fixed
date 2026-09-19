package com.glmwebshell.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A chat conversation seen by the app, either captured from the web client
 * (source=WEB) or created via the API mode (source=API).
 *
 * Per TZ §9 data model.
 */
@Entity(
    tableName = "chats",
    indices = [
        Index("remoteId"),
        Index("url"),
        Index("updatedAt"),
        Index("pinned"),
    ],
)
@Serializable
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val remoteId: String?,
    val url: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val source: ChatSource = ChatSource.WEB,
)

@Serializable
enum class ChatSource { WEB, API }
