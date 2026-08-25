package com.hack.kidhack.feature.child.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.domain.model.PayQrToken
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.SessionRepository
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
    val history: List<PointHistory> = emptyList(),
    val amountInput: String = "",
    val token: PayQrToken? = null,
    val secondsLeft: Int = 0,
    val paying: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val amount: Int get() = amountInput.filter { it.isDigit() }.toIntOrNull() ?: 0

    /** 잔액 한도 안에서만 결제 가능 — 자녀 모드의 핵심 제약. */
    val overLimit: Boolean get() = amount > balance
    val canIssue: Boolean get() = amount > 0 && !overLimit && !paying
}

/**
 * 자녀 모드 루키즈페이.
 *
 * 부모가 보낸 용돈(포인트) **잔액 한도 안에서만** QR 결제 토큰을 발급한다.
 * 발급된 토큰은 [TOKEN_TTL_SECONDS] 초 뒤 자동 만료되고, 가맹점이 스캔해 승인하면
 * `POST /points/use` 로 잔액이 차감된다.
 *
 * 송금·회수(부모 모드)는 여기서 다루지 않는다 — 자녀 앱에는 그 UI 자체가 없다.
 */
class ChildPayViewModel(
    private val pointRepository: PointRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildPayUiState())
    val state: StateFlow<ChildPayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val balance = runCatching { pointRepository.balance().balance }.getOrDefault(0)
            val history = runCatching { pointRepository.history() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, balance = balance, history = history) }
        }
    }

    fun onAmountChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(MAX_AMOUNT_DIGITS)
        _state.update { it.copy(amountInput = digits, error = null) }
    }

    fun quickAmount(amount: Int) = onAmountChange(amount.toString())

    /** 결제용 QR 토큰 발급. 서버 승인 전이라 잔액은 아직 깎지 않는다. */
    fun issueToken() {
        val current = _state.value
        if (!current.canIssue) {
            if (current.overLimit) _state.update { it.copy(error = "잔액보다 많은 금액은 결제할 수 없어요") }
            return
        }
        viewModelScope.launch {
            val childId = sessionRepository.current()?.user?.id.orEmpty()
            val nonce = Random.nextLong(NONCE_BOUND).toString(RADIX_36).uppercase()
            val expiresAt = System.currentTimeMillis() + TOKEN_TTL_SECONDS * 1000L
            val payload = listOf(
                PAYLOAD_PREFIX,
                childId,
                current.amount.toString(),
                nonce,
                expiresAt.toString(),
            ).joinToString(PAYLOAD_SEPARATOR)

            _state.update {
                it.copy(
                    token = PayQrToken(payload, current.amount, expiresAt),
                    secondsLeft = TOKEN_TTL_SECONDS,
                    error = null,
                    message = null,
                )
            }
            startCountdown()
        }
    }

    fun cancelToken() = _state.update { it.copy(token = null, secondsLeft = 0) }

    /**
     * 가맹점 승인 시뮬레이션.
     *
     * 실제 서비스에서는 가맹점 단말이 QR 을 읽어 서버로 승인 요청을 보내고,
     * 자녀 앱은 소켓으로 결과만 받는다. 데모에서는 자녀 앱이 직접 확정한다.
     */
    fun confirmPayment() {
        val token = _state.value.token ?: return
        if (System.currentTimeMillis() > token.expiresAtMillis) {
            _state.update { it.copy(token = null, secondsLeft = 0, error = "QR 이 만료됐어요. 다시 발급해 주세요") }
            return
        }
        if (token.amount > _state.value.balance) {
            _state.update { it.copy(error = "잔액이 부족해요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(paying = true, error = null) }
            val result = runCatching { pointRepository.use(QR_PRODUCT_ID, token.amount) }
            _state.update {
                it.copy(
                    paying = false,
                    token = null,
                    secondsLeft = 0,
                    amountInput = if (result.isSuccess) "" else it.amountInput,
                    message = if (result.isSuccess) "결제가 끝났어요!" else null,
                    error = if (result.isSuccess) null else "결제에 실패했어요. 잠시 후 다시 시도해 주세요",
                )
            }
            if (result.isSuccess) refresh()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun startCountdown() {
        viewModelScope.launch {
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

        /** 명세의 `POST /points/use` 는 productId 를 요구한다. QR 결제용 고정 값. */
        const val QR_PRODUCT_ID = 9001
    }
}
