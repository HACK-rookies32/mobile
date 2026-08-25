package com.hack.kidhack.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotUiState(
    val findName: String = "",
    val findPhone: String = "",
    val foundLoginId: String? = null,
    val resetLoginId: String = "",
    val verifyCode: String = "",
    val newPassword: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class ForgotViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onFindName(v: String) = _state.update { it.copy(findName = v, error = null) }
    fun onFindPhone(v: String) = _state.update { it.copy(findPhone = v, error = null) }
    fun onResetLoginId(v: String) = _state.update { it.copy(resetLoginId = v, error = null) }
    fun onVerifyCode(v: String) = _state.update { it.copy(verifyCode = v, error = null) }
    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, error = null) }

    fun findId() {
        val s = _state.value
        if (s.findName.isBlank() || s.findPhone.isBlank()) {
            _state.update { it.copy(error = "이름과 휴대폰 번호를 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            runCatching { authRepository.findId(s.findName.trim(), s.findPhone.trim()) }
                .onSuccess { id ->
                    _state.update {
                        it.copy(
                            loading = false,
                            foundLoginId = id,
                            resetLoginId = id,
                            info = "아이디는 $id 입니다",
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "아이디를 찾지 못했어요") }
                }
        }
    }

    fun resetPassword() {
        val s = _state.value
        when {
            s.resetLoginId.isBlank() -> _state.update { it.copy(error = "아이디를 입력해 주세요") }
            s.verifyCode.isBlank() -> _state.update { it.copy(error = "인증 코드를 입력해 주세요") }
            s.newPassword.length < 8 -> _state.update { it.copy(error = "비밀번호는 8자 이상이어야 해요") }
            else -> viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null, info = null) }
                runCatching {
                    authRepository.resetPassword(s.resetLoginId.trim(), s.verifyCode.trim(), s.newPassword)
                }.onSuccess {
                    _state.update { it.copy(loading = false, info = "비밀번호가 재설정되었어요. 로그인해 주세요") }
                }.onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "재설정에 실패했어요") }
                }
            }
        }
    }
}
