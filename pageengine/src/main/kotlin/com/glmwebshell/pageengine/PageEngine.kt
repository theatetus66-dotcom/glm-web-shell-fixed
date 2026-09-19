package com.glmwebshell.pageengine

import com.glmwebshell.core.common.AppResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * The page-engine abstraction (TZ §4).
 *
 * MVP ships with the W1 implementation (`WebViewEngine`). W2 (GeckoView) is a
 * future drop-in: it implements the same surface without rewriting the core.
 *
 * The engine is intentionally a low-level primitive: it loads URLs, injects
 * the bridge, observes traffic and DOM events, and exposes a typed message
 * channel. Capability-level semantics (e.g. "open chat", "fill input") live
 * in `:adapter` and are expressed on top of [PageEngine].
 *
 * Per TZ §6.1.1 the implementation is responsible for: fullscreen WebView,
 * window insets, back gesture, pull-to-refresh, lifecycle/state retention.
 */
interface PageEngine {

    /** Human-readable name shown in Diagnostics (e.g. "W1 · System WebView"). */
    val displayName: String
    /** Engine version stamp surfaced on the Diagnostics screen. */
    val version: String

    /** Lifecycle. Must be paired. */
    fun attach(host: PageEngineHost)
    fun detach()

    /** Load the given URL inside the engine. */
    fun loadUrl(url: String)

    /** Current URL reported by the page. */
    fun currentUrl(): String?

    /** True when the engine has a navigation history it can pop. */
    fun canGoBack(): Boolean
    fun goBack()

    /** Reload current page. */
    fun reload()

    /**
     * Inject the bridge boot script and set up the [addWebMessageListener].
     * Called automatically by the engine on attach. Exposed for adapter
     * self-tests that want to force a fresh injection.
     */
    fun installBridge()

    /**
     * Send a typed message to the page side of the bridge.
     * Per TZ §5.4.1 messages are strictly-typed JSON with a protocol version.
     */
    fun postMessage(message: BridgeMessage.Outgoing)

    /** Stream of messages received from the page. */
    val incoming: SharedFlow<BridgeMessage.Incoming>

    /** Stream of engine-level events (load progress, errors, etc.). */
    val events: SharedFlow<PageEngineEvent>

    /** Evaluate JS in the page. Used only inside adapter strategies (never for arbitrary actions). */
    suspend fun evaluateJs(script: String, timeoutMs: Long = 4_000L): AppResult<String>

    /** Open the system file chooser for `files.attach` capability. */
    fun openFileChooser(callback: FileChooserCallback, accept: List<String>, allowMultiple: Boolean)

    /** Release all internal resources (the WebView itself is recycled by the host Activity). */
    fun destroy()
}

/** Lifecycle owner of the engine. */
interface PageEngineHost {
    /** The Android `Context` for the engine (Activity context). */
    val context: android.content.Context
    /** A View that the engine can add itself to. */
    val container: android.view.ViewGroup
    /** Whether to allow `setWebContentsDebuggingEnabled(true)` (debug builds only). */
    val allowDebug: Boolean
    /** Pull-to-refresh toggle (TZ §6.1.1). */
    val pullToRefreshEnabled: Boolean
    /** Per-origin proxy rules (TZ §6.1.6). */
    val proxyConfig: com.glmwebshell.core.common.AppResult<com.glmwebshell.pageengine.proxy.ProxyConfig>
}

interface FileChooserCallback {
    fun onResult(uris: List<android.net.Uri>)
    fun onCancelled()
}
