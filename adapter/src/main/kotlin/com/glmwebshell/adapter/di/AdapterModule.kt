package com.glmwebshell.adapter.di

import com.glmwebshell.adapter.capabilities.ChatInputFillCapability
import com.glmwebshell.adapter.capabilities.ChatObserveCapability
import com.glmwebshell.adapter.capabilities.ChatSendCapability
import com.glmwebshell.adapter.capabilities.FilesAttachCapability
import com.glmwebshell.adapter.capabilities.NavCapability
import com.glmwebshell.adapter.capabilities.SessionStatusCapability
import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.PageEngineCapabilityContext
import com.glmwebshell.adapter.remote.AdapterLoader
import com.glmwebshell.adapter.remote.AdapterSignatureVerifier
import com.glmwebshell.adapter.remote.BundledAdapterProvider
import com.glmwebshell.pageengine.PageJson
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdapterModule {

    @Provides @Singleton
    fun provideJson(): Json = PageJson.json

    @Provides @Singleton
    fun provideCapabilityContext(impl: PageEngineCapabilityContext): CapabilityContext = impl

    @Provides @Singleton
    fun provideBundledProvider(json: Json): BundledAdapterProvider = BundledAdapterProvider(json)

    @Provides @Singleton
    fun provideSignatureVerifier(json: Json): AdapterSignatureVerifier = AdapterSignatureVerifier(json)

    @Provides @Singleton
    fun provideAdapterLoader(
        json: Json,
        verifier: AdapterSignatureVerifier,
        bundled: BundledAdapterProvider,
        @ApplicationContext ctx: android.content.Context,
    ): AdapterLoader = AdapterLoader(json, verifier).apply {
        loadInitial(bundled.load(com.glmwebshell.adapter.remote.AdapterChannel.STABLE, ctx.assets))
    }
    // ---- Capability multibindings ----------------------------------------
    // Each capability is keyed by its spec id; the runtime looks them up to
    // build (Capability, CapabilitySpec) pairs for the active adapter.

    @Provides @Singleton @IntoMap @StringKey("nav")
    fun provideNavCap(impl: NavCapability): Capability = impl

    @Provides @Singleton @IntoMap @StringKey("chat.observe")
    fun provideChatObserve(impl: ChatObserveCapability): Capability = impl

    @Provides @Singleton @IntoMap @StringKey("chat.input.fill")
    fun provideChatInputFill(impl: ChatInputFillCapability): Capability = impl

    @Provides @Singleton @IntoMap @StringKey("chat.send")
    fun provideChatSend(impl: ChatSendCapability): Capability = impl

    @Provides @Singleton @IntoMap @StringKey("session.status")
    fun provideSessionStatus(impl: SessionStatusCapability): Capability = impl

    @Provides @Singleton @IntoMap @StringKey("files.attach")
    fun provideFilesAttach(impl: FilesAttachCapability): Capability = impl
}

@MapKey
annotation class CapabilityKey(val value: String)
