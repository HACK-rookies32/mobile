package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.BuildConfig
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.hack.kidhack.domain.model.displayTitle
import com.hack.kidhack.domain.model.body

data class NotificationsUiState(
    val settings: NotificationSettings = NotificationSettings(true, true, false, true),
    val notifications: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** 테스트 버튼 결과처럼 오류는 아니지만 알려 줘야 하는 것. */
    val notice: String? = null,
)

class NotificationsViewModel(
    private val userRepository: UserRepository,
    private val alertSync: ServerAlertSync,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            val alerts = alertSync.sync().alerts
            runCatching { userRepository.notificationSettings() }
                .onSuccess { s ->
                    _state.update { it.copy(loading = false, settings = s, notifications = alerts) }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, notifications = alerts, error = t.message) }
                }
            alertSync.markAllRead()
        }
    }

    /**
     * 알림 테스트 버튼(디버그 빌드 전용).
     *
     * 서버 폴링과 같은 경로([ServerAlertSync.emitLocal])를 타므로 「최근 알림에 영구 기록 +
     * 푸시 표시」가 실제로 되는지 그대로 확인할 수 있다. 목록을 다시 읽되 읽음 처리는 하지
     * 않는다 — 대시보드의 미확인 건수가 오르는 것까지 봐야 하기 때문이다.
     */
    fun sendTestAlert(label: String, type: String, title: String, body: String) {
        viewModelScope.launch {
            val child = runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                children.firstOrNull { it.id == id }
            }.getOrNull()
            val pushed = alertSync.emitLocal(
                type = type,
                title = title,
                body = child?.let { it.name + " · " + body } ?: body,
                childId = child?.id,
            )
            val alerts = alertSync.cached()
            _state.update {
                it.copy(
                    notifications = alerts,
                    notice = if (pushed) {
                        label + " 알림을 " + (child?.name ?: "전체") + " 앞으로 보냈어요. 상단바를 확인해 주세요."
                    } else {
                        label + " 알림을 기록했어요. 수신 설정이나 알림 권한이 꺼져 있어 푸시는 뜨지 않았어요."
                    },
                )
            }
        }
    }

    fun patch(update: (NotificationSettings) -> NotificationSettings) {
        val oldSettings = _state.value.settings
        val next = update(oldSettings)

        // Optimistic update
        _state.update { it.copy(settings = next) }

        viewModelScope.launch {
            runCatching { userRepository.patchNotificationSettings(next) }
                .onSuccess { s ->
                    _state.update { it.copy(settings = s, error = null) }
                }
                .onFailure { t ->
                    // Rollback on failure
                    _state.update { it.copy(settings = oldSettings, error = t.message) }
                }
        }
    }
}

@Composable
fun ParentNotificationsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 알림 목록은 명세상 비페이징(배열 그대로) — 일반 스크롤 Column 으로 전부 렌더.
    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_notifications_title), onBack = onBack) },
    ) {
        ParentLoadingOrError(state.loading && state.notifications.isEmpty(), state.error, viewModel::load)

        // 개발 빌드에서만 보이는 알림 점검 도구. 릴리즈에는 들어가지 않는다.
        // 버튼을 누를수록 아래 목록이 길어지므로 목록 위에 둔다.
        if (BuildConfig.DEBUG) {
            TestAlertCard(state.notice) { sample ->
                viewModel.sendTestAlert(
                    label = context.getString(sample.labelRes),
                    type = sample.type,
                    title = context.getString(sample.titleRes),
                    body = context.getString(sample.bodyRes),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 알림 목록 영역
        Text(
            text = stringResource(R.string.parent_dashboard_recent),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 16.sp,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        if (!state.loading && state.notifications.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.parent_empty), color = colors.muted, fontSize = 14.sp)
            }
        }

        state.notifications.forEach { n -> NotificationCard(n) }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NotificationCard(n: AppNotification) {
    val colors = LocalKidhackColors.current
    KidCard {
        // 명세 type enum 기준 아이콘 매핑.
        val icon = when (n.type) {
            "LOCATION_ENTER" -> "📍"
            "LOCATION_ARRIVED" -> "🏫"
            "LOCATION_EXIT" -> "🚶"
            "DEVICE_POWER_OFF" -> "📵"
            "HARMFUL_SITE" -> "🚫"
            "PAYMENT_APPROVED" -> "💳"
            "PAYMENT_CANCELED" -> "↩️"
            "CHAT_MESSAGE" -> "💬"
            "SCREEN_TIME_LIMIT" -> "⏰"
            "APP_USAGE" -> "📊"
            "NOTICE" -> "📢"
            "INQUIRY_ANSWERED" -> "📮"
            "ALLOWANCE_RECEIVED" -> "💰"
            "ALLOWANCE_RECLAIMED" -> "↪️"
            else -> "🔔"
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // 서버가 제목을 준다(#150 이후). 없으면 message 첫 줄로 떨어진다.
                Text(
                    text = n.displayTitle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                if (n.body.isNotBlank()) {
                    Text(
                        text = n.body,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = n.createdAt,
                    fontSize = 11.sp,
                    color = colors.muted.copy(alpha = 0.7f),
                )
            }
            if (!n.read) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                )
            }
        }
    }
}

/**
 * 알림 종류별 테스트 샘플.
 *
 * [type] 은 서버가 내려 주는 값과 같은 문자열이라 아이콘 매핑과 수신 설정(알림 탭 토글)
 * 필터를 그대로 탄다. 즉 버튼으로 띄운 알림은 실제 알림과 같은 경로를 지난다.
 */
private data class AlertSample(
    val type: String,
    val labelRes: Int,
    val titleRes: Int,
    val bodyRes: Int,
)

private val TestAlertSamples = listOf(
    AlertSample("GPS_OFF", R.string.parent_notif_test_gps, R.string.parent_notif_test_gps_title, R.string.parent_notif_test_gps_body),
    AlertSample("LOCATION", R.string.parent_notif_test_geofence, R.string.parent_notif_test_geofence_title, R.string.parent_notif_test_geofence_body),
    AlertSample("HARMFUL", R.string.parent_notif_test_harmful, R.string.parent_notif_test_harmful_title, R.string.parent_notif_test_harmful_body),
    AlertSample("APP_USAGE", R.string.parent_notif_test_usage, R.string.parent_notif_test_usage_title, R.string.parent_notif_test_usage_body),
    AlertSample("NOTICE", R.string.parent_notif_test_notice, R.string.parent_notif_test_notice_title, R.string.parent_notif_test_notice_body),
)

@Composable
private fun TestAlertCard(notice: String?, onSend: (AlertSample) -> Unit) {
    val colors = LocalKidhackColors.current
    KidCard {
        Text(
            stringResource(R.string.parent_notif_test_section),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Text(
            stringResource(R.string.parent_notif_test_desc),
            fontSize = 12.sp,
            color = colors.muted,
        )
        TestAlertSamples.forEach { sample ->
            KidButton(
                text = stringResource(sample.labelRes),
                onClick = { onSend(sample) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                variant = KidButtonVariant.Secondary,
            )
        }
        if (notice != null) {
            Spacer(Modifier.height(4.dp))
            Text(notice, fontSize = 12.sp, color = colors.muted)
        }
    }
}

@Composable
private fun NotifRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    showDivider: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = KidhackFontFamily, fontWeight = FontSemibold, fontSize = 14.sp, color = colors.text)
                if (subtitle != null) {
                    Text(subtitle, color = colors.muted, fontSize = 12.sp)
                }
            }
            KidSwitch(checked, onChecked)
        }
        if (showDivider) HorizontalDivider(color = colors.border)
    }
}
