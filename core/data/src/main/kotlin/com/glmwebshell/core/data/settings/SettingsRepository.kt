package com.glmwebshell.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.glmwebshell.core.common.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("glm-settings")

/** Per TZ §6.1.6 (proxy), §6.1.8 (theme), §7 (privacy/biometrics). */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dispatchers: AppDispatchers,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")           // system | light | dark
        val USE_PROXY = booleanPreferencesKey("use_proxy")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")           // http | socks
        val PROXY_USERNAME = stringPreferencesKey("proxy_user")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_pass")
        val PULL_TO_REFRESH = booleanPreferencesKey("pull_to_refresh")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val TELEMETRY = booleanPreferencesKey("telemetry")
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports")
        val USE_MOCK_PAGE = booleanPreferencesKey("use_mock_page")
        val ADAPTER_CHANNEL = stringPreferencesKey("adapter_channel") // stable | canary
        val WEBVIEW_DEBUG = booleanPreferencesKey("webview_debug")
    }

    val themeMode: Flow<ThemeMode> = ctx.dataStore.data.map {
        ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val proxyConfig: Flow<ProxyConfig> = ctx.dataStore.data.map {
        ProxyConfig(
            enabled = it[Keys.USE_PROXY] ?: false,
            type = ProxyType.valueOf(it[Keys.PROXY_TYPE] ?: ProxyType.HTTP.name),
            host = it[Keys.PROXY_HOST] ?: "",
            port = it[Keys.PROXY_PORT] ?: 0,
            username = it[Keys.PROXY_USERNAME] ?: "",
            password = it[Keys.PROXY_PASSWORD] ?: "",
        )
    }

    val usePullToRefresh: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.PULL_TO_REFRESH] ?: true }
    val biometricLock: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.BIOMETRIC_LOCK] ?: false }
    val telemetry: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.TELEMETRY] ?: false }
    val crashReports: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.CRASH_REPORTS] ?: false }
    val useMockPage: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.USE_MOCK_PAGE] ?: false }
    val adapterChannel: Flow<AdapterChannel> = ctx.dataStore.data.map {
        AdapterChannel.valueOf(it[Keys.ADAPTER_CHANNEL] ?: AdapterChannel.STABLE.name)
    }
    val webViewDebug: Flow<Boolean> = ctx.dataStore.data.map { it[Keys.WEBVIEW_DEBUG] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setProxyConfig(c: ProxyConfig) = edit {
        it[Keys.USE_PROXY] = c.enabled
        it[Keys.PROXY_TYPE] = c.type.name
        it[Keys.PROXY_HOST] = c.host
        it[Keys.PROXY_PORT] = c.port
        it[Keys.PROXY_USERNAME] = c.username
        it[Keys.PROXY_PASSWORD] = c.password
    }
    suspend fun setPullToRefresh(v: Boolean) = edit { it[Keys.PULL_TO_REFRESH] = v }
    suspend fun setBiometricLock(v: Boolean) = edit { it[Keys.BIOMETRIC_LOCK] = v }
    suspend fun setTelemetry(v: Boolean) = edit { it[Keys.TELEMETRY] = v }
    suspend fun setCrashReports(v: Boolean) = edit { it[Keys.CRASH_REPORTS] = v }
    suspend fun setUseMockPage(v: Boolean) = edit { it[Keys.USE_MOCK_PAGE] = v }
    suspend fun setAdapterChannel(c: AdapterChannel) = edit { it[Keys.ADAPTER_CHANNEL] = c.name }
    suspend fun setWebViewDebug(v: Boolean) = edit { it[Keys.WEBVIEW_DEBUG] = v }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) =
        withContext(dispatchers.io) { ctx.dataStore.edit { block(it) } }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ProxyType { HTTP, SOCKS }
enum class AdapterChannel { STABLE, CANARY }

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
