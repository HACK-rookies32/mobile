package com.hack.kidhack.feature.child.screenshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildScreenshotUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<Screenshot> = emptyList(),
    val bannerVisible: Boolean = false,
    /** 아직 처리하지 않은 캡처 요청. null 이면 대기 중인 요청이 없다. */
    val pendingRequestId: String? = null,
    val requestedBy: String? = null,
)

class ChildScreenshotViewModel(
    private val screenshotRepository: ScreenshotRepository,
    private val commandRepository: CommandRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildScreenshotUiState())
    val state: StateFlow<ChildScreenshotUiState> = _state.asStateFlow()

    init {
        refresh()
        // 화면이 열려 있는 동안 들어온 캡처 요청은 알림뿐 아니라 배너로도 보여 준다.
        viewModelScope.launch {
            commandRepository.observe()
                .filter { it.type == RemoteCommandType.SCREEN_CAPTURE }
                .collect { command ->
                    _state.update {
                        it.copy(
                            bannerVisible = true,
                            pendingRequestId = command.requestId,
                            requestedBy = command.requestedBy,
                        )
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val childId = sessionRepository.current()?.user?.id.orEmpty()
            runCatching { screenshotRepository.list(childId) }
                .onSuccess { items ->
                    _state.update { it.copy(loading = false, items = items, error = null) }
                }
                .onFailure {
                    // 서버가 죽어 있어도 화면이 그냥 비어 보이면 안 된다. 원인을 알리고 재시도를 준다.
                    _state.update { it.copy(loading = false, error = "캡처 내역을 불러오지 못했어요") }
                }
        }
    }

    /** 동의 화면으로 넘어간 뒤에는 배너를 접는다. 결과는 목록 갱신으로 확인한다. */
    fun onCaptureLaunched() = _state.update {
        it.copy(bannerVisible = false, pendingRequestId = null)
    }

    fun dismissBanner() = _state.update { it.copy(bannerVisible = false) }
}
