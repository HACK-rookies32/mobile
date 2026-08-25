package com.hack.kidhack.feature.child.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.feature.child.formatDurationMin
import com.hack.kidhack.feature.child.remainingLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeServiceRow(
    val label: String,
    val desc: String,
    val emoji: String,
    val active: Boolean,
)

data class HomeAlertRow(
    val message: String,
    val time: String,
    val emoji: String,
    val tint: AlertTint,
    val opensScreenshot: Boolean = false,
)

enum class AlertTint { Brand, Danger, Info }

data class ChildHomeUiState(
    val name: String = FakeData.minjun.name,
    val alias: String = FakeData.minjun.alias ?: "민준",
    val usedLabel: String = formatDurationMin(FakeData.CHILD_SCREEN_USED_MINUTES),
    val limitLabel: String = formatDurationMin(FakeData.CHILD_SCREEN_LIMIT_MINUTES),
    val remaining: String = remainingLabel(
        FakeData.CHILD_SCREEN_USED_MINUTES,
        FakeData.CHILD_SCREEN_LIMIT_MINUTES,
    ),
    val progress: Float = FakeData.CHILD_SCREEN_USED_MINUTES /
        FakeData.CHILD_SCREEN_LIMIT_MINUTES.toFloat(),
    val protected: Boolean = true,
    val services: List<HomeServiceRow> = listOf(
        HomeServiceRow("위치 추적", "부모님이 위치를 확인 중", "📍", true),
        HomeServiceRow("유해 콘텐츠 차단", "안전한 인터넷 사용 중", "🚫", true),
        HomeServiceRow("앱 사용 모니터링", "오늘 5개 앱 사용 중", "📊", true),
        HomeServiceRow("기기 제한", "취침 모드: 밤 10시", "⏰", false),
    ),
    val alerts: List<HomeAlertRow> = listOf(
        HomeAlertRow("부모님이 화면 캡처를 요청했어요", "10분 전", "📸", AlertTint.Brand, true),
        HomeAlertRow("유해 사이트 접속이 차단됐어요", "1시간 전", "🚫", AlertTint.Danger),
        HomeAlertRow("부모님 메시지가 도착했어요", "2시간 전", "💬", AlertTint.Info),
    ),
)

class ChildHomeViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildHomeUiState())
    val state: StateFlow<ChildHomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = sessionRepository.current()?.user
            val alias = FakeData.minjun.alias
                ?: user?.name?.removePrefix("김")?.ifBlank { user.name }
                ?: "민준"
            _state.update {
                it.copy(
                    name = user?.name ?: FakeData.minjun.name,
                    alias = alias,
                )
            }
        }
    }
}
