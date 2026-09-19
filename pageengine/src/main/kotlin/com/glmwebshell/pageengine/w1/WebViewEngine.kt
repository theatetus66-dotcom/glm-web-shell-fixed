package com.glmwebshell.pageengine.w1

import android.net.http.SslError
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.WebViewCompat
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.common.withAppTimeout
import com.glmwebshell.pageengine.BridgeMessage
import com.glmwebshell.pageengine.FileChooserCallback
import com.glmwebshell.pageengine.PageEngine
import com.glmwebshell.pageengine.PageEngineEvent
import com.glmwebshell.pageengine.PageEngineHost
import com.glmwebshell.pageengine.bridge.PageBridge
import com.glmwebshell.pageengine.download.WebViewDownloadListener
import com.glmwebshell.pageengine.security.WebSecurityPolicy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * W1 implementation: a thin wrapper around Android system WebView +
 * AndroidX WebKit. This is the MVP engine per TZ §4.
 *
 * Notes:
 *  - One WebView instance per attached host. The host owns the View lifetime;
 *    the engine tears itself down on [destroy].
 *  - `WebView.setWebContentsDebuggingEnabled` is gated on [PageEngineHost.allowDebug].
 *  - Proxy is applied via [WebViewProxyApplier] in the host scope before
 *    loading the URL.
 *  - External links are routed via Custom Tabs (TZ §6.1.5).
 */
@Singleton
class WebViewEngine @Inject constructor(
    private val bridge: PageBridge,
    private val json: Json,
) : PageEngine {

    override val displayName: String = "W1 · System WebView"
    override val version: String
        get() = "androidx.webkit/${getWebkitVersion()}"

    private var host: PageEngineHost? = null
    private var webView: WebView? = null
    private var downloadListener: WebViewDownloadListener? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val _events = MutableSharedFlow<PageEngineEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PageEngineEvent> = _events.asSharedFlow()

    override val incoming: SharedFlow<BridgeMessage.Incoming> get() = bridge.incoming

    override fun attach(host: PageEngineHost) {
        if (this.host != null) detach()
        this.host = host
        val wv = WebView(host.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            isFocusable = true
            isFocusableInTouchMode = true
            WebSecurityPolicy.apply(settings, host.allowDebug)
        }
        this.webView = wv

        bridge.attach(wv)
        wv.webViewClient = GlmWebViewClient()
        wv.webChromeClient = GlmWebChromeClient()
        this.downloadListener = WebViewDownloadListener(host.context).also { dl ->
            wv.setDownloadListener(dl)
        }
        host.container.addView(wv)
    }

    override fun detach() {
        val host = this.host ?: return
        val wv = webView ?: return
        bridge.detach(wv)
        host.container.removeView(wv)
        runCatching { wv.stopLoading() }
        (wv.parent as? ViewGroup)?.removeView(wv)
        this.webView = null
        this.host = null
        this.downloadListener = null
        this.fileCallback = null
    }

    override fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    override fun currentUrl(): String? = webView?.url

    override fun canGoBack(): Boolean = webView?.canGoBack() == true
    override fun goBack() { webView?.goBack() }

    override fun reload() { webView?.reload() }

    override fun installBridge() {
        webView?.let { bridge.detach(it); bridge.attach(it) }
    }

    override fun postMessage(message: BridgeMessage.Outgoing) = bridge.post(message)

    override suspend fun evaluateJs(script: String, timeoutMs: Long): AppResult<String> {
        val wv = webView ?: return AppResult.err(ErrorCode.Unknown, "WebView is null")
        return withAppTimeout(timeoutMs, "evaluateJs") {
            val raw: String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(script) { result ->
                    if (cont.isActive) cont.resume(result) { }
                }
            }
            raw ?: ""
        }
    }

    override fun openFileChooser(callback: FileChooserCallback, accept: List<String>, allowMultiple: Boolean) {
        // The actual file chooser is invoked through the host's ActivityResultLauncher
        // registered in :features:chat. The WebChromeClient.onShowFileChooser
        // path is what the page actually triggers; this method is a fallback
        // that the engine itself never invokes today.
        Logger.w(TAG, "openFileChooser called directly — no-op. Use the host launcher instead.")
    }

    override fun destroy() {
        detach()
    }

    private fun getWebkitVersion(): String {
        val pkg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WebView.getCurrentWebViewPackage()
        } else {
            host?.context?.let { WebViewCompat.getCurrentWebViewPackage(it) }
        }
        return pkg?.versionName ?: "unknown"
    }

    // ---- Inner WebViewClient --------------------------------------------------

    private inner class GlmWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url?.toString() ?: return false
            if (WebSecurityPolicy.shouldLoad(url)) return false  // let WebView handle it
            // External link → Custom Tabs (TZ §6.1.5).
            _events.tryEmit(PageEngineEvent.ExternalNavigation(url))
            val host = host ?: return true
            try {
                val tabs = CustomTabsIntent.Builder().build()
                tabs.intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                tabs.launchUrl(host.context, android.net.Uri.parse(url))
            } catch (ex: Throwable) {
                Logger.w(TAG, "Custom Tabs launch failed: ${ex.message}")
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            _events.tryEmit(PageEngineEvent.LoadStarted(url))
        }

        override fun onPageFinished(view: WebView, url: String) {
            _events.tryEmit(PageEngineEvent.LoadFinished(url))
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) {
                _events.tryEmit(
                    PageEngineEvent.LoadFailed(
                        request.url?.toString() ?: "",
                        error.errorCode,
                        error.description?.toString() ?: "load error",
                    )
                )
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Per TZ §7 we never proceed on SSL errors silently.
            handler.cancel()
            Logger.w("WebViewEngine", "SSL error cancelled: ${error.primaryError}")
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame && errorResponse.statusCode in 500..599) {
                _events.tryEmit(
                    PageEngineEvent.LoadFailed(
                        request.url?.toString() ?: "",
                        errorResponse.statusCode,
                        errorResponse.reasonPhrase ?: "http error",
                    )
                )
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
            shouldOverrideUrlLoading(view, object : WebResourceRequest {
                override fun getUrl() = android.net.Uri.parse(url)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
            })
    }

    // ---- Inner WebChromeClient ------------------------------------------------

    private inner class GlmWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            _events.tryEmit(PageEngineEvent.LoadProgress(newProgress))
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            fileCallback = callback
            val host = host ?: return false
            // We hand off to the host's ActivityResultLauncher (registered in
            // :features:chat). The accept types and multi-select flag are
            // forwarded so the host can construct the right intent.
            val launcher = (host as? WebFileChooserHost)?.fileChooserLauncher
            if (launcher == null) {
                callback.onReceiveValue(null)
                return false
            }
            val intent = params.createIntent().apply {
                type = params.acceptTypes?.firstOrNull()?.ifBlank { "*/*" } ?: "*/*"
                putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            launcher.launch(intent)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Default behaviour: do not auto-grant; the host may decide.
            request.deny()
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: android.webkit.SafeBrowsingResponse,
        ) {
            // Per TZ §7: never silently proceed on a Safe Browsing hit.
            // Send the user back to the previous page and show Google's
            // interstitial so they understand what was blocked.
            Logger.w(TAG, "Safe Browsing hit (threatType=$threatType) on ${request.url}; backToSafety")
            try {
                callback.backToSafety(true)
            } catch (t: Throwable) {
                Logger.w(TAG, "backToSafety not callable: ${t.message}")
                callback.showInterstitial(true)
            }
        }
    }

    /** Implemented by the host Activity that owns the file-chooser launcher. */
    interface WebFileChooserHost {
        val fileChooserLauncher: ActivityResultLauncher<android.content.Intent>
    }

    /** Called by the host after the file chooser returns. */
    fun deliverFileChooserResult(uris: Array<Uri>?) {
        val cb = fileCallback ?: return
        fileCallback = null
        cb.onReceiveValue(uris)
    }

    companion object { private const val TAG = "WebViewEngine" }
}
