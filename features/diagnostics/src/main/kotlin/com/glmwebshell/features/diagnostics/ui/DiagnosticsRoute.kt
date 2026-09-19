package com.glmwebshell.features.diagnostics.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.core.ui.components.HealthPill
import com.glmwebshell.core.ui.components.HealthPillColor
import com.glmwebshell.features.diagnostics.viewmodel.DiagnosticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.capabilities.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = viewModel.buildReport()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "GLM Web Shell — capability report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Share report"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Report problem")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Adapter: v${viewModel.adapterVersion} (${viewModel.adapterChannel})",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Host: ${viewModel.targetHost}", style = MaterialTheme.typography.bodyMedium)
                    Text("Generated: ${viewModel.generatedAt}", style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(state.entries.toList(), key = { it.key }) { (id, h) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(id, style = MaterialTheme.typography.bodyLarge)
                            Text("strategy=${h.activeStrategy ?: "—"} · fails=${h.failStreak}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val color = when (h.status) {
                            HealthStatus.OK -> HealthPillColor.OK
                            HealthStatus.DEGRADED -> HealthPillColor.DEGRADED
                            HealthStatus.FAILED -> HealthPillColor.FAILED
                        }
                        HealthPill(label = h.status.name, state = color)
                    }
                }
            }
        }
    }
}
