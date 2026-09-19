package com.glmwebshell.features.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glmwebshell.adapter.capabilities.ChatObserveCapability
import com.glmwebshell.adapter.capabilities.NavCapability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.health.AdapterRuntime
import com.glmwebshell.adapter.health.CapabilityHealthMonitor
import com.glmwebshell.adapter.remote.AdapterLoader
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.data.db.entity.ChatEntity
import com.glmwebshell.core.data.db.entity.ChatSource
import com.glmwebshell.core.data.repository.ChatRepository
import com.glmwebshell.core.data.repository.MessageRepository
import com.glmwebshell.core.data.repository.PromptRepository
import com.glmwebshell.core.data.settings.SettingsRepository
import com.glmwebshell.pageengine.PageEngine
import com.glmwebshell.pageengine.PageEngineEvent
import com.glmwebshell.pageengine.proxy.ProxyConfig as EngineProxyConfig
import com.glmwebshell.pageengine.proxy.WebViewProxyApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    val engine: PageEngine,
    private val runtime: AdapterRuntime,
    private val monitor: CapabilityHealthMonitor,
    private val capContext: CapabilityContext,
    private val settings: SettingsRepository,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val prompts: PromptRepository,
    private val adapterLoader: AdapterLoader,
    private val chatObserve: ChatObserveCapability,
    private val navCapability: NavCapability,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val healthSnapshot: StateFlow<Map<String, com.glmwebshell.adapter.health.CapabilityHealth>> =
        monitor.states.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val useMockPage: StateFlow<Boolean> =
        settings.useMockPage.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val proxyEnabled: StateFlow<Boolean> =
        settings.proxyConfig.map { it.enabled }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var observeJob: Job? = null
    private var activeChatId: Long? = null

    init {
        viewModelScope.launch {
            engine.events.collect { ev -> handleEngineEvent(ev) }
        }
        viewModelScope.launch {
            settings.proxyConfig.collect { config ->
                val mapped = EngineProxyConfig(
                    enabled = config.enabled,
                    type = when (config.type) {
                        com.glmwebshell.core.data.settings.ProxyType.HTTP ->
                            com.glmwebshell.pageengine.proxy.ProxyType.HTTP
                        com.glmwebshell.core.data.settings.ProxyType.SOCKS ->
                            com.glmwebshell.pageengine.proxy.ProxyType.SOCKS
                    },
                    host = config.host,
                    port = config.port,
                    username = config.username,
                    password = config.password,
                )
                WebViewProxyApplier.apply(mapped)
            }
        }
        viewModelScope.launch {
            navCapability.navigationRequests.collect { url ->
                engine.loadUrl(url)
                _uiState.update { it.copy(currentUrl = url) }
            }
        }
    }

    fun loadInitialPage() {
        val mock = useMockPage.value
        val url = if (mock) "file:///android_asset/mock-chat.html" else AllowedOrigins.CHAT_ORIGIN
        engine.loadUrl(url)
        _uiState.update { it.copy(currentUrl = url) }
    }

    fun loadUrl(url: String) {
        engine.loadUrl(url)
        _uiState.update { it.copy(currentUrl = url) }
    }

    fun applySharedText(text: String?) {
        if (text.isNullOrBlank()) return
        _uiState.update { it.copy(pendingInsert = text) }
    }

    private fun handleEngineEvent(ev: PageEngineEvent) {
        when (ev) {
            is PageEngineEvent.LoadStarted ->
                _uiState.update { it.copy(isLoading = true, currentUrl = ev.url, offline = false) }
            is PageEngineEvent.LoadProgress ->
                _uiState.update { it.copy(progress = ev.percent) }
            is PageEngineEvent.LoadFinished -> {
                _uiState.update {
                    it.copy(isLoading = false, progress = 100, offline = false, currentUrl = ev.url)
                }
                viewModelScope.launch {
                    ensureChatAndObserve(ev.url)
                    runCatching { runtime.runSelfTests(capContext) }
                        .onFailure { Logger.w(TAG, "self-test crashed: ${it.message}", it) }
                }
            }
            is PageEngineEvent.LoadFailed ->
                _uiState.update { it.copy(isLoading = false, offline = true) }
            is PageEngineEvent.ExternalNavigation ->
                _uiState.update { it.copy(lastExternalUrl = ev.url) }
            is PageEngineEvent.SessionStateChanged ->
                _uiState.update { it.copy(signedIn = ev.signedIn) }
            PageEngineEvent.PullToRefreshTriggered -> engine.reload()
        }
    }

    /**
     * Create or reuse a ChatEntity for the current URL and start observing
     * bridge messages so history is populated.
     */
    private suspend fun ensureChatAndObserve(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val now = System.currentTimeMillis()
        val remoteId = extractRemoteId(url)
        val existing = if (remoteId != null) {
            chats.search(remoteId).firstOrNull { it.remoteId == remoteId }
        } else {
            chats.search(url).firstOrNull { it.url == url }
        }
        val chatId = if (existing != null) {
            chats.touch(existing.id, now)
            existing.id
        } else {
            chats.upsert(
                ChatEntity(
                    remoteId = remoteId,
                    url = url,
                    title = remoteId?.let { "Chat $it" } ?: url.substringAfter("://").take(48),
                    createdAt = now,
                    updatedAt = now,
                    source = ChatSource.WEB,
                )
            )
        }
        activeChatId = chatId
        _uiState.update { it.copy(activeChatId = chatId) }

        observeJob?.cancel()
        val observeSpec = runCatching {
            adapterLoader.current().manifest.capabilities
                .firstOrNull { it.id == ChatObserveCapability.ID }
        }.getOrNull()
        if (observeSpec != null) {
            observeJob = viewModelScope.launch {
                runCatching {
                    chatObserve.observe(capContext, chatId, observeSpec)
                }.onFailure {
                    Logger.w(TAG, "observe stopped: ${it.message}", it)
                }
            }
        }
    }

    private fun extractRemoteId(url: String): String? {
        // Matches https://chat.z.ai/c/<id> style paths.
        val m = Regex("""/c/([A-Za-z0-9_-]+)""").find(url)
        return m?.groupValues?.getOrNull(1)
    }

    fun openChatById(chatId: Long) {
        viewModelScope.launch {
            val chat = chats.getById(chatId) ?: return@launch
            engine.loadUrl(chat.url)
            _uiState.update { it.copy(currentUrl = chat.url, activeChatId = chat.id) }
            activeChatId = chat.id
        }
    }

    fun reload() = engine.reload()
    fun goBack() {
        if (engine.canGoBack()) engine.goBack()
    }

    fun onPromptInsert(text: String) {
        _uiState.update { it.copy(pendingInsert = text) }
    }

    fun onInsertHandled() = _uiState.update { it.copy(pendingInsert = null) }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        engine.destroy()
    }

    companion object { private const val TAG = "ChatViewModel" }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val offline: Boolean = false,
    val currentUrl: String = "",
    val lastExternalUrl: String? = null,
    val signedIn: Boolean = false,
    val pendingInsert: String? = null,
    val activeChatId: Long? = null,
)

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
