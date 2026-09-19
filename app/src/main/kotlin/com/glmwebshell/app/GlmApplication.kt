package com.glmwebshell.app

import android.app.Application
import com.glmwebshell.core.common.BuildFlags
import com.glmwebshell.core.common.Logger
import com.glmwebshell.BuildConfig
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Per TZ §7:
 *  - `BuildConfig.DEBUG` is the single source of truth for verbose logs and
 *    `setWebContentsDebuggingEnabled`.
 *  - Hilt application — capabilities, repositories and the page engine are
 *    singletons owned here.
 *  - WorkManager is configured lazily when the first scheduled adapter-fetch
 *    job enqueues (the project ships with no WorkManager jobs in MVP, but
 *    the Configuration.Provider hook is in place for the future remote-adapter
 *    scheduler).
 */
@HiltAndroidApp
class GlmApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        BuildFlags.init(BuildConfig.DEBUG)
        Logger.i("App", "GLM Web Shell started (debug=${BuildConfig.DEBUG})")
    }
}
