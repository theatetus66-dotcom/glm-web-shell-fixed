package com.glmwebshell.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One turn of a chat. `capturedBy` records which strategy produced it so the
 * Diagnostics screen can show what is actually working.
 *
 * Per TZ §9.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId"), Index("createdAt"), Index(value = ["contentHash"], unique = true)],
)
@Serializable
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val capturedBy: CaptureSource,
    /** Stable hash of the content; used to deduplicate network + DOM captures. */
    val contentHash: String,
)

@Serializable
enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

@Serializable
enum class CaptureSource { NETWORK, DOM, API }
