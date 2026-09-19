package com.glmwebshell.features.settings.viewmodel

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glmwebshell.core.data.repository.ChatRepository
import com.glmwebshell.core.data.settings.AdapterChannel
import com.glmwebshell.core.data.settings.ProxyConfig
import com.glmwebshell.core.data.settings.SettingsRepository
import com.glmwebshell.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    /**
     * Settings is a flat UI; we combine the relevant flows into a single state
     * holder so the Compose layer can read one thing. kotlinx.coroutines's
     * typed `combine` tops out at 5 flows — beyond that we nest two combines.
     */
    private val firstHalf = combine(
        settings.themeMode,
        settings.proxyConfig,
        settings.usePullToRefresh,
        settings.biometricLock,
        settings.telemetry,
    ) { themeMode, proxy, pullToRefresh, biometricLock, telemetry ->
        arrayOf(themeMode, proxy, pullToRefresh, biometricLock, telemetry)
    }

    private val secondHalf = combine(
        settings.crashReports,
        settings.useMockPage,
        settings.adapterChannel,
        settings.webViewDebug,
    ) { crashReports, useMockPage, adapterChannel, webViewDebug ->
        arrayOf(crashReports, useMockPage, adapterChannel, webViewDebug)
    }

    val state: StateFlow<SettingsUi> = combine(firstHalf, secondHalf) { a, b ->
        SettingsUi(
            themeMode = a[0] as ThemeMode,
            proxy = a[1] as ProxyConfig,
            pullToRefresh = a[2] as Boolean,
            biometricLock = a[3] as Boolean,
            telemetry = a[4] as Boolean,
            crashReports = b[0] as Boolean,
            useMockPage = b[1] as Boolean,
            adapterChannel = b[2] as AdapterChannel,
            webViewDebug = b[3] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUi())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setProxy(cfg: ProxyConfig) = viewModelScope.launch { settings.setProxyConfig(cfg) }
    fun setPullToRefresh(v: Boolean) = viewModelScope.launch { settings.setPullToRefresh(v) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { settings.setBiometricLock(v) }
    fun setTelemetry(v: Boolean) = viewModelScope.launch { settings.setTelemetry(v) }
    fun setCrashReports(v: Boolean) = viewModelScope.launch { settings.setCrashReports(v) }
    fun setUseMockPage(v: Boolean) = viewModelScope.launch { settings.setUseMockPage(v) }
    fun setAdapterChannel(c: AdapterChannel) = viewModelScope.launch { settings.setAdapterChannel(c) }
    fun setWebViewDebug(v: Boolean) = viewModelScope.launch { settings.setWebViewDebug(v) }

    /** Sign out + clear all site data (TZ §6.1.2 / §6.1.6). */
    fun signOutAndClearSiteData() = viewModelScope.launch {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    /** Per TZ §7 — local history is fully user-removable. */
    fun clearLocalHistory() = viewModelScope.launch { chats.deleteAll() }
}

data class SettingsUi(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val proxy: ProxyConfig = ProxyConfig.NONE,
    val pullToRefresh: Boolean = true,
    val biometricLock: Boolean = false,
    val telemetry: Boolean = false,
    val crashReports: Boolean = false,
    val useMockPage: Boolean = false,
    val adapterChannel: AdapterChannel = AdapterChannel.STABLE,
    val webViewDebug: Boolean = false,
)
