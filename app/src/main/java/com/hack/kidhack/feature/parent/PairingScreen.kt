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

    fun confirm() {
        viewModelScope.launch {
            val child = runCatching { childRepository.children().firstOrNull() }.getOrNull()
            _state.update { it.copy(step = 2, child = child) }
            timer?.cancel()
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
                            Text(state.child?.name ?: "김민준", fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                            Text(stringResource(R.string.parent_pairing_connected), color = colors.safe, fontWeight = FontBold, fontSize = 12.sp)
                        }
                        Text(state.child?.deviceModel ?: "Galaxy A54", color = colors.muted, fontSize = 12.sp)
                    }
                }
                KidButton(stringResource(R.string.parent_pairing_to_dashboard), onDone, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
