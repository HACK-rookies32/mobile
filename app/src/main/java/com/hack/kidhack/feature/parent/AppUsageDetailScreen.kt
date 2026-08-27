package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.UsageBar
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class UsageGroupBy(val apiValue: String, val label: String) {
    APP("APP", "앱별"),
    HOUR("HOUR", "시간대별"),
    DAY("DAY", "일별"),
}

data class AppUsageDetailUiState(
    val items: List<AppUsageStat> = emptyList(),
    val groupBy: UsageGroupBy = UsageGroupBy.APP,
    val loading: Boolean = true,
    val error: String? = null,
)

class AppUsageDetailViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appUsageRepository: AppUsageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUsageDetailUiState())
    val state: StateFlow<AppUsageDetailUiState> = _state.asStateFlow()

    init { load() }

    fun onGroupBy(g: UsageGroupBy) {
        if (g == _state.value.groupBy) return
        _state.update { it.copy(groupBy = g) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children())
                if (id == null) {
                    _state.update { it.copy(loading = false, items = emptyList()) }
                    return@launch
                }
                val items = appUsageRepository.list(id, _state.value.groupBy.apiValue)
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }
}

@Composable
fun ParentAppUsageDetailScreen(
    onBack: () -> Unit = {},
    viewModel: AppUsageDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val maxSec = state.items.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_usage_detail_title), onBack)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UsageGroupBy.entries.forEach { g ->
                    UsageGroupChip(g.label, state.groupBy == g) { viewModel.onGroupBy(g) }
                }
            }
        },
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)

        if (!state.loading && state.error == null && state.items.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.parent_empty), color = colors.muted, fontSize = 14.sp)
            }
        }

        if (state.items.isNotEmpty()) {
            // 앱별=많이 쓴 순, 시간대별=오래된 순(0시→23시), 일별=최신순으로 정렬해서 보여준다.
            val sortedItems = when (state.groupBy) {
                UsageGroupBy.APP -> state.items.sortedByDescending { it.durationSec }
                UsageGroupBy.HOUR -> state.items.sortedBy { it.key.toIntOrNull() ?: 0 }
                UsageGroupBy.DAY -> state.items.sortedByDescending { it.key }
            }
            KidCard {
                sortedItems.forEachIndexed { index, item ->
                    val barColor = when (state.groupBy) {
                        UsageGroupBy.APP -> appColor(item.key)
                        UsageGroupBy.HOUR -> hourColor(item.key)
                        // 최신순 표시라 index를 뒤집어 '최신일수록 진하게'를 유지한다.
                        UsageGroupBy.DAY -> dayColor(sortedItems.lastIndex - index, sortedItems.size)
                    }
                    UsageBar(
                        name = displayKey(item.key, state.groupBy),
                        timeLabel = formatDuration(item.durationSec),
                        progress = item.durationSec.toFloat() / maxSec,
                        barColor = barColor,
                    )
                    if (index < sortedItems.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

internal val UsageChartPalette = listOf(
    Color(0xFF2563EB), Color(0xFFF58220), Color(0xFF16A34A),
    Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFFACC15),
)

/** 앱별: 이름 해시로 고정 배정 — 같은 앱은 항상 같은 색. 대시보드와 상세 화면이 공유한다. */
internal fun appColor(key: String): Color =
    UsageChartPalette[(key.hashCode().let { if (it < 0) -it else it }) % UsageChartPalette.size]

/** 시간대별: 하루 흐름에 따른 고정 색. key는 "00"~"23" 형태(2자리 시). */
private fun hourColor(key: String): Color {
    val hour = key.toIntOrNull() ?: 0
    return when (hour) {
        in 0..5 -> Color(0xFF1E3A8A)   // 새벽
        in 6..11 -> Color(0xFFFDBA74)  // 아침
        in 12..17 -> Color(0xFFF58220) // 오후
        in 18..21 -> Color(0xFF7C3AED) // 저녁
        else -> Color(0xFF1E3A8A)      // 밤(22~23)
    }
}

/** 일별: 브랜드 오렌지, 최근일수록 진하게(오래된 항목일수록 옅게). items는 날짜 오름차순 가정. */
private fun dayColor(index: Int, total: Int): Color {
    val minAlpha = 0.35f
    val ratio = if (total <= 1) 1f else index / (total - 1).toFloat()
    return Color(0xFFF58220).copy(alpha = minAlpha + (1f - minAlpha) * ratio)
}

/** HOUR면 "09시"처럼 보기 좋게, 나머지(APP=앱이름/DAY=날짜)는 그대로 보여준다. */
private fun displayKey(key: String, groupBy: UsageGroupBy): String =
    if (groupBy == UsageGroupBy.HOUR) "${key}시" else key

/** AppControlScreen의 FilterChip과 동일 톤(그라디언트 헤더 위 흰색 칩). */
@Composable
private fun UsageGroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (selected) colors.brand else Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}
