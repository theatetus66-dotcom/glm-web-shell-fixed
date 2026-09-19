package com.glmwebshell.features.history.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glmwebshell.core.ui.components.GlmEmptyState
import com.glmwebshell.features.history.viewmodel.ExportFormat
import com.glmwebshell.features.history.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val chats by viewModel.chatList.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text("Search title, URL, remote id…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            val list = if (query.isBlank()) chats else results
            if (list.isEmpty()) {
                GlmEmptyState(
                    title = if (query.isBlank()) "No chats captured yet" else "No matches",
                    subtitle = if (query.isBlank())
                        "Open the chat and start talking — visible turns are saved locally."
                    else "Try a different query.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onPin = { viewModel.togglePinned(chat) },
                            onDelete = { viewModel.delete(chat) },
                            onExport = { format ->
                                scope.launch {
                                    val exp = viewModel.export(chat, format)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = exp.mime
                                        putExtra(Intent.EXTRA_SUBJECT, exp.filename)
                                        putExtra(Intent.EXTRA_TEXT, exp.content)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Export"))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRow(
    chat: com.glmwebshell.core.data.db.entity.ChatEntity,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onExport: (ExportFormat) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.title.ifBlank { chat.url }, style = MaterialTheme.typography.bodyLarge)
                Text(chat.url, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPin) {
                Icon(
                    if (chat.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Markdown") }, onClick = { menuOpen = false; onExport(ExportFormat.MARKDOWN) })
                    DropdownMenuItem(text = { Text("Plain text") }, onClick = { menuOpen = false; onExport(ExportFormat.PLAIN_TEXT) })
                    DropdownMenuItem(text = { Text("JSON") }, onClick = { menuOpen = false; onExport(ExportFormat.JSON) })
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}
