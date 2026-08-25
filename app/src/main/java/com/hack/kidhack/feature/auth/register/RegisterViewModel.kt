package com.hack.kidhack.feature.auth.register

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.core.common.formatPhoneNumber
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val step: Int = 0,
    val role: Role? = null,
    val nickname: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val birthDate: String = "",
    val address: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val parentId: String = "",
    val agreedAge: Boolean = false,
    val agreedTerms: Boolean = false,
    val agreedPrivacy: Boolean = false,
    val agreedLocation: Boolean = false,
    val agreedPush: Boolean = false,
    val agreedMarketing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val session: Session? = null,
) {
    val agreedAll: Boolean
        get() = agreedAge && agreedTerms && agreedPrivacy && agreedLocation && agreedPush && agreedMarketing

    val requiredAgreed: Boolean
        get() = agreedAge && agreedTerms && agreedPrivacy && agreedLocation && agreedPush

    val doneStep: Int
        get() = if (role == Role.CHILD) 4 else 3
}

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val onboardingStore: ChildOnboardingStore,
    private val platformController: ChildPlatformController,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun selectRole(role: Role) = _state.update { it.copy(role = role, step = 1, error = null) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    fun onNickname(v: String) = _state.update { it.copy(nickname = v) }
    fun onUsername(v: String) = _state.update { it.copy(username = v) }
    fun onPhoneNumber(v: String) = _state.update {
        it.copy(phoneNumber = formatPhoneNumber(v))
    }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onBirthDate(v: String) = _state.update { it.copy(birthDate = v) }
    fun onAddress(v: String) = _state.update { it.copy(address = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun onPasswordConfirm(v: String) = _state.update { it.copy(passwordConfirm = v) }
    fun onParentId(v: String) = _state.update { it.copy(parentId = v.filter { ch -> ch.isDigit() }) }
    fun onAgreedAge(v: Boolean) = _state.update { it.copy(agreedAge = v) }
    fun onAgreedTerms(v: Boolean) = _state.update { it.copy(agreedTerms = v) }
    fun onAgreedPrivacy(v: Boolean) = _state.update { it.copy(agreedPrivacy = v) }
    fun onAgreedLocation(v: Boolean) = _state.update { it.copy(agreedLocation = v) }
    fun onAgreedPush(v: Boolean) = _state.update { it.copy(agreedPush = v) }
    fun onAgreedMarketing(v: Boolean) = _state.update { it.copy(agreedMarketing = v) }
    fun onAgreedAll(v: Boolean) = _state.update {
        it.copy(
            agreedAge = v,
            agreedTerms = v,
            agreedPrivacy = v,
            agreedLocation = v,
            agreedPush = v,
            agreedMarketing = v,
        )
    }

    fun nextFromInfo() {
        val s = _state.value
        val role = s.role ?: return
        val birthOk = s.birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))
        val parentIdValue = s.parentId.toLongOrNull()
        when {
            s.username.isBlank() -> _state.update { it.copy(error = "아이디를 입력해 주세요") }
            s.phoneNumber.isBlank() -> _state.update { it.copy(error = "휴대폰 번호를 입력해 주세요") }
            role != Role.CHILD && s.email.isBlank() ->
                _state.update { it.copy(error = "이메일을 입력해 주세요") }
            !birthOk -> _state.update { it.copy(error = "생년월일을 선택해 주세요") }
            role == Role.CHILD && (parentIdValue == null || parentIdValue <= 0) ->
                _state.update { it.copy(error = "연결할 부모님 회원 번호를 입력해 주세요") }
            s.password.length < 8 -> _state.update { it.copy(error = "비밀번호는 8자 이상이어야 해요") }
            s.password != s.passwordConfirm -> _state.update { it.copy(error = "비밀번호가 일치하지 않아요") }
            else -> _state.update { it.copy(step = 2, error = null) }
        }
    }

    /** After terms: child goes to OS permissions; parent submits immediately. */
    fun nextFromTerms() {
        val s = _state.value
        val role = s.role ?: return
        when {
            !s.requiredAgreed -> _state.update { it.copy(error = "필수 약관에 모두 동의해 주세요") }
            role == Role.CHILD -> _state.update { it.copy(step = 3, error = null) }
            else -> submit()
        }
    }

    fun submit() {
        val s = _state.value
        val role = s.role ?: return
        when {
            !s.requiredAgreed -> _state.update { it.copy(error = "필수 약관에 모두 동의해 주세요") }
            role == Role.CHILD && !KidhackPermissions.hasAllRequiredForChildControl(appContext) ->
                _state.update {
                    it.copy(error = "자녀 보호에 필요한 권한을 모두 허용해 주세요")
                }
            else -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching {
                    authRepository.signup(
                        username = s.username.trim(),
                        password = s.password,
                        nickname = s.nickname.trim().ifBlank { null },
                        email = s.email.trim().ifBlank { null },
                        phoneNumber = s.phoneNumber.trim(),
                        birthDate = s.birthDate.trim(),
                        address = s.address.trim().ifBlank { null },
                        role = role,
                        parentId = s.parentId.toLongOrNull()?.takeIf { role == Role.CHILD },
                    )
                }.onSuccess { session ->
                    if (role == Role.CHILD) {
                        onboardingStore.markDone()
                        platformController.reevaluate()
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            step = it.doneStep,
                            session = session,
                        )
                    }
                }.onFailure { t ->
                    _state.update {
                        it.copy(loading = false, error = t.userMessage("가입에 실패했어요"))
                    }
                }
            }
        }
    }
}
