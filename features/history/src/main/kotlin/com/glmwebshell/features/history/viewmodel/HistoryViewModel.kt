package com.glmwebshell.features.history.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glmwebshell.core.data.db.entity.ChatEntity
import com.glmwebshell.core.data.db.entity.MessageEntity
import com.glmwebshell.core.data.repository.ChatRepository
import com.glmwebshell.core.data.repository.MessageRepository
import com.glmwebshell.features.history.export.ChatExport
import com.glmwebshell.features.history.export.ChatExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val messages: MessageRepository,
) : ViewModel() {

    val chatList: StateFlow<List<ChatEntity>> =
        chats.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatEntity>>(emptyList())
    val searchResults: StateFlow<List<ChatEntity>> = _searchResults.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
        viewModelScope.launch {
            _searchResults.value = if (q.isBlank()) emptyList() else chats.search(q)
        }
    }

    fun togglePinned(chat: ChatEntity) {
        viewModelScope.launch { chats.setPinned(chat.id, !chat.pinned) }
    }

    fun delete(chat: ChatEntity) {
        viewModelScope.launch { chats.delete(chat.id) }
    }

    suspend fun export(chat: ChatEntity, format: ExportFormat): ChatExport {
        val msgs = messages.getByChat(chat.id)
        return when (format) {
            ExportFormat.MARKDOWN -> ChatExporter.toMarkdown(chat, msgs)
            ExportFormat.PLAIN_TEXT -> ChatExporter.toPlainText(chat, msgs)
            ExportFormat.JSON -> ChatExporter.toJson(chat, msgs)
        }
    }
}

enum class ExportFormat { MARKDOWN, PLAIN_TEXT, JSON }
