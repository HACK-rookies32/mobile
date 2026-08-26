package com.hack.kidhack.feature.child.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.PayQrToken
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.childId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ChildPayUiState(
    val loading: Boolean = true,
    val balance: Int = 0,
    val history: List<AllowanceEntry> = emptyList(),
    val amountInput: String = "",
    val token: PayQrToken? = null,
    val secondsLeft: Int = 0,
    val paying: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val amount: Int get() = amountInput.filter { it.isDigit() }.toIntOrNull() ?: 0

    /** 부모가 채워 준 잔액 한도 안에서만 결제 가능 — 자녀 모드의 핵심 제약. */
    val overLimit: Boolean get() = amount > balance
    val canIssue: Boolean get() = amount > 0 && !overLimit && !paying
}

/**
 * 자녀 모드 루키즈페이.
 *
 * 잔액의 공급원은 오직 부모의 「용돈 보내기」이고, 부모는 언제든 회수할 수 있다.
 * 그래서 결제 확정 직전에 잔액을 **서버에서 다시 읽어** 확인한다
 * (QR 을 띄워 둔 사이에 부모가 회수했을 수 있다).
 *
 * 용돈 보내기·회수 UI 는 부모 모드 기능이라 이 화면에 존재하지 않는다.
 */
class ChildPayViewModel(
    private val allowanceRepository: AllowanceRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildPayUiState())
    val state: StateFlow<ChildPayUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val childId = sessionRepository.childId()
            val balance = runCatching { allowanceRepository.balance(childId).balance }
            val history = runCatching { allowanceRepository.history(childId) }
            _state.update {
                it.copy(
                    loading = false,
                    balance = balance.getOrDefault(it.balance),
                    history = history.getOrDefault(it.history),
                    error = if (balance.isFailure) "잔액을 불러오지 못했어요" else it.error,
                )
            }
        }
    }

    fun onAmountChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(MAX_AMOUNT_DIGITS)
        _state.update { it.copy(amountInput = digits, error = null) }
    }

    fun quickAmount(amount: Int) = onAmountChange(amount.toString())

    /** 결제용 QR 토큰 발급. 서버 승인 전이므로 잔액은 아직 깎이지 않는다. */
    fun issueToken() {
        val current = _state.value
        if (!current.canIssue) {
            if (current.overLimit) _state.update { it.copy(error = "잔액보다 많은 금액은 결제할 수 없어요") }
            return
        }
        viewModelScope.launch {
            val childId = sessionRepository.childId()
            val token = Random.nextLong(NONCE_BOUND).toString(RADIX_36).uppercase()
            val expiresAt = System.currentTimeMillis() + TOKEN_TTL_SECONDS * 1000L
            val payload = listOf(
                PAYLOAD_PREFIX,
                childId,
                current.amount.toString(),
                token,
                expiresAt.toString(),
            ).joinToString(PAYLOAD_SEPARATOR)

            _state.update {
                it.copy(
                    token = PayQrToken(token, payload, current.amount, expiresAt),
                    secondsLeft = TOKEN_TTL_SECONDS,
                    error = null,
                    message = null,
                )
            }
            startCountdown()
        }
    }

    fun cancelToken() {
        countdownJob?.cancel()
        _state.update { it.copy(token = null, secondsLeft = 0) }
    }

    /**
     * 가맹점 승인 확정.
     *
     * 실서비스에서는 가맹점 단말이 QR 을 읽어 서버로 승인 요청을 보내고 자녀 앱은 결과만 받는다.
     * 가맹점 단말이 없는 지금은 자녀 앱이 같은 토큰으로 승인을 요청한다.
     */
    fun confirmPayment() {
        val token = _state.value.token ?: return
        if (System.currentTimeMillis() > token.expiresAtMillis) {
            cancelToken()
            _state.update { it.copy(error = "QR 이 만료됐어요. 다시 만들어 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(paying = true, error = null) }
            val childId = sessionRepository.childId()

            // QR 을 띄워 둔 사이 부모가 회수했을 수 있으므로 최신 잔액을 다시 확인한다.
            val latest = runCatching { allowanceRepository.balance(childId).balance }.getOrNull()
            if (latest != null && token.amount > latest) {
                countdownJob?.cancel()
                _state.update {
                    it.copy(
                        paying = false,
                        token = null,
                        secondsLeft = 0,
                        balance = latest,
                        error = "잔액이 부족해요. 부모님이 용돈을 회수했을 수 있어요",
                    )
                }
                return@launch
            }

            val result = runCatching {
                allowanceRepository.pay(
                    childId = childId,
                    token = token.token,
                    amount = token.amount,
                    merchant = null,
                )
            }
            countdownJob?.cancel()
            result
                .onSuccess { payment ->
                    _state.update {
                        it.copy(
                            paying = false,
                            token = null,
                            secondsLeft = 0,
                            amountInput = "",
                            balance = payment.balanceAfter,
                            message = "결제가 끝났어요!",
                        )
                    }
                    refresh()
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            paying = false,
                            token = null,
                            secondsLeft = 0,
                            error = t.message ?: "결제에 실패했어요. 잠시 후 다시 시도해 주세요",
                        )
                    }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val token = _state.value.token ?: return@launch
                val left = ((token.expiresAtMillis - System.currentTimeMillis()) / 1000).toInt()
                if (left <= 0) {
                    _state.update { it.copy(token = null, secondsLeft = 0) }
                    return@launch
                }
                _state.update { it.copy(secondsLeft = left) }
            }
        }
    }

    private companion object {
        const val TOKEN_TTL_SECONDS = 180
        const val MAX_AMOUNT_DIGITS = 7
        const val PAYLOAD_PREFIX = "KIDHACKPAY1"
        const val PAYLOAD_SEPARATOR = "|"
        const val NONCE_BOUND = 1_000_000_000L
        const val RADIX_36 = 36
    }
}
