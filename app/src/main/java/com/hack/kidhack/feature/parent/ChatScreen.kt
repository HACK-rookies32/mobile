package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.data.remote.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ParentChatUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val selectedRoom: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class ParentChatViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentChatUiState())
    val state: StateFlow<ParentChatUiState> = _state.asStateFlow()
    private var observeJob: Job? = null
    /** childId → room (세션 동안 재사용) */
    private val roomByChildId = mutableMapOf<String, ChatRoom>()

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    /** 방 목록 화면에서 탭 이탈 시 rooms만 비운다. 방 상세 중에는 WS 구독을 유지한다. */
    fun clearRoomsForPause() {
        if (_state.value.selectedRoom != null) return
        observeJob?.cancel()
        roomByChildId.clear()
        _state.update {
            it.copy(
                rooms = emptyList(),
                loading = false,
                error = null,
            )
        }
    }

    /** 방 상세 재진입·resume 시 히스토리 refresh + STOMP 구독 재시작. */
    fun reloadCurrentRoom() {
        val room = _state.value.selectedRoom ?: return
        viewModelScope.launch {
            observeJob?.cancel()
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val messages = chatRepository.messages(room.id)
                messages
            }.onSuccess { messages ->
                _state.update { it.copy(loading = false, messages = messages) }
                observe(room.id)
                chatRepository.markRead(room.id, messages.lastOrNull()?.id)
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
                observe(room.id)
            }
        }
    }

    fun loadRooms() {
        // launch 전에 동기로 비워, 재조회 중 이전 LazyColumn이 그려지지 않게 한다.
        roomByChildId.clear()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                rooms = emptyList(),
                selectedRoom = null,
                messages = emptyList(),
            )
        }
        viewModelScope.launch {
            runCatching {
                val parentId = sessionRepository.current()?.user?.id?.toLongOrNull()
                    ?: error("로그인 정보가 없어요")
                val children = childRepository.children()
                if (children.isEmpty()) {
                    emptyList()
                } else {
                    children.map { child ->
                        val childId = child.id.toLongOrNull()
                            ?: error("자녀 회원 번호가 올바르지 않아요")
                        val cached = roomByChildId[child.id]
                        val room = cached ?: chatRepository.getOrCreateRoom(parentId, childId)
                            .also { roomByChildId[child.id] = it }
                        // 서버가 자녀 접속 여부를 주지 않는다(ChildSummaryResponse 에 없음).
                        // 방 자체의 상태를 그대로 쓰고 자녀 정보로는 이름만 덮는다.
                        room.copy(title = child.name)
                    }
                }
            }.onSuccess { rooms ->
                _state.update { it.copy(loading = false, rooms = rooms, selectedRoom = null, error = null) }
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        loading = false,
                        rooms = emptyList(),
                        error = t.userMessage("채팅방을 준비하지 못했어요"),
                    )
                }
            }
        }
    }

    fun selectRoom(room: ChatRoom?) {
        observeJob?.cancel()
        if (room == null) {
            _state.update { it.copy(selectedRoom = null, messages = emptyList()) }
            loadRooms()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, selectedRoom = room) }
            runCatching {
                val children = childRepository.children()
                children.find { it.name == room.title || roomByChildId[it.id]?.id == room.id }
                    ?.let { selectedChildRepository.select(it.id) }

                val messages = chatRepository.messages(room.id)
                _state.update { it.copy(loading = false, messages = messages) }
                observe(room.id)
                chatRepository.markRead(room.id, messages.lastOrNull()?.id)
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.userMessage("메시지를 불러오지 못했어요"))
                }
            }
        }
    }

    fun send() {
        val text = _state.value.draft.trim()
        val room = _state.value.selectedRoom ?: return
        if (text.isBlank()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            runCatching { chatRepository.send(room.id, text) }
                .onSuccess { append(it) }
                .onFailure { t ->
                    _state.update {
                        it.copy(error = t.userMessage("메시지를 보내지 못했어요"), draft = text)
                    }
                }
        }
    }

    private fun observe(roomId: String?) {
        if (roomId == null) return
        observeJob = viewModelScope.launch {
            chatRepository.observeMessages(roomId).collect { append(it) }
        }
    }

    private fun append(msg: ChatMessage) {
        _state.update { s ->
            val selectedId = s.selectedRoom?.id
            if (selectedId != null && msg.roomId != selectedId) return@update s
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

@Composable
fun ParentChatScreen(viewModel: ParentChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val selectedRoom = state.selectedRoom

    // 탭 복원 시: 방 상세면 WS 재구독, 목록이면 rooms 재조회.
    LifecycleResumeEffect(Unit) {
        if (viewModel.state.value.selectedRoom != null) {
            viewModel.reloadCurrentRoom()
        } else {
            viewModel.loadRooms()
        }
        onPauseOrDispose { viewModel.clearRoomsForPause() }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    ParentScreenScaffold(
        header = {
            if (selectedRoom == null) {
                ParentHeaderBar(title = stringResource(R.string.parent_chat_title))
            } else {
                ParentHeaderBar(
                    title = selectedRoom.title,
                    onBack = { viewModel.selectRoom(null) }
                )
            }
        },
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.imePadding()
    ) {
        if (selectedRoom == null) {
            // 채팅방 목록 화면
            if (state.loading) {
                ParentLoadingOrError(true, state.error, viewModel::loadRooms)
            } else if (state.rooms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.parent_chat_empty_rooms), color = colors.muted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.rooms) { room ->
                        KidCard(Modifier.clickable { viewModel.selectRoom(room) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(colors.surface),
                                    contentAlignment = Alignment.Center
                                ) { Text("👦", fontSize = 24.sp) }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(room.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                                    Text(room.lastMessage, color = colors.muted, fontSize = 13.sp, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(room.lastAt, color = colors.muted, fontSize = 11.sp)
                                    if (room.online) {
                                        Text(stringResource(R.string.parent_chat_online), color = colors.safe, fontSize = 10.sp, fontWeight = FontBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 채팅 상세 화면
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { m ->
                        val mine = m.fromRole == Role.PARENT
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (mine) colors.brand else Color.White)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    m.text,
                                    color = if (mine) Color.White else colors.text,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontSemibold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    formatChatSentAt(m.sentAt),
                                    color = if (mine) Color.White.copy(alpha = 0.7f) else colors.muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraft,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 14.sp,
                            color = colors.text,
                        ),
                        decorationBox = { inner ->
                            Box {
                                if (state.draft.isEmpty()) {
                                    Text(stringResource(R.string.parent_chat_input_hint), color = colors.muted, fontSize = 14.sp)
                                }
                                inner()
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.brand)
                            .clickable(onClick = viewModel::send),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.parent_chat_send), color = Color.White, fontWeight = FontBold)
                    }
                }
            }
        }
    }
}
