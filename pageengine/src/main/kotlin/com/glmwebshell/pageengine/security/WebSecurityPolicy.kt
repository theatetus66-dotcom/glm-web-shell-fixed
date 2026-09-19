package com.glmwebshell.pageengine.security

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.common.Logger

/**
 * Applies the TZ §7 security baseline to a [WebSettings] instance:
 *
 * - `file://` / `content://` access disabled;
 * - mixed content blocked;
 * - Safe Browsing on (when supported);
 * - DOM storage enabled (chat sites commonly rely on it);
 * - JavaScript enabled (the bridge is JavaScript-based).
 *
 * `setWebContentsDebuggingEnabled(true)` is gated on a flag that the host
 * sets from `BuildConfig.DEBUG` only (TZ §7).
 *
 * Safe Browsing hit handling is implemented inside the WebView's
 * `WebViewClient` via `onSafeBrowsingHit(...)` — see
 * `WebViewEngine.GlmWebViewClient` for the override. There is no
 * `WebView.setSafeBrowsingResponseHandler` API; the AndroidX docs require
 * the WebViewClient override.
 */
object WebSecurityPolicy {

    fun apply(settings: WebSettings, allowDebug: Boolean) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.allowFileAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = buildString {
            append(settings.userAgentString)
            append(" GLMWebShell/1.0 (unofficial)")
        }

        android.webkit.WebView.setWebContentsDebuggingEnabled(allowDebug)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }
    }

    /** Whether the given URL is allowed to load inside our WebView. */
    fun shouldLoad(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.startsWith("file://") || url.startsWith("content://")) return false
        return AllowedOrigins.isInternal(url)
    }
}
