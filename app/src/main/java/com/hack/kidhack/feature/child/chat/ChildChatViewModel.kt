package com.hack.kidhack.feature.child.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildChatUiState(
    val loading: Boolean = true,
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val error: String? = null,
) {
    val online: Boolean get() = room?.online == true
    val title: String get() = room?.title.orEmpty()
    val canSend: Boolean get() = room != null && draft.isNotBlank()
}

/**
 * 자녀 ↔ 부모 채팅.
 *
 * 방 id 를 상수로 박지 않고 `GET /chat/rooms` 로 해결한다.
 * 자녀 계정에는 보호자 방이 하나뿐이라 첫 방을 쓰고, 그 방을 WebSocket 으로 구독한다.
 * (실제 송수신은 [ChatRepository] 구현이 담당 — 목 모드는 인메모리, 실서버는 `wss://…/ws/chat`)
 */
class ChildChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildChatUiState())
    val state: StateFlow<ChildChatUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val room = chatRepository.rooms().firstOrNull()
                    ?: error("연결된 보호자가 없어요")
                val messages = chatRepository.messages(room.id)
                room to messages
            }.onSuccess { (room, messages) ->
                _state.update { it.copy(loading = false, room = room, messages = messages) }
                observe(room.id)
                runCatching { chatRepository.markRead(room.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.message ?: "채팅을 불러오지 못했어요")
                }
            }
        }
    }

    fun onDraft(value: String) = _state.update { it.copy(draft = value) }

    fun send() {
        val text = _state.value.draft.trim()
        val room = _state.value.room ?: return
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            runCatching { chatRepository.send(room.id, text) }
                .onSuccess { append(it) }
                .onFailure { _state.update { s -> s.copy(draft = text, error = "메시지를 보내지 못했어요") } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun observe(roomId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            chatRepository.observeMessages(roomId).collect { message ->
                append(message)
                runCatching { chatRepository.markRead(roomId, message.id) }
            }
        }
    }

    /**
     * 낙관적으로 붙여 둔 로컬 메시지(`local-`/`fake-`)를 서버가 확정한 메시지로 갈아 끼운다.
     * 같은 id 가 이미 있으면 무시한다(소켓 에코 중복 방지).
     */
    private fun append(msg: ChatMessage) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) return@update s
            val last = s.messages.lastOrNull()
            val isEchoOfLocal = last != null &&
                (last.id.startsWith("local-") || last.id.startsWith("fake-")) &&
                last.text == msg.text &&
                last.fromRole == msg.fromRole
            if (isEchoOfLocal) {
                s.copy(messages = s.messages.dropLast(1) + msg)
            } else {
                s.copy(messages = s.messages + msg)
            }
        }
    }
}
