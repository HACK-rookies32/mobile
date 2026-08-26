package com.hack.kidhack.feature.child.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingPermissionKind {
    RuntimeMulti,
    RuntimeSingle,
    SettingsUsage,
    SettingsOverlay,
    SettingsAccessibility,
    /** App details / location permission page (e.g. background location on API 30+). */
    SettingsAppDetails,
    MediaProjection,
}

data class OnboardingStep(
    val kind: OnboardingPermissionKind,
    val runtimePermission: String? = null,
    /** Required for child control — cannot skip until granted. */
    val required: Boolean = true,
    @StringRes val emojiRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

data class ChildOnboardingUiState(
    val items: List<OnboardingStep> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
    /** 설정 앱에서만 켤 수 있는 특별 권한들의 현재 상태. */
    val harmfulBlockEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
) {
    /** 위치 → 알림 → 카메라 → 나중에 설정 안내 */
    val totalSteps: Int = 4
}

class ChildOnboardingViewModel(
    private val store: ChildOnboardingStore,
    private val deviceStatus: ChildDeviceStatus,
)

class ChildOnboardingViewModel(
    private val store: ChildOnboardingStore,
    private val platformController: ChildPlatformController,
    private val persistDone: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChildOnboardingUiState(items = buildItems()),
    )
    val state: StateFlow<ChildOnboardingUiState> = _state.asStateFlow()

    init {
        refreshPermissions()
    }

    /**
     * 접근성·사용정보 접근·오버레이는 런타임 권한 다이얼로그가 없어서
     * 설정 앱에 다녀온 뒤 결과를 직접 다시 읽어야 한다.
     */
    fun refreshPermissions() {
        viewModelScope.launch {
            val device = deviceStatus.snapshot()
            _state.update {
                it.copy(
                    harmfulBlockEnabled = device.harmfulBlockEnabled,
                    usageAccessGranted = device.usageAccessGranted,
                    overlayGranted = device.overlayGranted,
                )
            }
        }
    }

    fun next() {
        val current = _state.value
        if (current.step >= current.totalSteps - 1) {
            finish()
        } else {
            _state.update { it.copy(step = it.step + 1) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun resetFinished() = _state.update { it.copy(finished = false, error = null) }

    fun denyOrMissingRequired() {
        _state.update {
            it.copy(error = "권한이 필요해요. 「허용하기」를 다시 눌러 주세요.")
        }
    }

    fun notifyNeedForegroundLocationFirst() {
        _state.update {
            it.copy(error = "먼저 위 위치 권한을 허용한 뒤, 백그라운드 위치를 설정해 주세요.")
        }
    }

    fun notifyPermissionBlocked() {
        _state.update {
            it.copy(
                error = "시스템 팝업이 더 이상 안 떠요. 설정에서 권한을 허용한 뒤 돌아와 주세요.",
            )
        }
    }

    fun finish() {
        viewModelScope.launch {
            if (persistDone) {
                store.markDone()
                platformController.reevaluate()
            }
            _state.update { it.copy(finished = true, error = null) }
        }
    }

    companion object {
        fun buildItems(): List<OnboardingStep> = buildList {
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.RuntimeMulti,
                    required = true,
                    emojiRes = R.string.child_onboarding_location_emoji,
                    titleRes = R.string.child_onboarding_location_title,
                    bodyRes = R.string.child_onboarding_location_body,
                ),
            )
            if (KidhackPermissions.backgroundLocation != null) {
                add(
                    OnboardingStep(
                        // API 30+: requestPermissions opens the app Location permission page
                        // (Allow all the time). API 29: system dialog.
                        kind = OnboardingPermissionKind.RuntimeSingle,
                        runtimePermission = KidhackPermissions.backgroundLocation,
                        required = true,
                        emojiRes = R.string.child_onboarding_bg_location_emoji,
                        titleRes = R.string.child_onboarding_bg_location_title,
                        bodyRes = R.string.child_onboarding_bg_location_body,
                    ),
                )
            }
            if (KidhackPermissions.notifications != null) {
                add(
                    OnboardingStep(
                        kind = OnboardingPermissionKind.RuntimeSingle,
                        runtimePermission = KidhackPermissions.notifications,
                        required = true,
                        emojiRes = R.string.child_onboarding_notif_emoji,
                        titleRes = R.string.child_onboarding_notif_title,
                        bodyRes = R.string.child_onboarding_notif_body,
                    ),
                )
            }
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.RuntimeSingle,
                    runtimePermission = KidhackPermissions.camera,
                    required = false,
                    emojiRes = R.string.child_onboarding_camera_emoji,
                    titleRes = R.string.child_onboarding_camera_title,
                    bodyRes = R.string.child_onboarding_camera_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsUsage,
                    required = true,
                    emojiRes = R.string.child_onboarding_usage_emoji,
                    titleRes = R.string.child_onboarding_usage_title,
                    bodyRes = R.string.child_onboarding_usage_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsOverlay,
                    required = true,
                    emojiRes = R.string.child_onboarding_overlay_emoji,
                    titleRes = R.string.child_onboarding_overlay_title,
                    bodyRes = R.string.child_onboarding_overlay_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsAccessibility,
                    required = true,
                    emojiRes = R.string.child_onboarding_accessibility_emoji,
                    titleRes = R.string.child_onboarding_accessibility_title,
                    bodyRes = R.string.child_onboarding_accessibility_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.MediaProjection,
                    required = true,
                    emojiRes = R.string.child_onboarding_capture_emoji,
                    titleRes = R.string.child_onboarding_capture_title,
                    bodyRes = R.string.child_onboarding_capture_body,
                ),
            )
        }
    }
}
