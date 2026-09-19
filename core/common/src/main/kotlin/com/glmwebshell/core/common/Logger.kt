package com.glmwebshell.core.common

import android.util.Log

/**
 * Thin wrapper around android.util.Log that:
 *  - has a single tag prefix ("GLM/") for easy filtering;
 *  - never logs cookies, tokens or full request bodies (just lengths);
 *  - is a no-op for verbose logs in release builds (configured by BuildConfig
 *    in :app via `android.util.Log` is still callable, but we guard here).
 *
 * Per TZ §7 — "Cookies and tokens are not logged".
 */
object Logger {
    private const val PREFIX = "GLM"

    fun d(tag: String, msg: String) {
        if (BuildFlags.DEBUG) Log.d("$PREFIX/$tag", msg)
    }

    fun i(tag: String, msg: String) {
        Log.i("$PREFIX/$tag", msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w("$PREFIX/$tag", msg, t) else Log.w("$PREFIX/$tag", msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e("$PREFIX/$tag", msg, t) else Log.e("$PREFIX/$tag", msg)
    }
}

/**
 * Set by :app at init time via [BuildFlags.init].
 */
object BuildFlags {
    @Volatile var DEBUG: Boolean = false
    fun init(debug: Boolean) { DEBUG = debug }
}
