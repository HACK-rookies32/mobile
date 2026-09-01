package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.ChildChips
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.hack.kidhack.data.remote.userMessage

enum class AppControlFilter { ALL, BLOCKED, ALLOWED, LIMITED }

data class AppControlUiState(
    /** 자녀 목록 (id to 이름). 이 화면의 모든 설정은 고른 자녀 한 명 기준이다. */
    val children: List<Pair<String, String>> = emptyList(),
    val selectedChildId: String? = null,
    val items: List<AppControlItem> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedApp: InstalledApp? = null,
    val filter: AppControlFilter = AppControlFilter.ALL,
    val showAppSelection: Boolean = false,
    val showControlSettings: Boolean = false,
    /** 자녀 하루 총 사용 한도(분). null 이면 제한 없음 — 0("한도 0분")과 의미가 다르다. */
    val dailyLimitMinutes: Int? = null,
    val savingLimit: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class AppControlViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appControlRepository: AppControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppControlUiState())
    val state: StateFlow<AppControlUiState> = _state.asStateFlow()

    init { load() }

    fun onFilter(f: AppControlFilter) = _state.update { it.copy(filter = f) }
    
    fun toggleAppSelection(show: Boolean) {
        _state.update { it.copy(showAppSelection = show) }
        if (show) loadInstalledApps()
    }

    fun selectAppForControl(app: InstalledApp) {
        _state.update { it.copy(selectedApp = app, showAppSelection = false, showControlSettings = true) }
    }

    fun closeControlSettings() {
        _state.update { it.copy(showControlSettings = false, selectedApp = null) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                    ?: return@runCatching Triple(children, null, emptyList<AppControlItem>() to null)
                // 총 한도는 부가 정보라 실패해도 규칙 목록은 그대로 보여 준다.
                val limit = runCatching { appControlRepository.screenTimeLimit(id) }.getOrNull()
                Triple(children, id, appControlRepository.items(id) to limit)
            }.onSuccess { (children, id, loaded) ->
                val (items, limit) = loaded
                _state.update {
                    it.copy(
                        loading = false,
                        children = children.map { c -> c.id to c.name },
                        selectedChildId = id,
                        items = items,
                        dailyLimitMinutes = limit,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    /**
     * 볼 자녀를 바꾼다.
     *
     * <p>고른 자녀는 앱 전체가 공유한다(대시보드·캡처·사용량도 같은 값을 본다). 화면마다
     * 다른 자녀를 보고 있으면 부모가 다른 아이 설정을 건드리게 된다.
     */
    fun selectChild(childId: String) {
        if (childId == _state.value.selectedChildId) return
        viewModelScope.launch {
            selectedChildRepository.select(childId)
            load()
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching emptyList()
                appControlRepository.getInstalledApps(id)
            }.onSuccess { apps ->
                _state.update { it.copy(installedApps = apps) }
            }
        }
    }

    /**
     * 차단 토글. 서버에 `blocked` 필드가 없어 **하루 한도 0분**으로 표현한다.
     * 해제하면 이전 한도를 알 수 없으므로 무제한(null)으로 되돌린다.
     */
    fun toggle(item: AppControlItem, blocked: Boolean) {
        viewModelScope.launch {
            runCatching {
                appControlRepository.updateRule(
                    ruleId = item.ruleId,
                    dailyLimitMinutes = if (blocked) 0 else null,
                    enabled = true,
                )
            }.onSuccess { updated ->
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.ruleId == updated.ruleId) updated else it })
                }
            }
        }
    }

    fun saveControl(blocked: Boolean, limitMinutes: Int?) {
        val app = _state.value.selectedApp ?: return
        viewModelScope.launch {
            runCatching {
                val id = selectedChildRepository.current() ?: return@launch
                val existing = _state.value.items.firstOrNull { it.packageName == app.packageName }
                val limit = if (blocked) 0 else limitMinutes
                if (existing != null) {
                    appControlRepository.updateRule(existing.ruleId, limit, enabled = true)
                } else {
                    appControlRepository.addRule(id, app.packageName, app.appName, limit)
                }
            }.onSuccess { newItem ->
                _state.update { s ->
                    s.copy(
                        items = (s.items.filterNot { it.packageName == newItem.packageName } + newItem),
                        showControlSettings = false,
                        selectedApp = null
                    )
                }
            }
        }
    }

    /**
     * 하루 총 사용 한도 저장. 빈 값이면 제한을 푼다(서버에 null 을 보낸다).
     *
     * 앱 통제 규칙의 한도는 **앱별**이고 이 값은 **하루 전체**다. 서버에서도 별개 개념이다.
     */
    fun saveDailyLimit(minutes: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(savingLimit = true, error = null) }
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching null
                appControlRepository.setScreenTimeLimit(id, minutes)
            }.onSuccess { applied ->
                _state.update { it.copy(savingLimit = false, dailyLimitMinutes = applied) }
            }.onFailure { t ->
                _state.update { it.copy(savingLimit = false, error = t.userMessage("한도를 저장하지 못했어요")) }
            }
        }
    }

    fun removeControl(ruleId: String) {
        viewModelScope.launch {
            runCatching { appControlRepository.deleteRule(ruleId) }
                .onSuccess {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.ruleId == ruleId }) }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentAppControlScreen(
    onBack: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    viewModel: AppControlViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val filtered = state.items.filter { item ->
        when (state.filter) {
            AppControlFilter.ALL -> true
            AppControlFilter.BLOCKED -> item.blocked
            AppControlFilter.ALLOWED -> !item.blocked && (item.limit == "무제한")
            AppControlFilter.LIMITED -> !item.blocked && item.limit != "무제한" && item.limit != "차단"
        }
    }

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_appcontrol_title), onBack)
            // 이 화면의 모든 설정이 자녀 한 명 기준이라, 무엇보다 먼저 누구인지 고르게 한다.
            if (state.children.size >= 2) {
                Spacer(Modifier.height(12.dp))
                ChildChips(
                    children = state.children,
                    selectedId = state.selectedChildId,
                    onSelect = viewModel::selectChild,
                    onDark = true,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.parent_filter_all), state.filter == AppControlFilter.ALL) { viewModel.onFilter(AppControlFilter.ALL) }
                FilterChip(stringResource(R.string.parent_filter_blocked), state.filter == AppControlFilter.BLOCKED) { viewModel.onFilter(AppControlFilter.BLOCKED) }
                FilterChip(stringResource(R.string.parent_filter_allowed), state.filter == AppControlFilter.ALLOWED) { viewModel.onFilter(AppControlFilter.ALLOWED) }
                FilterChip(stringResource(R.string.parent_filter_limited), state.filter == AppControlFilter.LIMITED) { viewModel.onFilter(AppControlFilter.LIMITED) }
            }
        },
    ) {
        DailyLimitRow(current = state.dailyLimitMinutes, onClick = onOpenScreenTime)

        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)
        
        filtered.forEach { app ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { Text("📱", fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Text(app.packageName, color = colors.muted, fontSize = 12.sp)
                    }
                    if (!app.blocked && app.limit != "무제한") {
                        Text(
                            app.limit,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.brandLight)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = colors.brand,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    KidSwitch(
                        checked = !app.blocked,
                        onCheckedChange = { viewModel.toggle(app, !it) },
                        trackColor = if (app.blocked) colors.danger else colors.safe,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        KidButton(
            text = stringResource(R.string.parent_appcontrol_add),
            onClick = { viewModel.toggleAppSelection(true) },
            modifier = Modifier.fillMaxWidth(),
            variant = KidButtonVariant.Secondary,
        )
    }

    if (state.showAppSelection) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleAppSelection(false) },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "통제할 앱 선택",
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                    color = colors.text
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.installedApps) { app ->
                        val alreadyAdded = state.items.any { it.packageName == app.packageName }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (alreadyAdded) colors.surface else Color.Transparent)
                                .clickable(enabled = !alreadyAdded) { viewModel.selectAppForControl(app) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📱", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.appName, fontWeight = FontBold, fontSize = 14.sp)
                                if (alreadyAdded) {
                                    Text("이미 추가됨", color = colors.brand, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (state.showControlSettings && state.selectedApp != null) {
        val app = state.selectedApp!!
        var isBlocked by remember { mutableStateOf(false) }
        var limitMinutes by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { viewModel.closeControlSettings() },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    text = "${app.appName} 설정",
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("앱 차단", Modifier.weight(1f), fontWeight = FontSemibold)
                    KidSwitch(checked = isBlocked, onCheckedChange = { isBlocked = it })
                }
                
                if (!isBlocked) {
                    Spacer(Modifier.height(16.dp))
                    Text("일일 시간 제한 (분)", fontWeight = FontSemibold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    KidTextField(
                        value = limitMinutes,
                        onValueChange = { if (it.all { c -> c.isDigit() }) limitMinutes = it },
                        label = "제한 시간(분)",
                        placeholder = "예: 60",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(32.dp))
                KidButton(
                    text = "저장하기",
                    onClick = { viewModel.saveControl(isBlocked, limitMinutes.toIntOrNull()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/**
 * 하루 사용시간 약속으로 들어가는 줄.
 *
 * <p>설정 자체는 별도 화면(`ParentScreenTimeScreen`)에서 한다. 앱별 규칙 목록과 성격이
 * 달라(이건 하루 전체, 아래는 앱 하나하나) 같은 화면에 섞어 두면 무엇을 바꾸는지
 * 헷갈렸다. 여기서는 현재 값만 보여 준다.
 */
@Composable
private fun DailyLimitRow(current: Int?, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    KidCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏳", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.parent_screentime_entry),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Text(
                    text = if (current == null) {
                        stringResource(R.string.parent_screentime_entry_off)
                    } else {
                        stringResource(R.string.parent_screentime_entry_value, formatMinutes(current))
                    },
                    color = colors.muted,
                    fontSize = 12.sp,
                )
            }
            Text("›", color = colors.border, fontSize = 18.sp)
        }
    }
}

/** 180 → "3시간", 90 → "1시간 30분", 45 → "45분". */
private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> h.toString() + "시간 " + m + "분"
        h > 0 -> h.toString() + "시간"
        else -> m.toString() + "분"
    }
}
