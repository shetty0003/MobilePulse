package com.mobilepulse.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilepulse.app.data.model.AiProvider
import com.mobilepulse.app.data.repository.AiMessage
import com.mobilepulse.app.data.repository.AiRepository
import com.mobilepulse.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiRepo: AiRepository,
    private val settingsRepo: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val initialQuery = savedStateHandle.get<String>("query")
        if (!initialQuery.isNullOrBlank()) {
            send(initialQuery)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        _messages.value = _messages.value + AiMessage("user", trimmed)
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val provider = settingsRepo.aiProviderFlow.first()
            val apiKey = when (provider) {
                AiProvider.CLAUDE   -> settingsRepo.claudeApiKeyFlow.first()
                AiProvider.DEEPSEEK -> settingsRepo.deepseekApiKeyFlow.first()
            }
            if (apiKey.isBlank()) {
                _messages.value = _messages.value.dropLast(1)
                val name = if (provider == AiProvider.CLAUDE) "Claude" else "DeepSeek"
                _error.value = "No $name API key set. Go to Settings → AI to add it."
                _isLoading.value = false
                return@launch
            }

            aiRepo.sendMessage(provider, apiKey, _messages.value).fold(
                onSuccess = { reply ->
                    _messages.value = _messages.value + AiMessage("assistant", reply)
                },
                onFailure = { e ->
                    _error.value = e.message ?: "Unknown error"
                }
            )
            _isLoading.value = false
        }
    }

    fun dismissError() { _error.value = null }

    fun clearChat() { _messages.value = emptyList() }
}
