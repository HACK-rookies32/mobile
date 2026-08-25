package com.hack.kidhack.feature.child.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.feature.child.formatDurationMin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuardianRow(
    val name: String,
    val status: String,
    val emoji: String,
    val online: Boolean,
)

data class ChildMypageUiState(
    val name: String = FakeData.minjun.name,
    val email: String = FakeData.childUser.email.orEmpty(),
    val grade: String = FakeData.minjun.ageLabel
        ?.substringAfter("·")
        ?.trim()
        ?: "초등학교 6학년",
    val screenTime: String = formatDurationMin(FakeData.CHILD_SCREEN_USED_MINUTES),
    val chatCount: Int = FakeData.chatMessages.size,
    val blockedCount: Int = FakeData.CHILD_BLOCKED_TODAY,
    val payBalance: Int = 0,
    val guardians: List<GuardianRow> = listOf(
        GuardianRow("엄마 (${FakeData.parent.name})", "온라인", "👩", true),
        GuardianRow("아빠 (김철수)", "자리 비움", "👨", false),
    ),
)

class ChildMypageViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val pointRepository: PointRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildMypageUiState())
    val state: StateFlow<ChildMypageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = sessionRepository.current()?.user
            val chatCount = runCatching {
                chatRepository.messages(FakeData.CHILD_CHAT_ROOM_ID).size
            }.getOrElse { FakeData.chatMessages.size }
            val balance = runCatching { pointRepository.balance().balance }.getOrDefault(0)
            _state.update {
                it.copy(
                    name = user?.name ?: FakeData.minjun.name,
                    email = user?.email ?: FakeData.childUser.email.orEmpty(),
                    chatCount = chatCount,
                    payBalance = balance,
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { authRepository.logout() }
        }
    }
}
