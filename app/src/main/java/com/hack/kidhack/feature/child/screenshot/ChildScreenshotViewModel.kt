package com.hack.kidhack.feature.child.screenshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.ScreenshotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildScreenshotUiState(
    val items: List<Screenshot> = emptyList(),
    val bannerVisible: Boolean = true,
)

class ChildScreenshotViewModel(
    private val screenshotRepository: ScreenshotRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildScreenshotUiState())
    val state: StateFlow<ChildScreenshotUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val items = runCatching {
                screenshotRepository.list(FakeData.minjun.id)
            }.getOrDefault(emptyList())
            _state.update { it.copy(items = items) }
        }
    }

    fun dismissBanner() = _state.update { it.copy(bannerVisible = false) }
}
