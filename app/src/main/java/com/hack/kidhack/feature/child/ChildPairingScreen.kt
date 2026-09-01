package com.hack.kidhack.feature.child

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.domain.repository.PairingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildPairingUiState(
    val code: String = "",
    val loading: Boolean = false,
    val linked: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = code.length == CODE_LENGTH && !loading

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * 자녀가 부모의 6자리 페어링 코드를 입력해 계정을 연결한다.
 *
 * 예전에는 부모 앱만 코드를 **발급**하고 자녀 앱에는 그 코드를 **입력할 화면이 없었다**.
 * 그래서 회원가입 때 부모 회원번호를 넣지 않은 자녀는 사후에 연결할 방법이 아예 없었고,
 * 연결이 안 되니 부모의 캡처·촬영 요청이 자녀 기기로 갈 대상 자체가 없었다.
 */
class ChildPairingViewModel(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildPairingUiState())
    val state: StateFlow<ChildPairingUiState> = _state.asStateFlow()

    fun onCode(value: String) = _state.update {
        it.copy(code = value.filter { ch -> ch.isDigit() }.take(ChildPairingUiState.CODE_LENGTH), error = null)
    }

    fun connect() {
        val code = _state.value.code
        if (code.length != ChildPairingUiState.CODE_LENGTH) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { pairingRepository.connect(code) }
                .onSuccess { _state.update { it.copy(loading = false, linked = true) } }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("연결에 실패했어요. 코드를 다시 확인해 주세요.")) }
                }
        }
    }

    fun reset() = _state.update { ChildPairingUiState() }
}

@Composable
fun ChildPairingScreen(
    onBack: () -> Unit = {},
    viewModel: ChildPairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_screenshot_back),
                modifier = Modifier.clickable(onClick = onBack),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_pairing_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
        },
    ) {
        if (state.linked) {
            KidCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.child_pairing_done_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 18.sp,
                        color = colors.text,
                    )
                    Text(
                        stringResource(R.string.child_pairing_done_desc),
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    KidButton(stringResource(R.string.child_pairing_done_back), onBack, Modifier.fillMaxWidth())
                }
            }
            return@ChildScreenScaffold
        }

        // 화면 구성을 가입 흐름의 「부모님과 연결」 단계와 맞춘다. 같은 일을 하는 두 화면이
        // 서로 다르게 생기면 코드를 어디서 받아 어디에 넣는지 헷갈린다.
        Text(
            stringResource(R.string.child_pairing_desc),
            color = colors.muted,
            fontSize = 14.sp,
        )
        KidCard {
            Text(
                stringResource(R.string.child_pairing_guide_title),
                color = colors.brandDark,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
            Text(
                stringResource(R.string.child_pairing_guide_desc),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        KidTextField(
            value = state.code,
            onValueChange = viewModel::onCode,
            label = stringResource(R.string.child_pairing_code_label),
            placeholder = stringResource(R.string.child_pairing_code_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (state.error != null) {
            Text(state.error!!, color = colors.danger, fontSize = 12.sp, fontWeight = FontBold)
        }
        KidButton(
            text = stringResource(if (state.loading) R.string.child_pairing_connecting else R.string.child_pairing_connect),
            onClick = viewModel::connect,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
        )
        Text(
            stringResource(R.string.child_pairing_hint),
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
