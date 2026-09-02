package com.hack.kidhack.feature.parent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class CardUiState(
    val items: List<Card> = emptyList(),
    val loading: Boolean = true,
    val adding: Boolean = false,
    val error: String? = null,
    
    // Form fields
    val cardNumber: String = "",
    val expiry: String = "",
    val cvc: String = "",
    val cardPassword2: String = "",
    val birthDate: String = "",
    val ownerName: String = ""
)

class CardViewModel(
    private val cardRepository: CardRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { cardRepository.list() }
                .onSuccess { list -> _state.update { it.copy(items = list, loading = false) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun toggleAdding(show: Boolean) = _state.update { it.copy(adding = show) }

    fun onCardNumber(v: String) = _state.update { it.copy(cardNumber = v.filter { it.isDigit() }.take(16)) }
    fun onExpiry(v: String) = _state.update { it.copy(expiry = v.filter { it.isDigit() }.take(4)) }
    fun onCvc(v: String) = _state.update { it.copy(cvc = v.filter { it.isDigit() }.take(3)) }
    fun onCardPassword2(v: String) = _state.update { it.copy(cardPassword2 = v.filter { it.isDigit() }.take(2)) }
    fun onBirthDate(v: String) = _state.update { it.copy(birthDate = v.filter { it.isDigit() }.take(6)) }
    fun onOwnerName(v: String) = _state.update { it.copy(ownerName = v) }

    fun register() {
        val s = _state.value
        if (s.cardNumber.length != 16 || s.expiry.length < 4 || s.cvc.length < 3 || 
            s.cardPassword2.length < 2 || s.birthDate.length < 6 || s.ownerName.isBlank()) {
            _state.update { it.copy(error = "모든 정보를 올바르게 입력해 주세요.") }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val formattedExpiry = "${s.expiry.take(2)}/${s.expiry.drop(2)}"
                cardRepository.register(
                    cardNumber = s.cardNumber,
                    expiry = formattedExpiry,
                    cvc = s.cvc,
                    cardPassword2 = s.cardPassword2,
                    birthDate = s.birthDate,
                    ownerName = s.ownerName
                )
            }.onSuccess {
                // Success: Reset sensitive state immediately
                _state.update { it.copy(
                    loading = false,
                    adding = false,
                    cardNumber = "",
                    expiry = "",
                    cvc = "",
                    cardPassword2 = "",
                    birthDate = "",
                    ownerName = "",
                    error = null
                ) }
                load()
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun delete(cardId: String) {
        viewModelScope.launch {
            runCatching { cardRepository.delete(cardId) }
                .onSuccess { load() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * 카드 관리.
 *
 * <p>카드 그림을 브랜드색 그라데이션으로 칠하고 있었다. 그러면 「이 앱이 발급한 카드」처럼
 * 보이는데, 실제로는 사용자가 등록한 남의 카드다. 먹색 한 장으로 둔다.
 *
 * <p>삭제가 <b>길게 누르기</b>였다. 알아낼 방법이 없어 카드 오른쪽 위 「더 보기」로 꺼냈다.
 */
@Composable
fun ParentCardsScreen(
    onBack: () -> Unit = {},
    onOpenWallet: () -> Unit = {},
    viewModel: CardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var deleteCardId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_cards_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 16.sp,
                color = colors.text,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.parent_cards_lead),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 18.dp),
                color = colors.muted,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )

            if (state.loading && state.items.isEmpty()) {
                Box(Modifier.padding(horizontal = 22.dp)) {
                    ParentLoadingOrError(true, state.error, viewModel::load)
                }
            } else if (state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp)) {
                    ParentLoadingOrError(false, state.error, viewModel::load)
                }
            }

            state.items.forEach { card ->
                CreditCard(card = card, onMore = { deleteCardId = card.cardId })
                Spacer(Modifier.height(12.dp))
            }

            if (state.adding) {
                AddCardForm(state = state, viewModel = viewModel)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .clickable { viewModel.toggleAdding(true) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.muted,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.parent_cards_add),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.5.sp,
                    )
                }

                Text(
                    stringResource(R.string.parent_cards_group),
                    modifier = Modifier.padding(start = 22.dp, top = 30.dp, bottom = 4.dp),
                    color = colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenWallet)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.parent_cards_go_wallet),
                        modifier = Modifier.weight(1f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.5.sp,
                        letterSpacing = (-0.25).sp,
                        color = colors.text,
                    )
                    Text("\u203a", color = colors.border, fontSize = 17.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    deleteCardId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCardId = null },
            containerColor = colors.card,
            title = {
                Text(
                    stringResource(R.string.parent_cards_delete_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 17.sp,
                    color = colors.text,
                )
            },
            text = {
                Text(
                    stringResource(R.string.parent_cards_delete_message),
                    color = colors.muted,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton({ viewModel.delete(id); deleteCardId = null }) {
                    Text(
                        stringResource(R.string.parent_cards_delete),
                        color = colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                    )
                }
            },
            dismissButton = {
                TextButton({ deleteCardId = null }) {
                    Text(
                        stringResource(R.string.parent_cards_cancel),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                    )
                }
            },
        )
    }
}

/** 등록된 카드 한 장. 번호는 뒤 네 자리만 서버가 준다. */
@Composable
private fun CreditCard(card: Card, onMore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardInk)
            .padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ROOKIES PAY",
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 10.5.sp,
                letterSpacing = 1.6.sp,
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.parent_community_more),
                    modifier = Modifier.size(18.dp),
                    tint = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = "\u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  \u2022\u2022\u2022\u2022  ${card.last4}",
            color = Color.White,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 17.sp,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().padding(end = 10.dp)) {
            Text(
                card.ownerName,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.5.sp,
            )
            Text(
                card.expiry,
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 11.5.sp,
            )
        }
    }
}

/** 카드 그림 바탕. 브랜드 주황을 쓰면 앱이 발급한 카드처럼 보인다. */
private val CardInk = Color(0xFF1C2026)

/** 새 카드 등록 폼. */
@Composable
private fun ColumnScope.AddCardForm(state: CardUiState, viewModel: CardViewModel) {
    val colors = LocalKidhackColors.current
    val numberOptions = KeyboardOptions(keyboardType = KeyboardType.Number)

    Text(
        stringResource(R.string.parent_cards_new),
        modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 17.sp,
        color = colors.text,
    )
    Column(
        modifier = Modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KidTextField(
            state.cardNumber,
            viewModel::onCardNumber,
            stringResource(R.string.parent_cards_number),
            placeholder = stringResource(R.string.parent_cards_number_hint),
            keyboardOptions = numberOptions,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidTextField(
                state.expiry,
                viewModel::onExpiry,
                stringResource(R.string.parent_cards_expiry),
                placeholder = "MM/YY",
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
            KidTextField(
                state.cvc,
                viewModel::onCvc,
                "CVC",
                placeholder = stringResource(R.string.parent_cards_cvc_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidTextField(
                state.cardPassword2,
                viewModel::onCardPassword2,
                stringResource(R.string.parent_cards_password),
                placeholder = stringResource(R.string.parent_cards_password_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
            KidTextField(
                state.birthDate,
                viewModel::onBirthDate,
                stringResource(R.string.parent_cards_birth),
                placeholder = "YYMMDD",
                modifier = Modifier.weight(1f),
                keyboardOptions = numberOptions,
            )
        }
        KidTextField(
            state.ownerName,
            viewModel::onOwnerName,
            stringResource(R.string.parent_cards_owner),
            placeholder = stringResource(R.string.parent_cards_owner_hint),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KidButton(
                text = stringResource(R.string.parent_cards_cancel),
                onClick = { viewModel.toggleAdding(false) },
                modifier = Modifier.weight(1f),
                variant = KidButtonVariant.Secondary,
            )
            KidButton(
                text = stringResource(R.string.parent_cards_save),
                onClick = viewModel::register,
                modifier = Modifier.weight(2f),
                enabled = !state.loading,
            )
        }
    }
}
