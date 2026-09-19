package com.glmwebshell.features.prompts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glmwebshell.core.data.db.entity.PromptEntity
import com.glmwebshell.core.ui.components.GlmEmptyState
import com.glmwebshell.features.prompts.viewmodel.PromptsViewModel

/**
 * Per TZ §6.3.4: prompts library.
 * Insertion into the chat field is **transparent** — the user sees a preview
 * dialog with the exact text before it is sent. The "send" action in this UI
 * only stages the text; the actual fill happens in the chat screen via the
 * `chat.input.fill` capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsRoute(
    onBack: () -> Unit,
    onInsert: (String) -> Unit,
    viewModel: PromptsViewModel = hiltViewModel(),
) {
    val prompts by viewModel.prompts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pendingInsert by remember { mutableStateOf<PromptEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompts & persons") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New") },
            )
        }
    ) { padding ->
        if (prompts.isEmpty()) {
            GlmEmptyState(
                title = "No prompts yet",
                subtitle = "Tap “New” to add your first prompt or persona.",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(prompts, key = { it.id }) { p ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { pendingInsert = p }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            if (p.isPerson) Text("persona", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(p.text, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                p.tags.forEach { tag ->
                                    Text("· $tag", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { viewModel.delete(p.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                                IconButton(onClick = { pendingInsert = p }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Insert")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddPromptDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, text, tags, isPerson ->
                viewModel.add(title, text, tags, isPerson)
                showAdd = false
            }
        )
    }

    pendingInsert?.let { p ->
        // Transparency preview per TZ §5.5
        AlertDialog(
            onDismissRequest = { pendingInsert = null },
            title = { Text("Insert into chat") },
            text = {
                Column {
                    Text("The following text will be inserted into the input field. Review before sending:",
                        style = MaterialTheme.typography.bodyMedium)
                    Text(p.text, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onInsert(p.text)
                    pendingInsert = null
                }) { Text("Insert") }
            },
            dismissButton = {
                TextButton(onClick = { pendingInsert = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddPromptDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<String>, Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isPerson by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true)
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("Text") }, minLines = 3)
                OutlinedTextField(value = tags, onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = isPerson, onCheckedChange = { isPerson = it })
                    Text("Is a persona (system-style prompt)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && text.isNotBlank()) {
                    onConfirm(title.trim(), text.trim(),
                        tags.split(",").map { it.trim() }.filter { it.isNotBlank() }, isPerson)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
