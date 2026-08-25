package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class PayTab { CHARGE, TRANSFER, HISTORY }

data class PayUiState(
    val balance: Int = 0,
    val history: List<Payment> = emptyList(),
    val tab: PayTab = PayTab.HISTORY,
    val chargeAmount: Int = 10_000,
    val message: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class PayViewModel(
    private val pointRepository: PointRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    init { load() }

    fun onTab(tab: PayTab) = _state.update { it.copy(tab = tab, message = null) }
    fun onAmount(amount: Int) = _state.update { it.copy(chargeAmount = amount) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val balance = pointRepository.balance().balance
                val history = paymentRepository.history()
                balance to history
            }.onSuccess { (balance, history) ->
                _state.update { it.copy(loading = false, balance = balance, history = history) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun charge() {
        viewModelScope.launch {
            val amount = _state.value.chargeAmount
            runCatching { paymentRepository.checkout(amount, "TOSS", 1) }
                .onSuccess { pay ->
                    _state.update {
                        it.copy(
                            history = listOf(pay) + it.history,
                            balance = it.balance + amount,
                            message = "charged",
                            tab = PayTab.HISTORY,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

@Composable
fun ParentPayScreen(
    onBack: () -> Unit = {},
    viewModel: PayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_pay_title), onBack)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.parent_pay_balance_label), color = Color.White.copy(alpha = 0.7f), fontWeight = FontBold, fontSize = 12.sp)
                Text(
                    stringResource(R.string.parent_pay_balance, formatWon(state.balance)),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 32.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayTabBtn(stringResource(R.string.parent_pay_charge), state.tab == PayTab.CHARGE, Modifier.weight(1f)) { viewModel.onTab(PayTab.CHARGE) }
                    PayTabBtn(stringResource(R.string.parent_pay_transfer), state.tab == PayTab.TRANSFER, Modifier.weight(1f)) { viewModel.onTab(PayTab.TRANSFER) }
                    PayTabBtn(stringResource(R.string.parent_pay_history), state.tab == PayTab.HISTORY, Modifier.weight(1f)) { viewModel.onTab(PayTab.HISTORY) }
                }
            }
        },
    ) {
        ParentLoadingOrError(state.loading, state.error, viewModel::load)
        when (state.tab) {
            PayTab.CHARGE -> {
                KidCard {
                    Text(stringResource(R.string.parent_pay_charge_amount), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10_000, 30_000, 50_000).forEach { amt ->
                            val sel = state.chargeAmount == amt
                            Text(
                                "₩ ${formatWon(amt)}",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) colors.brandLight else colors.surface)
                                    .clickable { viewModel.onAmount(amt) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (sel) colors.brand else colors.text,
                                fontWeight = FontBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    KidButton(stringResource(R.string.parent_pay_charge_submit), viewModel::charge, Modifier.fillMaxWidth())
                }
            }
            PayTab.TRANSFER -> {
                KidCard {
                    Text(stringResource(R.string.parent_pay_transfer_disabled), color = colors.muted, fontSize = 14.sp)
                }
            }
            PayTab.HISTORY -> {
                KidCard {
                    Text(stringResource(R.string.parent_pay_recent), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    state.history.forEachIndexed { i, t ->
                        val incoming = t.amount >= 0 || t.type == "in"
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (incoming) Color(0xFFDCFCE7) else colors.brandLight),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (incoming) "↓" else "↑", fontSize = 14.sp) }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.description, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                                Text(t.dateLabel, color = colors.muted, fontSize = 12.sp)
                            }
                            Text(
                                if (incoming) stringResource(R.string.parent_pay_amount_in, formatWon(t.amount))
                                else stringResource(R.string.parent_pay_amount_out, formatWon(t.amount)),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBlack,
                                fontSize = 14.sp,
                                color = if (incoming) colors.safe else colors.danger,
                            )
                        }
                        if (i < state.history.lastIndex) HorizontalDivider(color = colors.border)
                    }
                }
            }
        }
        if (state.message == "charged") {
            Text(stringResource(R.string.parent_pay_charged), color = colors.safe, fontWeight = FontBold)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PayTabBtn(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        color = Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
