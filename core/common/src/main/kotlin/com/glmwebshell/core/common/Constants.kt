package com.glmwebshell.core.common

/** Project-wide constants. */
object Constants {
    /** Bridge protocol version, per TZ §5.4.1 (strictly-typed bridge messages). */
    const val BRIDGE_PROTOCOL_VERSION = 1

    /** Max size of a single bridge message (1 MiB, per TZ §7 — input validation). */
    const val BRIDGE_MAX_MESSAGE_BYTES = 1 * 1024 * 1024

    /** Self-test timeout per capability, in milliseconds. */
    const val SELFTEST_TIMEOUT_MS = 4_000L

    /** Fail-count threshold after which a capability auto-disables. */
    const val CAPABILITY_FAIL_THRESHOLD = 3

    /** Default remote-adapter check interval, in hours (TZ §5.4). */
    const val ADAPTER_CHECK_INTERVAL_HOURS = 6

    /** TTL for cached adapter package, in hours. */
    const val ADAPTER_TTL_HOURS = 24

    /** WebView deep-link scheme for OAuth return (TZ §6.2). */
    const val OAUTH_DEEP_LINK_SCHEME = "glmwebshell"
    const val OAUTH_DEEP_LINK_HOST = "oauth-callback"
}
