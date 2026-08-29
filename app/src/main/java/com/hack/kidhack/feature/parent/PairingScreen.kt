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

    /**
     * 코드를 발급한 시점에 이미 연결돼 있던 자녀 id.
     *
     * 완료 화면에 **이번에 연결된** 자녀를 보여주기 위해 필요하다. 목록의 첫 항목을 쓰면
     * 둘째를 연결해도 첫째가 뜬다.
     */
    private var knownChildIds: Set<String> = emptySet()

    fun nextFromInstall() {
        issue(thenStep = 1)
    }

    fun issue(thenStep: Int = _state.value.step) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notConnected = false) }
            runCatching { pairingRepository.issueCode() }
                .onSuccess { issued ->
                    // 발급 직전의 자녀를 기억해 둔다. 실패해도 발급 자체는 막지 않는다.
                    knownChildIds = runCatching { childRepository.children() }
                        .getOrDefault(emptyList())
                        .map { it.id }
                        .toSet()
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
     * 「다음」 — 발급한 코드가 **실제로 사용됐는지** 확인한 뒤에만 완료 화면으로 넘어간다.
     *
     * 서버는 연결이 성공하면 그 코드를 지우고(`PairingService.connect`), `GET /pairing/code` 는
     * 아직 살아 있는 코드만 준다. 그래서 발급한 코드가 목록에 남아 있으면 아무도 쓰지 않은 것이다.
     *
     * 예전에는 「자녀가 하나라도 있는가」만 봤다. 그래서 첫째가 이미 연결된 부모는 둘째용 코드를
     * 아무도 입력하지 않아도 완료 화면으로 넘어갔고, 거기에 **첫째**가 표시됐다.
     */
    fun confirm() {
        val issuedCode = _state.value.code
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notConnected = false) }
            runCatching {
                val stillActive = pairingRepository.activeCodes().any { it.pairingCode == issuedCode }
                if (stillActive) null else childRepository.children()
            }
                .onSuccess { children ->
                    if (children == null) {
                        _state.update { it.copy(loading = false, notConnected = true) }
                        return@onSuccess
                    }
                    // 이번에 새로 붙은 자녀. 이미 연결돼 있던 자녀가 같은 부모의 새 코드를 다시
                    // 입력하면 목록이 늘지 않아 특정할 수 없다. 그때는 엉뚱한 자녀를 보여주느니
                    // 카드를 생략한다(연결 자체는 성공이므로 완료로 넘어간다).
                    val linked = children.firstOrNull { it.id !in knownChildIds }
                    _state.update { it.copy(loading = false, step = 2, child = linked) }
                    timer?.cancel()
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
                // 이번에 연결된 자녀를 특정하지 못한 경우(기존 자녀의 재연결)에는 카드를 생략한다.
                // 더미값으로 메우거나 목록의 첫 자녀를 보여주면 「누가 연결됐는지」를 속이게 된다.
                val linked = state.child
                if (linked != null) {
                    KidCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFBBF7D0)),
                                contentAlignment = Alignment.Center,
                            ) { Text("👦", fontSize = 22.sp) }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(linked.name, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                                Text(stringResource(R.string.parent_pairing_connected), color = colors.safe, fontWeight = FontBold, fontSize = 12.sp)
                            }
                            // 서버 ChildSummaryResponse 에는 기기 모델이 없다(id·username·nickname·
                            // profileImageUrl·realtimeEnabled 뿐). 명세에 있는 닉네임을 대신 보여 준다.
                            val nickname = linked.alias?.takeIf { it.isNotBlank() && it != linked.name }
                            if (nickname != null) {
                                Text(nickname, color = colors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                KidButton(stringResource(R.string.parent_pairing_to_dashboard), onDone, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
