package com.glmwebshell.pageengine.proxy

import androidx.webkit.ProxyConfig as WebkitProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * Applies the user-configured proxy to the system WebView via
 * androidx.webkit's ProxyController (TZ §6.1.6). This is the only supported
 * way to drive a per-app proxy that applies to WebView traffic.
 */
object WebViewProxyApplier {

    /** Executes the runnable synchronously on the calling thread. */
    private val directExecutor = Executor { command -> command.run() }

    suspend fun apply(config: ProxyConfig): AppResult<Unit> {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return AppResult.err(ErrorCode.Proxy, "ProxyController not supported on this WebView")
        }
        if (!config.enabled) return clear()

        val builder = WebkitProxyConfig.Builder()
            .addProxyRule(buildAuthority(config))
            .addDirect()
        return runCatching {
            suspendCancellableCoroutine { cont ->
                ProxyController.getInstance().setProxyOverride(
                    builder.build(),
                    directExecutor,
                    Runnable { cont.resume(AppResult.ok(Unit)) },
                )
            }
        }.getOrElse {
            AppResult.err(ErrorCode.Proxy, it.message ?: "Proxy apply failed", it)
        }
    }

    suspend fun clear(): AppResult<Unit> {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return AppResult.ok(Unit)
        }
        return runCatching {
            suspendCancellableCoroutine { cont ->
                ProxyController.getInstance().clearProxyOverride(
                    directExecutor,
                    Runnable { cont.resume(AppResult.ok(Unit)) },
                )
            }
        }.getOrElse {
            AppResult.err(ErrorCode.Proxy, it.message ?: "Proxy clear failed", it)
        }
    }

    private fun buildAuthority(config: ProxyConfig): String {
        val scheme = if (config.type == ProxyType.SOCKS) "socks5" else "http"
        return "$scheme://${config.host}:${config.port}"
    }
}
