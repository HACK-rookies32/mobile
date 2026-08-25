package com.hack.kidhack.feature.child.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val online: Boolean = true,
)

class ChildChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildChatUiState())
    val state: StateFlow<ChildChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val messages = runCatching {
                chatRepository.messages(FakeData.CHILD_CHAT_ROOM_ID)
            }.getOrElse { FakeData.chatMessages }
            _state.update { it.copy(messages = messages) }
            runCatching {
                chatRepository.markRead(FakeData.CHILD_CHAT_ROOM_ID, messages.lastOrNull()?.id)
            }
        }
        viewModelScope.launch {
            chatRepository.observeMessages(FakeData.CHILD_CHAT_ROOM_ID).collect { append(it) }
        }
    }

    fun onDraft(value: String) = _state.update { it.copy(draft = value) }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            runCatching { chatRepository.send(FakeData.CHILD_CHAT_ROOM_ID, text) }
                .onSuccess { append(it) }
                .onFailure { _state.update { it.copy(draft = text) } }
        }
    }

    private fun append(msg: ChatMessage) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) return@update s
            val last = s.messages.lastOrNull()
            if (last != null && last.id.startsWith("local-") && last.text == msg.text && last.fromRole == msg.fromRole) {
                s.copy(messages = s.messages.dropLast(1) + msg)
            } else {
                s.copy(messages = s.messages + msg)
            }
        }
    }
}
