package com.hack.kidhack.feature.efin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.EfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 계좌비밀번호(전자금융 별도 비밀번호) 설정·변경 화면 — #59.
 *
 * 로그인 비밀번호와 분리된 6자리 거래용 비밀번호. 변경 시 기존 비밀번호로 본인확인하고, 과거에 쓴
 * 비밀번호로는 바꿀 수 없다(서버가 이력 대조). 결함은 서버측이다([VULN-EFIN-5/6]).
 */
enum class AccountPwMode { SET, CHANGE }

data class AccountPwUiState(
    val mode: AccountPwMode = AccountPwMode.SET,
    val password: String = "",
    val current: String = "",
    val newPassword: String = "",
    /** 본인확인용 앱 로그인 비밀번호. */
    val loginPassword: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

class AccountPasswordViewModel(
    private val efinRepository: EfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountPwUiState())
    val state: StateFlow<AccountPwUiState> = _state.asStateFlow()

    fun setMode(mode: AccountPwMode) = _state.update {
        it.copy(mode = mode, error = null, done = null)
    }

    fun onPassword(v: String) = _state.update { it.copy(password = digits(v)) }
    fun onCurrent(v: String) = _state.update { it.copy(current = digits(v)) }
    fun onNew(v: String) = _state.update { it.copy(newPassword = digits(v)) }
    fun onLoginPassword(v: String) = _state.update { it.copy(loginPassword = v) }

    fun submit() {
        val s = _state.value
        if (s.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null, done = null) }
            runCatching {
                when (s.mode) {
                    AccountPwMode.SET -> efinRepository.setAccountPassword(s.password, s.loginPassword)
                    AccountPwMode.CHANGE -> efinRepository.changeAccountPassword(s.current, s.newPassword, s.loginPassword)
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        working = false, password = "", current = "", newPassword = "", loginPassword = "",
                        done = if (s.mode == AccountPwMode.SET) "계좌비밀번호를 설정했습니다." else "계좌비밀번호를 변경했습니다.",
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(working = false, error = t.userMessage()) }
            }
        }
    }

    private fun digits(v: String) = v.filter { it.isDigit() }.take(6)
}

@Composable
fun AccountPasswordScreen(
    onBack: () -> Unit = {},
    viewModel: AccountPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        EfinHeader(title = "계좌비밀번호", onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 설정 / 변경 전환.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip("설정", state.mode == AccountPwMode.SET, Modifier.weight(1f)) {
                    viewModel.setMode(AccountPwMode.SET)
                }
                ModeChip("변경", state.mode == AccountPwMode.CHANGE, Modifier.weight(1f)) {
                    viewModel.setMode(AccountPwMode.CHANGE)
                }
            }

            when (state.mode) {
                AccountPwMode.SET -> {
                    FieldLabel("6자리 계좌비밀번호")
                    PinField(state.password, viewModel::onPassword, "숫자 6자리")
                }
                AccountPwMode.CHANGE -> {
                    FieldLabel("기존 계좌비밀번호")
                    PinField(state.current, viewModel::onCurrent, "기존 6자리")
                    Spacer(Modifier.height(2.dp))
                    FieldLabel("새 계좌비밀번호")
                    PinField(state.newPassword, viewModel::onNew, "새 6자리")
                }
            }

            Spacer(Modifier.height(2.dp))
            FieldLabel("본인확인 · 앱 로그인 비밀번호")
            LoginPwField(state.loginPassword, viewModel::onLoginPassword)

            EfinPrimaryButton(
                text = if (state.mode == AccountPwMode.SET) "설정" else "변경",
                enabled = !state.working && isReady(state),
                onClick = viewModel::submit,
            )

            EfinBanner(error = state.error, done = state.done)
        }
    }
}

private fun isReady(s: AccountPwUiState): Boolean = s.loginPassword.isNotBlank() && when (s.mode) {
    AccountPwMode.SET -> s.password.length == 6
    AccountPwMode.CHANGE -> s.current.length == 6 && s.newPassword.length == 6
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .border(1.dp, if (selected) colors.brand else colors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.brandDark else colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
        )
    }
}

/** 앱 로그인 비밀번호 입력(마스킹, 길이 제한 없음). */
@Composable
private fun LoginPwField(value: String, onChange: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text("로그인 비밀번호", color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = colors.text, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}

/** 비밀번호(마스킹) 6자리 입력 필드. */
@Composable
internal fun PinField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val colors = LocalKidhackColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = colors.text, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}