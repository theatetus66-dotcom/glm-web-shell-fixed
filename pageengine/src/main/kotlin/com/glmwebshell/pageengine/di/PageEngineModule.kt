package com.glmwebshell.pageengine.di

import com.glmwebshell.pageengine.PageEngine
import com.glmwebshell.pageengine.w1.WebViewEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PageEngineModule {
    @Binds @Singleton
    abstract fun bindPageEngine(impl: WebViewEngine): PageEngine
}
