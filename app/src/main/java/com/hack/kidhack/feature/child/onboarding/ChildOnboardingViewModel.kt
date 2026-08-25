package com.hack.kidhack.feature.child.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildOnboardingUiState(
    val step: Int = 0,
    val finished: Boolean = false,
) {
    /** 위치 → 알림 → 카메라 → 나중에 설정 안내 */
    val totalSteps: Int = 4
}

class ChildOnboardingViewModel(
    private val store: ChildOnboardingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildOnboardingUiState())
    val state: StateFlow<ChildOnboardingUiState> = _state.asStateFlow()

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
