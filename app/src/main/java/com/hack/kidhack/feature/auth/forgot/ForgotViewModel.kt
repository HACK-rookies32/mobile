package com.hack.kidhack.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.core.common.digitsOnlyPhone
import com.hack.kidhack.core.common.formatPhoneNumber
import com.hack.kidhack.data.remote.userMessage
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
    val newPasswordConfirm: String = "",
    val findLoading: Boolean = false,
    val resetLoading: Boolean = false,
    val findError: String? = null,
    val findInfo: String? = null,
    val resetError: String? = null,
    val resetInfo: String? = null,
)

class ForgotViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun onFindName(v: String) = _state.update { it.copy(findName = v, findError = null) }
    fun onFindPhone(v: String) = _state.update {
        it.copy(findPhone = digitsOnlyPhone(v), findError = null)
    }
    fun onResetLoginId(v: String) = _state.update { it.copy(resetLoginId = v, resetError = null) }
    fun onVerifyCode(v: String) = _state.update { it.copy(verifyCode = v, resetError = null) }
    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, resetError = null) }
    fun onNewPasswordConfirm(v: String) = _state.update { it.copy(newPasswordConfirm = v, resetError = null) }

    fun findId() {
        val s = _state.value
        if (s.findName.isBlank() || s.findPhone.isBlank()) {
            _state.update { it.copy(findError = "이름과 휴대폰 번호를 입력해 주세요", findInfo = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(findLoading = true, findError = null, findInfo = null) }
            runCatching { authRepository.findId(s.findName.trim(), formatPhoneNumber(s.findPhone)) }
                .onSuccess { id ->
                    _state.update {
                        it.copy(
                            findLoading = false,
                            foundLoginId = id,
                            resetLoginId = id,
                            findInfo = "아이디는 $id 입니다",
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            findLoading = false,
                            findError = t.userMessage("아이디를 찾지 못했어요"),
                        )
                    }
                }
        }
    }

    fun resetPassword() {
        val s = _state.value
        when {
            s.resetLoginId.isBlank() ->
                _state.update { it.copy(resetError = "아이디를 입력해 주세요", resetInfo = null) }
            s.verifyCode.isBlank() ->
                _state.update { it.copy(resetError = "인증 코드를 입력해 주세요", resetInfo = null) }
            s.newPassword.length < 8 ->
                _state.update { it.copy(resetError = "비밀번호는 8자 이상이어야 해요", resetInfo = null) }
            s.newPassword != s.newPasswordConfirm ->
                _state.update { it.copy(resetError = "비밀번호가 일치하지 않아요", resetInfo = null) }
            else -> viewModelScope.launch {
                _state.update { it.copy(resetLoading = true, resetError = null, resetInfo = null) }
                runCatching {
                    authRepository.resetPassword(s.resetLoginId.trim(), s.verifyCode.trim(), s.newPassword)
                }.onSuccess {
                    _state.update {
                        it.copy(
                            resetLoading = false,
                            resetInfo = "비밀번호가 재설정되었어요. 로그인해 주세요",
                        )
                    }
                }.onFailure { t ->
                    _state.update {
                        it.copy(
                            resetLoading = false,
                            resetError = t.userMessage("재설정에 실패했어요"),
                        )
                    }
                }
            }
        }
    }
}
