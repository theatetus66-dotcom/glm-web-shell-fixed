package com.glmwebshell.core.data.di

import android.content.Context
import androidx.room.Room
import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.common.DefaultAppDispatchers
import com.glmwebshell.core.data.db.GlmDatabase
import com.glmwebshell.core.data.db.dao.AdapterStateDao
import com.glmwebshell.core.data.db.dao.ChatDao
import com.glmwebshell.core.data.db.dao.MessageDao
import com.glmwebshell.core.data.db.dao.PromptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {

    @Provides @Singleton
    fun provideDispatchers(): AppDispatchers = DefaultAppDispatchers()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): GlmDatabase =
        Room.databaseBuilder(ctx, GlmDatabase::class.java, GlmDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChatDao(db: GlmDatabase): ChatDao = db.chatDao()
    @Provides fun provideMessageDao(db: GlmDatabase): MessageDao = db.messageDao()
    @Provides fun providePromptDao(db: GlmDatabase): PromptDao = db.promptDao()
    @Provides fun provideAdapterStateDao(db: GlmDatabase): AdapterStateDao = db.adapterStateDao()
}
