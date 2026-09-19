package com.glmwebshell.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glmwebshell.core.data.settings.AdapterChannel
import com.glmwebshell.core.data.settings.ProxyConfig
import com.glmwebshell.core.data.settings.ProxyType
import com.glmwebshell.core.data.settings.ThemeMode
import com.glmwebshell.features.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(title = "Appearance") {
                EnumPicker(
                    label = "Theme",
                    value = s.themeMode,
                    options = ThemeMode.values().toList(),
                    onPick = viewModel::setTheme,
                )
            }

            Section(title = "Network") {
                var enabled by remember { mutableStateOf(s.proxy.enabled) }
                var host by remember { mutableStateOf(s.proxy.host) }
                var port by remember { mutableStateOf(s.proxy.port.toString()) }
                var type by remember { mutableStateOf(s.proxy.type) }
                // Keep local edit buffers in sync when DataStore emits (e.g. after load or external write).
                LaunchedEffect(s.proxy) {
                    enabled = s.proxy.enabled
                    host = s.proxy.host
                    port = s.proxy.port.toString()
                    type = s.proxy.type
                }

                SwitchRow("Use proxy", enabled) { enabled = it; push(viewModel, enabled, type, host, port, s.proxy.username, s.proxy.password) }
                EnumPicker("Type", type, ProxyType.values().toList()) { type = it; push(viewModel, enabled, type, host, port, s.proxy.username, s.proxy.password) }
                OutlinedTextField(value = host, onValueChange = { host = it; push(viewModel, enabled, type, host, port, s.proxy.username, s.proxy.password) },
                    label = { Text("Host") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = port, onValueChange = { port = it; push(viewModel, enabled, type, host, port, s.proxy.username, s.proxy.password) },
                    label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Note: SOCKS5 requires AndroidX WebKit WebView with ProxyController support.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section(title = "Chat surface") {
                SwitchRow("Pull-to-refresh", s.pullToRefresh, viewModel::setPullToRefresh)
                SwitchRow("Use bundled mock chat page (offline self-tests)", s.useMockPage, viewModel::setUseMockPage)
                SwitchRow("WebView debugging (debug builds only)", s.webViewDebug, viewModel::setWebViewDebug)
            }

            Section(title = "Privacy & security") {
                SwitchRow("App biometric / PIN lock", s.biometricLock, viewModel::setBiometric)
                SwitchRow("Telemetry", s.telemetry, viewModel::setTelemetry)
                SwitchRow("Crash reports", s.crashReports, viewModel::setCrashReports)
                HorizontalDivider()
                Button(onClick = viewModel::signOutAndClearSiteData) {
                    Text("Sign out & clear site data")
                }
                Button(onClick = viewModel::clearLocalHistory) {
                    Text("Delete local chat history")
                }
            }

            Section(title = "Adapter channel") {
                EnumPicker(
                    label = "Channel",
                    value = s.adapterChannel,
                    options = AdapterChannel.values().toList(),
                    onPick = viewModel::setAdapterChannel,
                )
                Text("Canary receives adapter updates earlier and may be less stable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun push(
    vm: SettingsViewModel,
    enabled: Boolean,
    type: ProxyType,
    host: String,
    port: String,
    username: String = "",
    password: String = "",
) {
    val p = ProxyConfig(
        enabled = enabled,
        type = type,
        host = host,
        port = port.toIntOrNull() ?: 0,
        username = username,
        password = password,
    )
    vm.setProxy(p)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun <T> EnumPicker(label: String, value: T, options: List<T>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { open = true }) { Text(value.toString()) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o.toString()) }, onClick = { open = false; onPick(o) }) }
        }
    }
}
