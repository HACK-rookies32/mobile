package com.hack.kidhack.feature.parent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Brush
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
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
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
@Composable
fun ParentCardsScreen(
    onBack: () -> Unit = {},
    viewModel: CardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var deleteCardId by remember { mutableStateOf<String?>(null) }

    ParentScreenScaffold(
        header = { ParentHeaderBar("카드 관리", onBack) }
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)

        if (!state.loading && state.items.isEmpty() && !state.adding) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("등록된 카드가 없어요", color = colors.muted)
            }
        }

        state.items.forEach { card ->
            KidCard(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { deleteCardId = card.cardId }
                )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark)))
                        .padding(24.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Text("ROOKIES PAY", color = Color.White.copy(alpha = 0.8f), fontWeight = FontBold, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.3f)))
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "**** **** **** ${card.last4}",
                            color = Color.White,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(card.ownerName, color = Color.White, fontSize = 14.sp)
                            Text(card.expiry, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        if (state.adding) {
            KidCard {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("새 카드 등록", fontWeight = FontBlack, fontSize = 18.sp, color = colors.text)
                    Spacer(Modifier.height(16.dp))
                    
                    KidTextField(state.cardNumber, viewModel::onCardNumber, "카드번호", placeholder = "16자리 숫자", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KidTextField(state.expiry, viewModel::onExpiry, "유효기간", placeholder = "MM/YY", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        KidTextField(state.cvc, viewModel::onCvc, "CVC", placeholder = "3자리", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KidTextField(state.cardPassword2, viewModel::onCardPassword2, "비밀번호", placeholder = "앞 2자리", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        KidTextField(state.birthDate, viewModel::onBirthDate, "생년월일", placeholder = "YYMMDD", modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    
                    KidTextField(state.ownerName, viewModel::onOwnerName, "소유자 이름", placeholder = "실명 입력")
                    
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KidButton("취소", { viewModel.toggleAdding(false) }, Modifier.weight(1f), variant = KidButtonVariant.Ghost)
                        KidButton("저장하기", viewModel::register, Modifier.weight(2f))
                    }
                }
            }
        } else {
            KidButton("+ 카드 추가", { viewModel.toggleAdding(true) }, Modifier.fillMaxWidth(), variant = KidButtonVariant.Secondary)
        }
        
        Spacer(Modifier.height(16.dp))
    }

    // 삭제 확인 다이얼로그
    deleteCardId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCardId = null },
            title = { Text("카드 삭제", fontWeight = FontBold) },
            text = { Text("정말 이 카드를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(id)
                        deleteCardId = null
                    }
                ) {
                    Text("삭제", color = colors.danger, fontWeight = FontBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCardId = null }) {
                    Text("취소", color = colors.muted)
                }
            }
        )
    }
}
