package com.hack.kidhack.feature.child.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.repository.notificationRecipientId
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.feature.child.formatDurationMin
import com.hack.kidhack.feature.child.formatTimeLabel
import com.hack.kidhack.feature.child.remainingLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeServiceRow(
    val label: String,
    val desc: String,
    val emoji: String,
    val active: Boolean,
    /**
     * 꺼져 있을 때 눌러서 열 설정 화면. null 이면 앱에서 켤 방법이 없다는 뜻.
     *
     * 온보딩을 건너뛴 자녀도 여기서 바로 켤 수 있어야 한다.
     */
    val settingsAction: String? = null,
)

data class HomeAlertRow(
    val message: String,
    val time: String,
    val emoji: String,
    val tint: AlertTint,
    val opensScreenshot: Boolean = false,
    val opensChat: Boolean = false,
    val opensCamera: Boolean = false,
)

enum class AlertTint { Brand, Danger, Warn, Info }

data class ChildHomeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val alias: String = "",
    val usedMinutes: Int = 0,
    val protectedNow: Boolean = false,
    val services: List<HomeServiceRow> = emptyList(),
    val alerts: List<HomeAlertRow> = emptyList(),
    val error: String? = null,
) {
    val usedLabel: String get() = formatDurationMin(usedMinutes)

    /**
     * 꺼져 있는데 자녀가 켤 수 있는 보호 기능들.
     *
     * 접근성은 앱을 강제 중지하거나 업데이트하면 안드로이드가 꺼 버린다(앱이 막을 수 없다).
     * 그래서 앱을 열 때마다 확인해서 눈에 띄게 알려 준다.
     */
    val needsAttention: List<HomeServiceRow>
        get() = services.filter { !it.active && it.settingsAction != null }

}

/**
 * 자녀 홈 탭.
 *
 * 스크린타임은 실제 앱 사용량 합계, 「루키즈가 지키고 있어요」 배지는 실제 권한·서비스 상태,
 * 최근 알림은 알림 API 에서 가져온다. 하드코딩 문구는 남기지 않는다.
 */
class ChildHomeViewModel(
    private val sessionRepository: SessionRepository,
    private val usageCollector: AppUsageCollector,
    private val notificationRepository: NotificationRepository,
    private val deviceStatus: ChildDeviceStatus,
    private val alertStore: ChildAlertStore,
    private val screenTimeWatcher: ScreenTimeWatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildHomeUiState())
    val state: StateFlow<ChildHomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val user = sessionRepository.current()?.user
            val notifications = runCatching { notificationRepository.list(sessionRepository.notificationRecipientId()) }

            // 스크린타임은 서비스 현황 탭의 앱 사용 모니터링 합계와 반드시 같아야 하므로
            // 같은 수집기를 쓴다.
            val usage = usageCollector.todayUsage()
            val device = deviceStatus.snapshot()
            val usedMinutes = usage.totalMinutes

            // 한도 도달을 여기서도 확인한다(시간당 워커만 믿으면 최대 1시간 늦게 뜬다).
            screenTimeWatcher.check(usedMinutes)

            // 기기에서 실제로 일어난 사건(차단·한도 도달)을 서버 알림보다 앞에 둔다.
            val localAlerts = alertStore.recent()
            val usageTracked = device.usageAccessGranted
            val locationOn = device.locationGranted && device.gpsEnabled
            val harmfulOn = device.harmfulBlockEnabled
            val alertsOn = device.notificationsGranted

            _state.update {
                it.copy(
                    loading = false,
                    name = user?.name.orEmpty(),
                    alias = user?.name?.aliasOrSelf().orEmpty(),
                    usedMinutes = usedMinutes,
                    protectedNow = locationOn || harmfulOn,
                    services = listOf(
                        HomeServiceRow(
                            label = "위치 추적",
                            desc = if (locationOn) "부모님이 위치를 확인 중" else "위치 권한이나 GPS 가 꺼져 있어요",
                            emoji = "📍",
                            active = locationOn,
                        ),
                        HomeServiceRow(
                            label = "유해 콘텐츠 차단",
                            desc = if (harmfulOn) "안전한 인터넷 사용 중" else "눌러서 켜 주세요",
                            emoji = "🚫",
                            active = harmfulOn,
                            settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                        ),
                        HomeServiceRow(
                            label = "앱 사용 모니터링",
                            desc = usageDesc(usageTracked, usage.usages.size),
                            emoji = "📊",
                            active = usageTracked,
                            settingsAction = android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        ),
                        HomeServiceRow(
                            label = "요청 알림",
                            desc = if (alertsOn) "캡처·촬영 요청을 바로 받아요" else "알림 권한이 꺼져 있어요",
                            emoji = "🔔",
                            active = alertsOn,
                        ),
                    ),
                    alerts = (localAlerts + notifications.getOrDefault(emptyList()))
                        // 기기에 남긴 기록과 서버가 돌려준 같은 사건이 나란히 뜨지 않게 한다.
                        .distinctBy { it.message }
                        .take(MAX_ALERTS)
                        .map { alert -> alert.toRow() },
                    error = if (usage.usages.isEmpty() && notifications.isFailure) {
                        "정보를 불러오지 못했어요"
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun usageDesc(tracked: Boolean, count: Int): String = when {
        !tracked -> "눌러서 사용 기록을 켜 주세요"
        count == 0 -> "오늘 기록된 앱이 없어요"
        else -> "오늘 ${count}개 앱 사용 중"
    }

    /**
     * 성 한 글자를 떼어 친근한 호칭으로("김민수" → "민수").
     * 한글 2~4자 이름일 때만 적용한다 — 영문 등 다른 이름은 그대로 쓴다("ChildFour" → "hildFour" 오류 방지).
     */
    private fun String.aliasOrSelf(): String {
        val t = trim()
        val allHangul = t.isNotEmpty() && t.all { it in '가'..'힣' }
        return if (allHangul && t.length in 2..4) t.drop(1) else t
    }

    private fun AppNotification.toRow(): HomeAlertRow {
        val upper = type.uppercase()
        val isCameraRequest = upper.contains("CAMERA")
        val isCapture = !isCameraRequest &&
            (upper.contains("CAPTURE") || upper.contains("SCREENSHOT"))
        val isScheduled = upper.contains("SCHEDULED")
        val isChat = upper.contains("CHAT") || upper.contains("MESSAGE")
        val isHarmful = upper.contains("HARMFUL") || upper.contains("BLOCK")
        val isScreenTime = upper.contains("SCREEN_TIME") || upper.contains("LIMIT")
        return HomeAlertRow(
            message = message,
            time = formatTimeLabel(createdAt),
            emoji = when {
                isCameraRequest -> "📷"
                isCapture -> "📸"
                isHarmful -> "🚫"
                isScreenTime -> "⏰"
                isChat -> "💬"
                else -> "🔔"
            },
            tint = when {
                isHarmful -> AlertTint.Danger
                isScreenTime -> AlertTint.Warn
                isChat -> AlertTint.Info
                isScheduled -> AlertTint.Info
                else -> AlertTint.Brand
            },
            opensScreenshot = isCapture,
            opensChat = isChat,
            opensCamera = isCameraRequest,
        )
    }

    private companion object {
        const val MAX_ALERTS = 3
    }
}
