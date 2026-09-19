package com.glmwebshell.core.common

/**
 * Origins that the app is allowed to load inside its own WebView. Everything
 * else is dispatched to the system browser / Custom Tabs (TZ §7 security and
 * §6.1.5 — external links).
 *
 * The list is deliberately narrow and only contains the hostnames the GLM
 * web chat actually needs. Update during Stage 0 (reconnaissance) per TZ §11.
 */
object AllowedOrigins {

    /** The chat surface itself. */
    const val CHAT_HOST = "chat.z.ai"
    const val CHAT_ORIGIN = "https://$CHAT_HOST"

    /** Auth surface (z.ai root + accounts). */
    const val AUTH_HOST = "z.ai"
    const val AUTH_ORIGIN = "https://$AUTH_HOST"

    /** The full allow-list the WebView consults. */
    val hostAllowList: List<String> = listOf(
        CHAT_HOST,
        AUTH_HOST,
        "accounts.google.com",          // OAuth redirect chain (best-effort)
        "login.microsoftonline.com",
        "github.com",
    )

    /**
     * `true` if the given URL is on an origin we may render inside our own
     * WebView. Used by WebViewClient.shouldOverrideUrlLoading.
     */
    fun isInternal(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host ?: return false
        return hostAllowList.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Origins that the bridge (`addWebMessageListener`) is permitted to
     * receive messages from. Per TZ §5.4.1 the bridge only listens to the
     * chat origin itself.
     *
     * AndroidX WebKit's `addDocumentStartJavaScript` and `addWebMessageListener`
     * both require `Set<String>` for the `allowedOriginRules` parameter.
     */
    val bridgeOriginRules: Set<String> = setOf(
        CHAT_ORIGIN,
        AUTH_ORIGIN,
    )
}
