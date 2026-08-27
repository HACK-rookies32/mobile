package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.StatCard
import com.hack.kidhack.core.designsystem.component.UsageBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.DashboardRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class DashboardUiState(
    val children: List<ChildProfile> = emptyList(),
    val selectedId: String? = null,
    val selected: ChildProfile? = null,
    val lastLocation: String? = null,
    val lastLocationAt: String? = null,
    val usages: List<AppUsageStat> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DashboardViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appUsageRepository: AppUsageRepository,
    private val dashboardRepository: DashboardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { id ->
                if (_state.value.children.isNotEmpty()) loadFor(id)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                // TODO: Backend Integration - Real API 연결 시 childRepository의 실구현체(RealChildRepository)를 사용하도록 di/AppModule.kt 수정
                val children = childRepository.children()
                val selectedId = selectedChildRepository.resolveId(children)
                Triple(children, selectedId, selectedId)
            }.onSuccess { (children, selectedId, _) ->
                _state.update { it.copy(children = children, selectedId = selectedId) }
                loadFor(selectedId)
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message ?: "불러오지 못했어요") }
            }
        }
    }

    fun selectChild(id: String) {
        viewModelScope.launch { selectedChildRepository.select(id) }
    }

    private suspend fun loadFor(childId: String?) {
        if (childId == null) {
            _state.update { it.copy(loading = false, selected = null) }
            return
        }
        runCatching {
            val child = _state.value.children.find { it.id == childId }
                ?: childRepository.child(childId)
            // TODO: Backend Integration - API 엔드포인트에 따라 파라미터(날짜, 타입 등) 조정 필요
            // 앱 사용량은 대시보드 통합 응답에 없어 개별 유지.
            val usages = appUsageRepository.list(childId, "APP")
            // 위치·실시간·최근알림3·차단·공간을 통합 API 한 번으로 가져온다.
            val dashboard = dashboardRepository.get(childId)
            _state.update {
                it.copy(
                    loading = false,
                    error = null,
                    selectedId = childId,
                    selected = child,
                    usages = usages,
                    // recentNotifications는 이미 최근 3건. 화면의 .take(3)은 무해하게 유지.
                    notifications = dashboard.recentNotifications,
                    // TODO: Backend Integration - 실 API가 위경도만 줄 경우 역지오코딩 로직 추가 필요
                    lastLocation = dashboard.currentLocation?.label,
                    lastLocationAt = dashboard.currentLocation?.updatedAt?.takeLast(5),
                )
            }
        }.onFailure { t ->
            _state.update { it.copy(loading = false, error = t.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    onOpenNotifications: () -> Unit = {},
    onOpenUsageDetail: () -> Unit = {},
    onOpenPairing: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val child = state.selected
    val totalSec = state.usages.sumOf { it.durationSec }
    val alertCount = state.notifications.count { !it.read }
    val maxUsage = state.usages.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1

    ParentScreenScaffold(
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_hello),
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    Text(
                        stringResource(R.string.parent_dashboard_title),
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 20.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onOpenNotifications),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔔", fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.children.forEach { c ->
                    val selected = c.id == state.selectedId
                    Text(
                        text = c.name,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
                            .clickable { viewModel.selectChild(c.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (selected) colors.brand else Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(R.string.parent_add_child),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onOpenPairing)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        },
        scrollable = false,
    ) {
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ParentLoadingOrError(state.loading && child == null, state.error, viewModel::refresh)

                if (!state.loading && state.children.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.parent_dashboard_empty_child),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 14.sp,
                                color = colors.muted,
                            )
                            Spacer(Modifier.height(16.dp))
                            KidButton(
                                text = stringResource(R.string.parent_dashboard_empty_pair_action),
                                onClick = onOpenPairing,
                            )
                        }
                    }
                }

                if (child != null) {
                    KidCard(Modifier.clickable(onClick = onOpenProfile)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(child.avatarEmoji(), fontSize = 28.sp)
                            }
                            Spacer(Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(child.name, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 18.sp, color = colors.text)
                                Text(
                                    if (child.online) stringResource(R.string.parent_dashboard_online) else stringResource(R.string.parent_dashboard_offline),
                                    color = if (child.online) colors.safe else colors.muted,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 12.sp,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.parent_dashboard_last_location), color = colors.muted, fontSize = 11.sp, fontWeight = FontBold)
                                Text(
                                    state.lastLocation?.let { "📍 ${it.substringAfterLast('(').removeSuffix(")").ifBlank { it }}" }
                                        ?: stringResource(R.string.parent_location_school),
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 12.sp,
                                    color = colors.text,
                                )
                                Text(state.lastLocationAt ?: "", color = colors.muted, fontSize = 11.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("⏱️", stringResource(R.string.parent_dashboard_stat_screentime), formatDuration(totalSec), Modifier.weight(1f))
                        // GAP-03: 앱통제 API 없음, docs/spec-gaps.md 참고 — Fake 상수, 실API 연결 금지
                        // TODO: Backend Integration - 차단된 앱 개수를 반환하는 API가 추가되면 해당 값으로 교체
                        StatCard("🛡️", stringResource(R.string.parent_dashboard_stat_blocked), stringResource(R.string.parent_dashboard_blocked_count), Modifier.weight(1f))
                        StatCard("⚠️", stringResource(R.string.parent_dashboard_stat_alerts), "${alertCount}건", Modifier.weight(1f))
                    }
                    KidCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.parent_dashboard_usage_title),
                                modifier = Modifier.weight(1f),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 14.sp,
                                color = colors.text,
                            )
                            Text(
                                stringResource(R.string.parent_dashboard_see_all),
                                modifier = Modifier.clickable(onClick = onOpenUsageDetail),
                                color = colors.brand,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // 대시보드에는 많이 사용한 순으로 상위 3개만 노출한다(전체는 '전체 보기').
                        val topUsages = state.usages.sortedByDescending { it.durationSec }.take(3)
                        topUsages.forEachIndexed { i, u ->
                            UsageBar(
                                name = u.key,
                                timeLabel = formatDuration(u.durationSec),
                                progress = u.durationSec.toFloat() / maxUsage,
                                // 앱 사용량 상세 화면(APP 탭)과 같은 기준으로 색을 배정한다.
                                barColor = appColor(u.key),
                            )
                            if (i < topUsages.lastIndex) Spacer(Modifier.height(10.dp))
                        }
                    }
                    KidCard {
                        Text(
                            stringResource(R.string.parent_dashboard_recent),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(8.dp))
                        state.notifications.take(3).forEachIndexed { i, n ->
                            val icon = when (n.type) {
                                "HARMFUL" -> "🚫" to colors.danger
                                "ARRIVAL" -> "📍" to colors.safe
                                else -> "⏱️" to colors.warn
                            }
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                Text(icon.first, fontSize = 18.sp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.message, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 12.sp, color = colors.text)
                                    Text(n.createdAt, fontFamily = KidhackFontFamily, fontWeight = FontSemibold, fontSize = 12.sp, color = colors.muted)
                                }
                            }
                            if (i < state.notifications.take(3).lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
