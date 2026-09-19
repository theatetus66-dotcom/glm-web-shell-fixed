package com.glmwebshell.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * User-curated prompt template. Per TZ §6.3.4 — prompts library.
 */
@Entity(tableName = "prompts")
@Serializable
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val isPerson: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
