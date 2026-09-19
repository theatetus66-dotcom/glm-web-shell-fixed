package com.glmwebshell.adapter.core

import com.glmwebshell.core.common.AppResult
import com.glmwebshell.pageengine.BridgeMessage
import com.glmwebshell.pageengine.PageEngine
import com.glmwebshell.pageengine.PageEngineEvent
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [PageEngine] capabilities to the strategy layer without exposing
 * the WebView itself. The capability layer depends only on this interface —
 * which means the W2 (GeckoView) implementation only needs to provide the
 * same `evaluateJs` / `postBridge` / streams.
 */
@Singleton
class PageEngineCapabilityContext @Inject constructor(
    private val engine: PageEngine,
) : CapabilityContext {

    override val pageUrl: String get() = engine.currentUrl() ?: ""
    override val bridgeIncoming: SharedFlow<BridgeMessage.Incoming> get() = engine.incoming
    override val engineEvents: SharedFlow<PageEngineEvent> get() = engine.events

    override suspend fun evaluateJs(script: String, timeoutMs: Long): AppResult<String> =
        engine.evaluateJs(script, timeoutMs)

    override suspend fun postBridge(message: BridgeMessage.Outgoing) {
        engine.postMessage(message)
    }
}
