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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.permissionRequester
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.background.alert.ParentChildWatcher
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
import com.hack.kidhack.core.common.todayRange
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.DashboardRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SessionRepository
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
    val blockedAppCount: Int = 0,
    /** 오늘 쓸 수 있는 시간(분). null 이면 약속을 안 걸어 둔 것이다. */
    val dailyLimitMinutes: Int? = null,
    /** 보관함 전체. 화면에 그리는 건 아래 [notifications] 로 걸러 낸 것이다. */
    val allNotifications: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val parentName: String = "부모",
) {
    /**
     * 지금 고른 자녀의 알림.
     *
     * 자녀가 여러 명이면 대시보드의 모든 지표가 고른 자녀 기준이어야 한다.
     * 사용량·위치는 원래 childId 로 받아 오는데 알림만 계정 단위라 섞여 보였다.
     *
     * 자녀를 특정할 수 없는 알림(서버가 childId 를 안 준 경우)은 감추지 않는다.
     * 잘못 숨기면 보호자가 사건 자체를 놓친다.
     */
    val notifications: List<AppNotification>
        get() = allNotifications.filter { it.childId == null || it.childId == selectedId }
}

class DashboardViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appUsageRepository: AppUsageRepository,
    private val appControlRepository: AppControlRepository,
    private val dashboardRepository: DashboardRepository,
    private val alertSync: ServerAlertSync,
    private val childWatcher: ParentChildWatcher,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { id ->
                if (_state.value.children.isNotEmpty()) loadFor(id)
            }
        }
        // 알림 보관함을 구독한다. 워커가 새 알림을 받아 넣거나 기기에서 사건이 감지되면
        // 당겨서 새로고침하지 않아도 「최근 알림」이 바로 바뀐다.
        viewModelScope.launch {
            alertSync.alerts.collect { alerts ->
                _state.update { it.copy(allNotifications = alerts) }
            }
        }
        refresh()
    }

    /**
     * 알림만 다시 받아 온다.
     *
     * 자녀 목록·사용량까지 다시 부르는 [refresh] 와 달리 가볍기 때문에
     * 대시보드 탭으로 돌아올 때마다 호출해도 부담이 없다.
     */
    fun refreshAlerts() {
        viewModelScope.launch { alertSync.sync() }
    }

    /**
     * 자녀 상태를 지금 판정한다.
     *
     * 15분 주기 워커를 기다리지 않고 당겨서 새로고침하면 바로 확인할 수 있게 한다.
     */
    private fun evaluateChildren() {
        viewModelScope.launch { runCatching { childWatcher.evaluateAll() } }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    parentName = sessionRepository.current()?.user?.name?.trim()
                        ?.takeIf { name -> name.isNotBlank() } ?: "부모",
                )
            }
            runCatching {
                // TODO: Backend Integration - Real API 연결 시 childRepository의 실구현체(RealChildRepository)를 사용하도록 di/AppModule.kt 수정
                val children = childRepository.children()
                val selectedId = selectedChildRepository.resolveId(children)
                Triple(children, selectedId, selectedId)
            }.onSuccess { (children, selectedId, _) ->
                _state.update { it.copy(children = children, selectedId = selectedId) }
                loadFor(selectedId)
                evaluateChildren()
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
            // 앱 사용량은 대시보드 통합 응답에 없어 개별 조회한다.
            // 범위를 안 주면 서버가 최근 1주일을 합산해 「오늘」이 아니게 된다.
            val (from, to) = todayRange()
            val usages = appUsageRepository.list(childId, "APP", from, to)
            val dashboard = dashboardRepository.get(childId)
            // 「차단된 앱」 = 하루 한도 0분인 규칙. 서버에 blocked 필드가 없어 이렇게 센다.
            // 부가 정보라 실패해도 대시보드 나머지는 그대로 간다.
            val blockedApps = runCatching {
                appControlRepository.items(childId).count { it.blocked }
            }.getOrDefault(0)
            // 하루 사용시간 약속. 안 걸어 뒀으면 null 이고, 그때는 카드를 그리지 않는다.
            val dailyLimit = runCatching { appControlRepository.screenTimeLimit(childId) }.getOrNull()
            alertSync.sync()
            _state.update {
                it.copy(
                    loading = false,
                    error = null,
                    selectedId = childId,
                    selected = child,
                    usages = usages,
                    blockedAppCount = blockedApps,
                    dailyLimitMinutes = dailyLimit,
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
    val context = LocalContext.current
    val permissions = permissionRequester()

    // 대시보드로 돌아올 때마다 알림만 가볍게 다시 받아 온다.
    // (앱이 꺼져 있는 동안 서버에 쌓인 알림은 화면을 열어야 확인할 수 있다.)
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else viewModel.refreshAlerts()
    }

    // 안전 구역 이탈·유해 사이트 차단을 푸시로 받으려면 알림 권한이 필요하다(안드로이드 13+).
    // 이미 거절해서 시스템 대화상자가 뜨지 않는 상태면 조용히 넘어간다.
    LaunchedEffect(Unit) {
        val permission = KidhackPermissions.notifications ?: return@LaunchedEffect
        val activity = KidhackPermissions.findActivity(context) ?: return@LaunchedEffect
        if (!KidhackPermissions.canShowSystemDialog(activity, permission)) return@LaunchedEffect
        permissions.requestOne(permission) { granted ->
            if (!granted) KidhackPermissions.recordDeny(context, permission)
        }
    }

    val child = state.selected
    val totalSec = state.usages.sumOf { it.durationSec }
    val alertCount = state.notifications.count { !it.read }
    val maxUsage = state.usages.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1

    ParentScreenScaffold(
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_hello, state.parentName),
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
                                // 서버 ChildSummaryResponse 에 접속 여부(online)가 없다.
                                // 목록이 실제로 주는 값은 실시간 추적 on/off 라 그걸 보여 준다.
                                Text(
                                    if (child.realtimeEnabled) {
                                        stringResource(R.string.parent_dashboard_tracking_on)
                                    } else {
                                        stringResource(R.string.parent_dashboard_tracking_off)
                                    },
                                    color = if (child.realtimeEnabled) colors.safe else colors.muted,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 12.sp,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.parent_dashboard_last_location), color = colors.muted, fontSize = 11.sp, fontWeight = FontBold)
                                Text(
                                    // 위치 기록이 없으면 「학교」로 메우고 있었다. 없는 걸 없다고 말한다.
                                    state.lastLocation?.let { "📍 ${it.substringAfterLast('(').removeSuffix(")").ifBlank { it }}" }
                                        ?: stringResource(R.string.parent_dashboard_no_location),
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
                        StatCard("🛡️", stringResource(R.string.parent_dashboard_stat_blocked), "${state.blockedAppCount}개", Modifier.weight(1f))
                        StatCard("⚠️", stringResource(R.string.parent_dashboard_stat_alerts), "${alertCount}건", Modifier.weight(1f))
                    }
                    ScreenTimePromiseCard(usedSeconds = totalSec, limitMinutes = state.dailyLimitMinutes)
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

/**
 * 오늘 얼마나 쓸 수 있고 얼마나 썼는지.
 *
 * <p>대시보드의 「스크린타임」 통계는 쓴 시간만 보여 줘서, 부모가 정한 약속에 견줘 많은지
 * 적은지 알 수 없었다. 남은 시간을 같이 보여 준다.
 *
 * <p>약속을 안 걸어 뒀으면(null) 그리지 않는다 — 없는 한도에 대고 막대를 그리면
 * 제한이 있는 것처럼 보인다.
 */
@Composable
private fun ScreenTimePromiseCard(usedSeconds: Int, limitMinutes: Int?) {
    if (limitMinutes == null || limitMinutes <= 0) return
    val colors = LocalKidhackColors.current
    val usedMinutes = usedSeconds / 60
    val ratio = (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
    val over = usedMinutes >= limitMinutes
    val barColor = if (over) colors.danger else colors.brand

    KidCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.parent_dashboard_promise_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.parent_dashboard_promise_limit, formatMinutesLabel(limitMinutes)),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (over) {
                stringResource(R.string.parent_dashboard_promise_used_up, formatMinutesLabel(usedMinutes))
            } else {
                stringResource(
                    R.string.parent_dashboard_promise_remaining,
                    formatMinutesLabel(usedMinutes),
                    formatMinutesLabel(limitMinutes - usedMinutes),
                )
            },
            color = if (over) colors.danger else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}

/** 180 → "3시간", 90 → "1시간 30분", 45 → "45분". */
private fun formatMinutesLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> h.toString() + "시간 " + m + "분"
        h > 0 -> h.toString() + "시간"
        else -> m.toString() + "분"
    }
}
