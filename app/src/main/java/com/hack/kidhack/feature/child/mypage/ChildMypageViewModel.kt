package com.hack.kidhack.feature.child.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import com.hack.kidhack.feature.child.formatDurationSec
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
    val name: String = "",
    val email: String = "",
    val grade: String = DEFAULT_GRADE,
    val screenTime: String = formatDurationSec(0),
    val chatCount: Int = 0,
    val blockedCount: Int = 0,
    val payBalance: Int = 0,
    val guardians: List<GuardianRow> = emptyList(),
) {
    companion object {
        /** 학년 필드는 자녀가 부를 수 있는 API 에 없다(`children` 은 부모 전용). 서버가 추가하면 대체. */
        const val DEFAULT_GRADE = "초등학교 6학년"
    }
}

class ChildMypageViewModel(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val usageCollector: AppUsageCollector,
    private val notificationRepository: NotificationRepository,
    private val allowanceRepository: AllowanceRepository,
    private val alertStore: ChildAlertStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildMypageUiState())
    val state: StateFlow<ChildMypageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = sessionRepository.current()?.user
            val childId = sessionRepository.childId()

            // 보호자 목록은 자녀가 부를 수 있는 채팅방 목록에서 끌어온다.
            val rooms = runCatching { chatRepository.rooms() }.getOrDefault(emptyList())
            val chatCount = rooms.firstOrNull()
                ?.let { room -> runCatching { chatRepository.messages(room.id).size }.getOrDefault(0) }
                ?: 0
            // 서비스 현황 탭과 같은 값을 보여야 하므로 동일한 수집기를 쓴다.
            val usage = usageCollector.todayUsage()
            val notifications = runCatching { notificationRepository.list(childId) }.getOrDefault(emptyList())
            val balance = runCatching { allowanceRepository.balance(childId).balance }.getOrDefault(0)
            val blockedToday = alertStore.countToday(ChildAlertStore.TYPE_HARMFUL_BLOCK)

            _state.update {
                it.copy(
                    name = user?.name.orEmpty(),
                    email = user?.email.orEmpty(),
                    screenTime = formatDurationSec(usage.totalSeconds),
                    chatCount = chatCount,
                    blockedCount = blockedToday + notifications.count { n ->
                        val type = n.type.uppercase()
                        type.contains("HARMFUL") || type.contains("BLOCK")
                    },
                    payBalance = balance,
                    guardians = rooms.map { room ->
                        GuardianRow(
                            name = room.title,
                            status = if (room.online) "온라인" else "자리 비움",
                            emoji = "👩",
                            online = room.online,
                        )
                    },
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
