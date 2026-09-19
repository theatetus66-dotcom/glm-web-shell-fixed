package com.glmwebshell.pageengine.proxy

import kotlinx.serialization.Serializable

/**
 * Proxy config that the engine applies via androidx.webkit ProxyController.
 * Per TZ §6.1.6 the engine accepts HTTP and SOCKS proxies with optional
 * basic auth. The auth bits are *not* logged anywhere (TZ §7).
 */
@Serializable
data class ProxyConfig(
    val enabled: Boolean,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
) {
    companion object {
        val NONE = ProxyConfig(false, ProxyType.HTTP, "", 0, "", "")
    }
}

enum class ProxyType { HTTP, SOCKS }
