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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class PayTab { CHARGE, SEND, RECLAIM, HISTORY }
enum class TxFilter { ALL, OUT, IN, PAY }

data class PayUiState(
    val wallet: Wallet? = null,
    val childWallet: Wallet? = null,
    val cards: List<Card> = emptyList(),
    val transactions: List<WalletTransaction> = emptyList(),
    val tab: PayTab = PayTab.SEND,
    val txFilter: TxFilter = TxFilter.ALL,
    val selectedCardId: String? = null,
    val amountInput: String = "",
    val memoInput: String = "",
    val childId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)

class PayViewModel(
    private val walletRepository: WalletRepository,
    private val cardRepository: CardRepository,
    private val selectedChildRepository: SelectedChildRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    init { load() }

    fun onTab(tab: PayTab) = _state.update { it.copy(tab = tab, error = null, successMessage = null, amountInput = "", memoInput = "") }
    fun onAmount(v: String) = _state.update { it.copy(amountInput = v.filter { it.isDigit() }) }
    fun onMemo(v: String) = _state.update { it.copy(memoInput = v) }
    fun onFilter(f: TxFilter) {
        _state.update { it.copy(txFilter = f) }
        loadTransactions()
    }
    fun onSelectCard(id: String) = _state.update { it.copy(selectedCardId = id) }

    /** 카드 관리 화면에서 돌아왔을 때 목록만 갱신한다. 고른 카드가 남아 있으면 선택을 유지한다. */
    fun refreshCards() {
        viewModelScope.launch {
            runCatching { cardRepository.list() }
                .onSuccess { list ->
                    _state.update { s ->
                        val kept = s.selectedCardId?.takeIf { id -> list.any { it.cardId == id } }
                        s.copy(cards = list, selectedCardId = kept ?: list.firstOrNull()?.cardId)
                    }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val childId = selectedChildRepository.current()
                val wallet = walletRepository.getMyWallet()
                val cards = cardRepository.list()
                val childWallet = childId?.let { walletRepository.getChildWallet(it) }
                Triple(wallet, cards, childWallet to childId)
            }.onSuccess { (w, c, cw) ->
                _state.update { it.copy(
                    loading = false, 
                    wallet = w, 
                    cards = c, 
                    childWallet = cw.first, 
                    childId = cw.second,
                    selectedCardId = c.firstOrNull()?.cardId
                ) }
                loadTransactions()
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val type = when (_state.value.txFilter) {
                TxFilter.ALL -> null
                TxFilter.OUT -> "TRANSFER_OUT"
                TxFilter.IN -> "TRANSFER_IN"
                TxFilter.PAY -> "PAYMENT"
            }
            runCatching { walletRepository.getTransactions(type, 0) }
                .onSuccess { list -> _state.update { it.copy(transactions = list) } }
        }
    }

    fun charge() {
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        val cardId = _state.value.selectedCardId ?: return
        if (amount <= 0) return
        
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val prep = walletRepository.prepareCharge(amount, "CARD", cardId)
                walletRepository.confirmCharge(prep.chargeId, prep.paymentKey ?: "", amount)
            }.onSuccess {
                _state.update { it.copy(successMessage = "충전이 완료되었습니다.", amountInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    fun send() {
        val childId = _state.value.childId ?: return
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                walletRepository.transferToChild(childId, amount, _state.value.memoInput.ifBlank { null })
            }.onSuccess {
                _state.update { it.copy(successMessage = "송금이 완료되었습니다.", amountInput = "", memoInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    fun reclaim() {
        val childId = _state.value.childId ?: return
        val amount = _state.value.amountInput.toIntOrNull() ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                walletRepository.reclaimFromChild(childId, amount)
            }.onSuccess {
                _state.update { it.copy(successMessage = "회수가 완료되었습니다.", amountInput = "") }
                load()
            }.onFailure { handlePayError(it) }
        }
    }

    private fun handlePayError(t: Throwable) {
        val msg = when {
            t.message?.contains("402") == true -> "잔액이 부족합니다."
            t.message?.contains("403") == true -> "요청 권한이 없습니다."
            t.message?.contains("400") == true -> "잘못된 입력입니다."
            else -> t.message ?: "오류가 발생했습니다."
        }
        _state.update { it.copy(loading = false, error = msg) }
    }
}

@Composable
fun ParentPayScreen(
    onBack: () -> Unit = {},
    onRegisterCard: () -> Unit = {},
    viewModel: PayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCards()
        onPauseOrDispose { }
    }

    ParentScreenScaffold(
        header = {
            ParentHeaderBar("루키즈 페이", onBack)
            Spacer(Modifier.height(12.dp))
            KidCard(containerColor = Color.White.copy(alpha = 0.15f)) {
                Column(Modifier.padding(4.dp)) {
                    Text("내 지갑 잔액", color = Color.White.copy(alpha = 0.7f), fontWeight = FontBold, fontSize = 12.sp)
                    Text(
                        "₩ ${formatWon(state.wallet?.balance ?: 0)}",
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 28.sp,
                    )
                    
                    if (state.childId != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("자녀 지갑 잔액", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("₩ ${formatWon(state.childWallet?.balance ?: 0)}", color = Color.White, fontWeight = FontBold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PayTabBtn("보내기", state.tab == PayTab.SEND, Modifier.weight(1f)) { viewModel.onTab(PayTab.SEND) }
                PayTabBtn("회수하기", state.tab == PayTab.RECLAIM, Modifier.weight(1f)) { viewModel.onTab(PayTab.RECLAIM) }
                PayTabBtn("충전", state.tab == PayTab.CHARGE, Modifier.weight(1f)) { viewModel.onTab(PayTab.CHARGE) }
                PayTabBtn("내역", state.tab == PayTab.HISTORY, Modifier.weight(1f)) { viewModel.onTab(PayTab.HISTORY) }
            }
        },
    ) {
        if (state.childId == null && state.tab != PayTab.CHARGE && state.tab != PayTab.HISTORY) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("선택된 자녀가 없습니다.", color = colors.muted)
            }
        } else {
            ParentLoadingOrError(state.loading && state.wallet == null, state.error, viewModel::load)
            
            if (state.successMessage != null) {
                KidCard(containerColor = colors.safe.copy(alpha = 0.1f)) {
                    Text(state.successMessage!!, color = colors.safe, fontWeight = FontBold, fontSize = 14.sp)
                }
            }

            when (state.tab) {
                PayTab.SEND -> {
                    KidCard {
                        KidTextField(state.amountInput, viewModel::onAmount, "보낼 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.height(8.dp))
                        KidTextField(state.memoInput, viewModel::onMemo, "메모 (선택)", placeholder = "맛있는 거 사먹어!")
                        Spacer(Modifier.height(16.dp))
                        KidButton("보내기", viewModel::send, Modifier.fillMaxWidth(), enabled = state.amountInput.isNotEmpty())
                    }
                }
                PayTab.RECLAIM -> {
                    KidCard {
                        val childBal = state.childWallet?.balance ?: 0
                        Text("자녀 잔액: ₩ ${formatWon(childBal)}", color = colors.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        KidTextField(state.amountInput, viewModel::onAmount, "회수할 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.height(16.dp))
                        val amt = state.amountInput.toIntOrNull() ?: 0
                        KidButton("회수하기", viewModel::reclaim, Modifier.fillMaxWidth(), enabled = amt > 0 && amt <= childBal)
                    }
                }
                PayTab.CHARGE -> {
                    if (state.cards.isEmpty()) {
                        KidCard {
                            Text("등록된 카드가 없습니다.", color = colors.muted, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            KidButton("카드 등록하러 가기", onRegisterCard, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("결제 수단", fontWeight = FontBold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.cards) { card ->
                                val sel = state.selectedCardId == card.cardId
                                Box(
                                    Modifier
                                        .size(width = 140.dp, height = 80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) colors.brand else colors.surface)
                                        .border(2.dp, if (sel) colors.brandDark else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable { viewModel.onSelectCard(card.cardId) }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(card.ownerName, color = if (sel) Color.White else colors.text, fontSize = 11.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text("**** ${card.last4}", color = if (sel) Color.White else colors.text, fontWeight = FontBold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        KidCard {
                            KidTextField(state.amountInput, viewModel::onAmount, "충전 금액", placeholder = "0", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Spacer(Modifier.height(16.dp))
                            KidButton(
                                text = "충전하기",
                                onClick = viewModel::charge,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.amountInput.isNotEmpty() && state.selectedCardId != null
                            )
                        }
                    }
                }
                PayTab.HISTORY -> {
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip("전체", state.txFilter == TxFilter.ALL) { viewModel.onFilter(TxFilter.ALL) }
                        FilterChip("보낸내역", state.txFilter == TxFilter.OUT) { viewModel.onFilter(TxFilter.OUT) }
                        FilterChip("받은내역", state.txFilter == TxFilter.IN) { viewModel.onFilter(TxFilter.IN) }
                        FilterChip("사용내역", state.txFilter == TxFilter.PAY) { viewModel.onFilter(TxFilter.PAY) }
                    }
                    KidCard {
                        if (state.transactions.isEmpty()) {
                            Text("거래 내역이 없습니다.", color = colors.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                        }
                        state.transactions.forEachIndexed { i, tx ->
                            TransactionRow(tx)
                            if (i < state.transactions.lastIndex) HorizontalDivider(color = colors.border.copy(0.5f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction) {
    val colors = LocalKidhackColors.current
    val isOut = tx.type.contains("OUT") || tx.type == "PAYMENT"
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (isOut) colors.brandLight else Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isOut) "↑" else "↓", color = if (isOut) colors.brand else Color(0xFF166534), fontWeight = FontBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val label = when(tx.type) {
                "CHARGE" -> "충전"
                "TRANSFER_OUT" -> "보내기"
                "TRANSFER_IN" -> "받기"
                "PAYMENT" -> "결제"
                "RECLAIM" -> "회수"
                else -> tx.type
            }
            Text(label, fontWeight = FontBold, fontSize = 14.sp)
            Text(tx.createdAt, color = colors.muted, fontSize = 11.sp)
        }
        Text(
            "${if (isOut) "-" else "+"}₩ ${formatWon(Math.abs(tx.amount))}",
            fontWeight = FontBlack,
            fontSize = 14.sp,
            color = if (isOut) colors.text else colors.safe
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) colors.brand else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) Color.White else colors.text,
        fontWeight = FontBold,
        fontSize = 11.sp
    )
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
        fontSize = 11.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
