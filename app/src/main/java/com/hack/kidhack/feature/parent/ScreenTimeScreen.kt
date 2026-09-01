package com.hack.kidhack.feature.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.WheelGuides
import com.hack.kidhack.core.designsystem.component.WheelPicker
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 기본으로 보여 줄 시간. 서버에 값이 없을 때 휠이 어딘가에는 서 있어야 한다. */
private const val DEFAULT_HOURS = 3

data class ScreenTimeUiState(
    /** 약속을 쓰는지. 끄면 서버에 null(제한 없음)을 보낸다. */
    val enabled: Boolean = false,
    val hours: Int = DEFAULT_HOURS,
    val minutes: Int = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val totalMinutes: Int get() = hours * 60 + minutes

    /** 0분은 "하루 종일 잠금"이라 실수로 저장되기 쉽다. 켜 두었다면 최소 1분은 받는다. */
    val canSave: Boolean get() = !saving && (!enabled || totalMinutes > 0)
}

/**
 * 자녀의 하루 사용시간 약속 (`GET`·`PUT /appcontrol/screentime/{childId}`).
 *
 * <p>이 값은 자녀 앱이 받아 가서, 다 쓰면 다른 앱을 열지 못하게 잠근다. 앱 통제 목록의
 * 앱별 한도와는 별개다 — 그쪽은 앱 하나하나, 이쪽은 하루 전체다.
 *
 * <p>서버는 `null` 을 "제한 없음", `0` 을 "한도 0분"으로 구분한다. 화면의 토글이 그
 * 구분을 그대로 나른다 — 끄면 null, 켜면 시간·분 합.
 */
class ScreenTimeViewModel(
    private val appControlRepository: AppControlRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenTimeUiState())
    val state: StateFlow<ScreenTimeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children())
                    ?: return@runCatching null
                appControlRepository.screenTimeLimit(id)
            }.onSuccess { limit ->
                _state.update {
                    it.copy(
                        loading = false,
                        enabled = limit != null,
                        hours = limit?.let { m -> m / 60 } ?: DEFAULT_HOURS,
                        minutes = limit?.let { m -> m % 60 } ?: 0,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage("한도를 불러오지 못했어요")) }
            }
        }
    }

    fun onEnabled(value: Boolean) = _state.update { it.copy(enabled = value, saved = false, error = null) }
    fun onHours(value: Int) = _state.update { it.copy(hours = value, saved = false) }
    fun onMinutes(value: Int) = _state.update { it.copy(minutes = value, saved = false) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, saved = false) }
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching null
                appControlRepository.setScreenTimeLimit(
                    id,
                    if (current.enabled) current.totalMinutes else null,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure { t ->
                _state.update { it.copy(saving = false, error = t.userMessage("한도를 저장하지 못했어요")) }
            }
        }
    }
}

@Composable
fun ParentScreenTimeScreen(
    onBack: () -> Unit = {},
    viewModel: ScreenTimeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_screentime_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading, state.error, viewModel::load)

        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_screentime_toggle),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.parent_screentime_toggle_desc),
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                KidSwitch(checked = state.enabled, onCheckedChange = viewModel::onEnabled)
            }

            if (state.enabled) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.border)
                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.parent_screentime_how_long),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                // 강조선은 두 휠을 가로질러 한 줄로 긋는다. 휠마다 그리면 가운데가 끊겨 보인다.
                Box(contentAlignment = Alignment.Center) {
                    WheelGuides()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WheelPicker(
                            values = HOUR_VALUES,
                            value = state.hours,
                            onValueChange = viewModel::onHours,
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.parent_screentime_unit_hour),
                            showGuides = false,
                        )
                        WheelPicker(
                            values = MINUTE_VALUES,
                            value = state.minutes,
                            onValueChange = viewModel::onMinutes,
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.parent_screentime_unit_minute),
                            showGuides = false,
                        )
                    }
                }
            }
        }

        if (state.enabled) {
            Text(
                stringResource(R.string.parent_screentime_when_used_up),
                color = colors.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            KidCard {
                Row(verticalAlignment = Alignment.Top) {
                    Text("🔒", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.parent_screentime_lock_title),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.parent_screentime_lock_desc),
                            color = colors.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (state.saved) {
            Text(
                stringResource(R.string.parent_screentime_saved),
                color = colors.safe,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        KidButton(
            text = stringResource(
                if (state.saving) R.string.parent_screentime_saving else R.string.parent_screentime_save
            ),
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
        )
    }
}

/** 하루를 넘기는 약속은 의미가 없다. */
private val HOUR_VALUES = (0..23).toList()

/** 5분 단위. 1분 단위로 두면 60 칸을 굴려야 해서 고르기 어렵다. */
private val MINUTE_VALUES = (0..55 step 5).toList()
