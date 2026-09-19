package com.glmwebshell.features.diagnostics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glmwebshell.adapter.health.AdapterRuntime
import com.glmwebshell.adapter.health.CapabilityHealth
import com.glmwebshell.adapter.health.CapabilityHealthMonitor
import com.glmwebshell.adapter.remote.AdapterLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val monitor: CapabilityHealthMonitor,
    private val runtime: AdapterRuntime,
    private val loader: AdapterLoader,
) : ViewModel() {

    val capabilities: StateFlow<Map<String, CapabilityHealth>> =
        monitor.states.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val adapterVersion: Int get() = loader.current().manifest.adapterVersion
    val adapterChannel: String get() = loader.current().manifest.channel
    val targetHost: String get() = loader.current().manifest.targetHost
    val generatedAt: String get() = loader.current().manifest.generatedAt

    /** Produce a local report (TZ §5.3 — "Сообщить о проблеме"). No PII. */
    fun buildReport(): String = buildString {
        appendLine("# GLM Web Shell — capability report")
        appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(java.util.Date())}")
        appendLine("Adapter version: $adapterVersion (channel=$adapterChannel)")
        appendLine("Target host: $targetHost")
        appendLine("Generated at: $generatedAt")
        appendLine()
        appendLine("## Capabilities")
        val states = capabilities.value
        if (states.isEmpty()) {
            appendLine("(no capabilities have run yet)")
        } else for ((id, h) in states) {
            appendLine("- $id: ${h.status} (strategy=${h.activeStrategy ?: "—"}, failStreak=${h.failStreak}, autoDisabled=${h.autoDisabled})")
        }
    }
}
