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
 * 계정 전환 후에도 이전 room을 쓰지 않도록 [boundChildUserId]로 세션을 검증한다.
 */
class ChildChatViewModel(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildChatUiState())
    val state: StateFlow<ChildChatUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var cachedRoom: ChatRoom? = null
    /** load() 성공 시 바인딩한 자녀 user id. 다른 계정으로 바뀌면 load()를 강제한다. */
    private var boundChildUserId: String? = null

    /** 채팅 탭 resume: 세션과 room 소유자가 다르면 무조건 재로드. */
    fun onScreenResume() {
        viewModelScope.launch {
            val userId = sessionRepository.current()?.user?.id
            if (userId.isNullOrBlank() ||
                boundChildUserId != userId ||
                _state.value.room == null
            ) {
                load()
            } else {
                reloadCurrentRoom()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            observeJob?.cancel()
            cachedRoom = null
            boundChildUserId = null
            _state.update { it.copy(loading = true, error = null, room = null, messages = emptyList()) }
            runCatching {
                val user = sessionRepository.current()?.user
                    ?: error("로그인 정보가 없어요")
                val childId = user.id.toLongOrNull()
                    ?: error("회원 번호가 올바르지 않아요")
                val parentId = user.parentId
                    ?: error("연결된 보호자가 없어요")
                val room = chatRepository.getOrCreateRoom(parentId, childId)
                val roomChildId = room.childId
                if (roomChildId != null && roomChildId != childId) {
                    error("채팅방 정보가 계정과 맞지 않아요")
                }
                cachedRoom = room
                boundChildUserId = user.id
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
            val sessionChildId = sessionRepository.current()?.user?.id
            if (sessionChildId == null || boundChildUserId != sessionChildId) {
                _state.update { it.copy(draft = text) }
                load()
                return@launch
            }
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

    /** 채팅 탭 resume 시 히스토리 refresh + STOMP 구독 재시작. */
    fun reloadCurrentRoom() {
        viewModelScope.launch {
            val sessionChildId = sessionRepository.current()?.user?.id
            if (sessionChildId == null || boundChildUserId != sessionChildId) {
                load()
                return@launch
            }
            val room = _state.value.room ?: cachedRoom
            if (room == null) {
                load()
                return@launch
            }
            val roomChildId = room.childId
            if (roomChildId != null && roomChildId.toString() != sessionChildId) {
                load()
                return@launch
            }
            observeJob?.cancel()
            _state.update { it.copy(loading = true, error = null, room = room) }
            runCatching {
                chatRepository.messages(room.id)
            }.onSuccess { messages ->
                _state.update {
                    it.copy(loading = false, room = room, messages = messages, error = null)
                }
                observe(room.id)
                runCatching { chatRepository.markRead(room.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
                observe(room.id)
            }
        }
    }

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
            if (s.room != null && msg.roomId != s.room.id) return@update s
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
