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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class NotificationsUiState(
    val settings: NotificationSettings = NotificationSettings(true, true, false, true),
    val notifications: List<AppNotification> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class NotificationsViewModel(
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val s = userRepository.notificationSettings()
                val n = notificationRepository.list(null)
                s to n
            }.onSuccess { (s, n) ->
                _state.update { it.copy(loading = false, settings = s, notifications = n) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
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
    viewModel: NotificationsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_notifications_title), onBack = onBack) },
    ) {
        ParentLoadingOrError(state.loading && state.notifications.isEmpty(), state.error, viewModel::load)
        
        // 알림 설정 영역
        KidCard {
            Text(
                stringResource(R.string.parent_notifications_section),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            NotifRow(stringResource(R.string.parent_notif_location), stringResource(R.string.parent_notif_location_desc), s.location) { checked ->
                viewModel.patch { it.copy(location = checked) }
            }
            NotifRow(stringResource(R.string.parent_notif_harmful), null, s.harmfulAccess) { checked ->
                viewModel.patch { it.copy(harmfulAccess = checked) }
            }
            NotifRow(stringResource(R.string.parent_notif_app_usage), stringResource(R.string.parent_notif_app_usage_desc), s.appUsage) { checked ->
                viewModel.patch { it.copy(appUsage = checked) }
            }
            NotifRow(stringResource(R.string.parent_notif_notice), null, s.notice, showDivider = false) { checked ->
                viewModel.patch { it.copy(notice = checked) }
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
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        if (!state.loading && state.notifications.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.parent_empty), color = colors.muted, fontSize = 14.sp)
            }
        }

        state.notifications.forEach { n ->
            KidCard {
                val icon = when (n.type) {
                    "HARMFUL" -> "🚫" to colors.danger
                    "ARRIVAL" -> "📍" to colors.safe
                    "GPS_OFF" -> "📵" to colors.danger
                    "LOCATION" -> "📍" to colors.info
                    else -> "🔔" to colors.brand
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(icon.first, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = n.title,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = n.body,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 13.sp,
                            color = colors.muted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = n.createdAt,
                            fontSize = 11.sp,
                            color = colors.muted.copy(alpha = 0.7f)
                        )
                    }
                    if (!n.read) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.brand)
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
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
