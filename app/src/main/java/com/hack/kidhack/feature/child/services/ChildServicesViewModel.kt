package com.hack.kidhack.feature.child.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.data.repository.fake.FakeData
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.feature.child.formatDurationSec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChildServicesUiState(
    val locationLabel: String = "서울시 강남구",
    val gpsEnabled: Boolean = FakeData.CHILD_GPS_ENABLED,
    val usages: List<AppUsage> = FakeData.usages,
    val totalLabel: String = formatDurationSec(FakeData.usages.sumOf { it.durationSec }),
    val blockedToday: Int = FakeData.CHILD_BLOCKED_TODAY,
)

class ChildServicesViewModel(
    private val locationRepository: LocationRepository,
    private val appUsageRepository: AppUsageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildServicesUiState())
    val state: StateFlow<ChildServicesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val location = runCatching {
                locationRepository.current(FakeData.minjun.id)
            }.getOrNull()
            val usages = runCatching {
                appUsageRepository.list(FakeData.minjun.id, "APP")
            }.getOrElse { FakeData.usages }
            val label = location?.label
                ?.substringBefore("(")
                ?.trim()
                ?.ifBlank { null }
                ?: location?.label
                ?: "서울시 강남구"
            _state.update {
                it.copy(
                    locationLabel = label,
                    gpsEnabled = FakeData.CHILD_GPS_ENABLED,
                    usages = usages,
                    totalLabel = formatDurationSec(usages.sumOf { usage -> usage.durationSec }),
                )
            }
        }
    }
}
