package com.hack.kidhack.feature.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ParentChatUiState(
    val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class ParentChatViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ParentChatUiState())
    val state: StateFlow<ParentChatUiState> = _state.asStateFlow()
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { load() }
        }
        load()
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val childId = selectedChildRepository.resolveId(children)
                val child = children.find { it.id == childId }
                val rooms = chatRepository.rooms()
                val room = rooms.find { it.title == child?.name } ?: rooms.firstOrNull()
                val messages = room?.let { chatRepository.messages(it.id) }.orEmpty()
                _state.update { it.copy(loading = false, room = room, messages = messages) }
                observe(room?.id)
                room?.let { chatRepository.markRead(it.id, messages.lastOrNull()?.id) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun send() {
        val text = _state.value.draft.trim()
        val room = _state.value.room ?: return
        if (text.isBlank()) return
        _state.update { it.copy(draft = "") }
        viewModelScope.launch {
            runCatching { chatRepository.send(room.id, text) }
                .onSuccess { append(it) }
                .onFailure { t -> _state.update { it.copy(error = t.message, draft = text) } }
        }
    }

    private fun observe(roomId: String?) {
        observeJob?.cancel()
        if (roomId == null) return
        observeJob = viewModelScope.launch {
            chatRepository.observeMessages(roomId).collect { append(it) }
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

@Composable
fun ParentChatScreen(viewModel: ParentChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(colors.surface).imePadding()) {
        BrandGradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("👦", fontSize = 20.sp) }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(state.room?.title ?: "", color = Color.White, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp)
                    Text(
                        if (state.room?.online == true) stringResource(R.string.parent_chat_online) else stringResource(R.string.parent_chat_offline),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (state.loading && state.messages.isEmpty()) {
            ParentLoadingOrError(true, state.error, viewModel::load)
        }
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
                            .then(if (!mine) Modifier.background(Color.White) else Modifier)
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
                            m.sentAt,
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
