package com.hack.kidhack.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.PairingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val step: Int = 0,
    val role: Role? = null,
    val name: String = "",
    val loginId: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val pairingCode: String = "",
    val agreed: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val session: Session? = null,
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun selectRole(role: Role) = _state.update { it.copy(role = role, step = 1, error = null) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onLoginId(v: String) = _state.update { it.copy(loginId = v) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPassword(v: String) = _state.update { it.copy(password = v) }
    fun onPasswordConfirm(v: String) = _state.update { it.copy(passwordConfirm = v) }
    fun onPairingCode(v: String) = _state.update { it.copy(pairingCode = v) }
    fun onAgreed(v: Boolean) = _state.update { it.copy(agreed = v) }

    fun nextFromInfo() {
        val s = _state.value
        if (s.name.isBlank() || s.loginId.isBlank() || s.phone.isBlank()) {
            _state.update { it.copy(error = "이름, 아이디, 휴대폰 번호를 입력해 주세요") }
            return
        }
        _state.update { it.copy(step = 2, error = null) }
    }

    fun submit() {
        val s = _state.value
        val role = s.role ?: return
        when {
            s.password.length < 8 -> _state.update { it.copy(error = "비밀번호는 8자 이상이어야 해요") }
            s.password != s.passwordConfirm -> _state.update { it.copy(error = "비밀번호가 일치하지 않아요") }
            !s.agreed -> _state.update { it.copy(error = "약관에 동의해 주세요") }
            role == Role.CHILD && s.pairingCode.length != 6 ->
                _state.update { it.copy(error = "부모님 연결 코드 6자리를 입력해 주세요") }
            else -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                runCatching {
                    val session = authRepository.signup(
                        loginId = s.loginId.trim(),
                        password = s.password,
                        name = s.name.trim(),
                        role = role,
                        phone = s.phone.trim(),
                        email = s.email.trim().ifBlank { null },
                        pairingCode = s.pairingCode.takeIf { role == Role.CHILD },
                    )
                    if (role == Role.CHILD) {
                        pairingRepository.connect(s.pairingCode)
                    }
                    session
                }.onSuccess { session ->
                    _state.update { it.copy(loading = false, step = 3, session = session) }
                }.onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "가입에 실패했어요") }
                }
            }
        }
    }
}
