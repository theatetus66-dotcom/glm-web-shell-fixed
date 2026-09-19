package com.glmwebshell.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.glmwebshell.core.data.db.converter.Converters
import com.glmwebshell.core.data.db.dao.AdapterStateDao
import com.glmwebshell.core.data.db.dao.ChatDao
import com.glmwebshell.core.data.db.dao.MessageDao
import com.glmwebshell.core.data.db.dao.PromptDao
import com.glmwebshell.core.data.db.entity.AdapterStateEntity
import com.glmwebshell.core.data.db.entity.ChatEntity
import com.glmwebshell.core.data.db.entity.MessageEntity
import com.glmwebshell.core.data.db.entity.PromptEntity

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        PromptEntity::class,
        AdapterStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GlmDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun promptDao(): PromptDao
    abstract fun adapterStateDao(): AdapterStateDao

    companion object {
        const val NAME = "glm-web-shell.db"
    }
}
