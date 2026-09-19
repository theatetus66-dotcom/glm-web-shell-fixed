package com.glmwebshell.pageengine.bridge

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.common.Constants
import com.glmwebshell.core.common.Logger
import com.glmwebshell.pageengine.BridgeMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageBridge @Inject constructor(
    private val json: Json,
) {
    private val _incoming = MutableSharedFlow<BridgeMessage.Incoming>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val incoming: SharedFlow<BridgeMessage.Incoming> = _incoming.asSharedFlow()

    private var replyProxy: JavaScriptReplyProxy? = null
    private var bootScriptHandle: ScriptHandler? = null
    private var attachedWebView: WebView? = null

    fun attach(webView: WebView) {
        if (attachedWebView != null && attachedWebView !== webView) {
            detach(attachedWebView!!)
        }
        attachedWebView = webView

        val boot = BridgeBootScript.JS.replace(
            "__GLM_BRIDGE_PROTOCOL_VERSION__",
            Constants.BRIDGE_PROTOCOL_VERSION.toString(),
        )
        bootScriptHandle = WebViewCompat.addDocumentStartJavaScript(
            webView, boot, AllowedOrigins.bridgeOriginRules,
        )
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            AllowedOrigins.bridgeOriginRules,
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: android.net.Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy,
                ) {
                    this@PageBridge.replyProxy = replyProxy
                    handleIncoming(message.data ?: "")
                }
            },
        )
    }

    fun detach(webView: WebView) {
        runCatching {
            WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
            bootScriptHandle?.remove()
        }
        bootScriptHandle = null
        replyProxy = null
        attachedWebView = null
    }

    fun post(message: BridgeMessage.Outgoing) {
        val payload = runCatching {
            json.encodeToString(BridgeMessage.Outgoing.serializer(), message)
        }.getOrElse {
            Logger.w(TAG, "Failed to serialize outgoing message: ${it.message}")
            return
        }
        if (payload.length > Constants.BRIDGE_MAX_MESSAGE_BYTES) {
            Logger.w(TAG, "Outgoing message too large: ${payload.length} bytes")
            return
        }
        replyProxy?.postMessage(payload)
    }

    private fun handleIncoming(raw: String) {
        if (raw.length > Constants.BRIDGE_MAX_MESSAGE_BYTES) {
            Logger.w(TAG, "Incoming message too large: ${raw.length} bytes; dropping")
            return
        }
        val parsed = runCatching {
            json.decodeFromString(BridgeMessage.Incoming.serializer(), raw)
        }.getOrElse {
            Logger.w(TAG, "Failed to parse incoming: ${it.message}")
            return
        }
        _incoming.tryEmit(parsed)
    }

    companion object {
        private const val TAG = "PageBridge"
        const val BRIDGE_NAME = "__glmBridgeChannel"
    }
}
