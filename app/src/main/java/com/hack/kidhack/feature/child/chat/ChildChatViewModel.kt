package com.hack.kidhack.feature.child.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.SessionRepository
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
    val canSend: Boolean get() = room != null && draft.isNotBlank()
}

/**
 * 자녀 ↔ 부모 채팅.
 *
 * 채팅 탭 첫 로드 시 [ChatRepository.getOrCreateRoom]으로 roomId를 확보한다.
 */
class ChildChatViewModel(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildChatUiState())
    val state: StateFlow<ChildChatUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var cachedRoom: ChatRoom? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val user = sessionRepository.current()?.user
                    ?: error("로그인 정보가 없어요")
                val childId = user.id.toLongOrNull()
                    ?: error("회원 번호가 올바르지 않아요")
                val parentId = user.parentId
                    ?: error("연결된 보호자가 없어요")
                val room = cachedRoom
                    ?: chatRepository.getOrCreateRoom(parentId, childId).also { cachedRoom = it }
                val messages = chatRepository.messages(room.id)
                room to messages
            }.onSuccess { (room, messages) ->
                _state.update {
                    it.copy(loading = false, room = room, messages = messages, error = null)
                }
                observe(room.id)
                runCatching { chatRepository.markRead(room.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.userMessage("채팅방을 준비하지 못했어요"),
                    )
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
                .onFailure {
                    _state.update { s ->
                        s.copy(draft = text, error = it.userMessage("메시지를 보내지 못했어요"))
                    }
                }
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
