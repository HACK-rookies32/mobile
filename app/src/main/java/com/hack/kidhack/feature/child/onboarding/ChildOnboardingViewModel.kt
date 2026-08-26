package com.hack.kidhack.feature.child.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildOnboardingUiState(
    val step: Int = 0,
    val finished: Boolean = false,
    /** 설정 앱에서만 켤 수 있는 특별 권한들의 현재 상태. */
    val harmfulBlockEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
) {
    /** 위치 → 알림 → 카메라 → 나중에 설정 안내 */
    val totalSteps: Int = 4
}

class ChildOnboardingViewModel(
    private val store: ChildOnboardingStore,
    private val deviceStatus: ChildDeviceStatus,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildOnboardingUiState())
    val state: StateFlow<ChildOnboardingUiState> = _state.asStateFlow()

    init {
        refreshPermissions()
    }

    /**
     * 접근성·사용정보 접근·오버레이는 런타임 권한 다이얼로그가 없어서
     * 설정 앱에 다녀온 뒤 결과를 직접 다시 읽어야 한다.
     */
    fun refreshPermissions() {
        viewModelScope.launch {
            val device = deviceStatus.snapshot()
            _state.update {
                it.copy(
                    harmfulBlockEnabled = device.harmfulBlockEnabled,
                    usageAccessGranted = device.usageAccessGranted,
                    overlayGranted = device.overlayGranted,
                )
            }
        }
    }

    fun next() {
        val current = _state.value
        if (current.step >= current.totalSteps - 1) {
            finish()
        } else {
            _state.update { it.copy(step = it.step + 1) }
        }
    }

    fun finish() {
        viewModelScope.launch {
            store.markDone()
            _state.update { it.copy(finished = true) }
        }
    }
}
