package com.hack.kidhack.feature.parent

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.StepProgressBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.PairingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PairingUiState(
    val step: Int = 0,
    val code: String = "",
    val remaining: Int = 0,
    val child: ChildProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 「다음」을 눌렀는데 아직 연결된 자녀가 없는 상태. 조회 실패(error)와 구분한다. */
    val notConnected: Boolean = false,
)

class PairingViewModel(
    private val pairingRepository: PairingRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()
    private var timer: Job? = null

    fun nextFromInstall() {
        issue(thenStep = 1)
    }

    fun issue(thenStep: Int = _state.value.step) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { pairingRepository.issueCode() }
                .onSuccess { issued ->
                    _state.update {
                        it.copy(loading = false, code = issued.pairingCode, remaining = issued.expiresIn, step = thenStep)
                    }
                    startTimer()
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message) }
                }
        }
    }

    /**
     * 「다음」 — 자녀가 실제로 연결됐는지 `GET /children` 으로 확인한 뒤에만 완료 화면으로 넘어간다.
     *
     * 예전에는 결과를 보지 않고 무조건 완료로 넘어갔다. 그래서 자녀가 코드를 입력하지 않아도
     * 「연결 완료!」가 떴고, 화면의 하드코딩 더미값(김민준 / Galaxy A54)이 그걸 가려 줬다.
     * 조회 실패와 「아직 연결 안 됨」은 사용자에게 다른 문제라 메시지를 구분한다.
     */
    fun confirm() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { childRepository.children() }
                .onSuccess { children ->
                    val child = children.firstOrNull()
                    if (child == null) {
                        _state.update { it.copy(loading = false, notConnected = true) }
                    } else {
                        _state.update {
                            it.copy(loading = false, notConnected = false, step = 2, child = child)
                        }
                        timer?.cancel()
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("연결 상태를 확인하지 못했어요")) }
                }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (_state.value.remaining > 0) {
                delay(1_000)
                _state.update { it.copy(remaining = (it.remaining - 1).coerceAtLeast(0)) }
            }
        }
    }
}

@Composable
fun ParentPairingScreen(
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: PairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_pairing_title), onBack)
            Spacer(Modifier.height(12.dp))
            StepProgressBar(totalSteps = 3, currentStep = state.step)
        },
    ) {
        ParentLoadingOrError(state.loading, state.error)
        if (state.notConnected) {
            Text(
                stringResource(R.string.parent_pairing_not_connected),
                color = colors.danger,
                fontSize = 13.sp,
            )
        }
        when (state.step) {
            0 -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📱", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.parent_pairing_install_title), fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 22.sp, color = colors.text)
                    Text(stringResource(R.string.parent_pairing_install_highlight), fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 22.sp, color = colors.brand)
                    Text(stringResource(R.string.parent_pairing_install_desc), color = colors.muted, fontSize = 13.sp)
                }
                KidCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.brandLight),
                            contentAlignment = Alignment.Center,
                        ) { Text("🔗", fontSize = 20.sp) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.parent_pairing_share_title), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                            Text(stringResource(R.string.parent_pairing_share_desc), color = colors.muted, fontSize = 12.sp)
                        }
                        KidButton(
                            stringResource(R.string.parent_pairing_share),
                            {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.parent_pairing_share_text))
                                }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.parent_pairing_share)))
                            },
                            variant = KidButtonVariant.Secondary,
                        )
                    }
                }
                KidButton(stringResource(R.string.parent_pairing_installed), viewModel::nextFromInstall, Modifier.fillMaxWidth())
            }
            1 -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔢", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.parent_pairing_code_title), fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 22.sp, color = colors.text)
                    Text(stringResource(R.string.parent_pairing_code_desc), color = colors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.code.take(6).padEnd(6, ' ').forEach { ch ->
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(2.dp, colors.brand, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(ch.toString().trim(), fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 22.sp, color = colors.brand)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.remaining > 0) stringResource(R.string.parent_pairing_expires, formatMmSs(state.remaining))
                        else stringResource(R.string.parent_pairing_expired),
                        color = if (state.remaining > 0) colors.muted else colors.danger,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                    )
                }
                KidButton(stringResource(R.string.parent_pairing_next), viewModel::confirm, Modifier.fillMaxWidth(), enabled = state.code.isNotBlank())
                KidButton(stringResource(R.string.parent_pairing_refresh), { viewModel.issue(1) }, Modifier.fillMaxWidth(), KidButtonVariant.Ghost)
            }
            else -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.parent_pairing_done_title), fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 22.sp, color = colors.text)
                    Text(stringResource(R.string.parent_pairing_done_desc), color = colors.muted, fontSize = 13.sp)
                }
                KidCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFBBF7D0)),
                            contentAlignment = Alignment.Center,
                        ) { Text("👦", fontSize = 22.sp) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            // 더미값으로 메우지 않는다. 이 화면은 child 가 있을 때만 열린다.
                            Text(state.child?.name.orEmpty(), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                            Text(stringResource(R.string.parent_pairing_connected), color = colors.safe, fontWeight = FontBold, fontSize = 12.sp)
                        }
                        // 서버 ChildSummaryResponse 에는 기기 모델이 없다(id·username·nickname·
                        // profileImageUrl·realtimeEnabled 뿐). 명세에 있는 닉네임을 대신 보여 준다.
                        val nickname = state.child?.alias?.takeIf { it.isNotBlank() && it != state.child?.name }
                        if (nickname != null) {
                            Text(nickname, color = colors.muted, fontSize = 12.sp)
                        }
                    }
                }
                KidButton(stringResource(R.string.parent_pairing_to_dashboard), onDone, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
