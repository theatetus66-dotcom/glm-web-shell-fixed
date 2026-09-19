package com.glmwebshell.adapter.remote

import android.content.Context
import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.core.common.AppDispatchers
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.data.settings.AdapterChannel
import com.glmwebshell.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the remote adapter from a static host (TZ §5.4). URL is hard-coded
 * to a placeholder; project owner replaces it before publishing.
 *
 * Caching: the latest accepted package is written to `files/adapter/current.json`.
 * TTL: `ADAPTER_TTL_HOURS` (24h) — fetches more frequent than that are no-ops.
 */
@Singleton
class RemoteAdapterFetcher @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
    private val dispatchers: AppDispatchers,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val currentFile: File get() = File(ctx.filesDir, "adapter/current.json")
    private val lastFetchFile: File get() = File(ctx.filesDir, "adapter/last_fetch.txt")

    /** True if we should hit the network now (TTL not expired). */
    suspend fun shouldFetch(now: Long = System.currentTimeMillis()): Boolean = withContext(dispatchers.io) {
        val last = runCatching { lastFetchFile.readText().toLong() }.getOrNull() ?: 0L
        val ttlMs = com.glmwebshell.core.common.Constants.ADAPTER_TTL_HOURS * 3_600_000L
        now - last >= ttlMs
    }

    suspend fun fetch(channel: AdapterChannel): AppResult<String> = withContext(dispatchers.io) {
        val url = REMOTE_URL_TEMPLATE.format(channel.name.lowercase())
        val req = Request.Builder().url(url).get().build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext AppResult.err(
                    ErrorCode.NetworkUnavailable, "remote adapter HTTP ${resp.code}",
                )
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext AppResult.err(
                    ErrorCode.NetworkUnavailable, "remote adapter empty body",
                )
                lastFetchFile.parentFile?.mkdirs()
                lastFetchFile.writeText(System.currentTimeMillis().toString())
                AppResult.ok(body)
            }
        } catch (t: Throwable) {
            AppResult.err(ErrorCode.NetworkUnavailable, t.message ?: "fetch failed", t)
        }
    }

    fun persistCurrent(raw: String) {
        currentFile.parentFile?.mkdirs()
        currentFile.writeText(raw)
    }

    fun loadCachedRaw(): String? = runCatching {
        if (currentFile.exists()) currentFile.readText() else null
    }.getOrNull()

    companion object {
        // Placeholder. Replace with the actual static host before publishing.
        private const val REMOTE_URL_TEMPLATE = "https://example.com/glm-web-shell/adapters/%s.json"
        private const val TAG = "RemoteAdapterFetcher"
    }
}
