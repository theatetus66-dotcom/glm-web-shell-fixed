package com.glmwebshell.features.prompts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glmwebshell.core.data.db.entity.PromptEntity
import com.glmwebshell.core.data.repository.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val repo: PromptRepository,
) : ViewModel() {

    val prompts: StateFlow<List<PromptEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Seed the library the first time so the user sees something useful.
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    fun add(title: String, text: String, tags: List<String>, isPerson: Boolean) {
        viewModelScope.launch {
            repo.upsert(PromptEntity(title = title, text = text, tags = tags, isPerson = isPerson))
        }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}
