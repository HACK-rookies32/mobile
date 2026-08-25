package com.hack.kidhack.feature.child.pay

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.component.QrCode
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import org.koin.androidx.compose.koinViewModel

private val QUICK_AMOUNTS = listOf(1_000, 3_000, 5_000, 10_000)

/**
 * 마이페이지 › 루키즈 페이 (자녀 모드).
 *
 * 자녀는 **잔액 한도 안에서 QR 결제만** 할 수 있다.
 * 용돈 보내기·회수는 부모 모드 기능이라 이 화면에 존재하지 않는다.
 */
@Composable
fun ChildPayScreen(
    onBack: () -> Unit = {},
    viewModel: ChildPayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_pay_back),
                modifier = Modifier.clickable(onClick = onBack),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_pay_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_pay_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        KidCard {
            Text(
                text = stringResource(R.string.child_pay_balance_label),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.child_pay_balance_value, formatPoint(state.balance)),
                color = colors.brand,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 30.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.child_pay_balance_note),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }

        val token = state.token
        if (token == null) {
            KidCard {
                Text(
                    text = stringResource(R.string.child_pay_amount_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_AMOUNTS.forEach { amount ->
                        Text(
                            text = stringResource(R.string.child_pay_quick, formatPoint(amount)),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.amount == amount) colors.brand else colors.brandLight,
                                )
                                .clickable(enabled = amount <= state.balance) {
                                    viewModel.quickAmount(amount)
                                }
                                .padding(vertical = 10.dp),
                            color = when {
                                amount > state.balance -> colors.border
                                state.amount == amount -> Color.White
                                else -> colors.brand
                            },
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                KidTextField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    label = stringResource(R.string.child_pay_amount_label),
                    placeholder = stringResource(R.string.child_pay_amount_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.overLimit,
                    supportingText = if (state.overLimit) {
                        stringResource(R.string.child_pay_over_limit)
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_pay_issue),
                    onClick = viewModel::issueToken,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canIssue,
                )
            }
        } else {
            KidCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.child_pay_qr_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.child_pay_qr_amount, formatPoint(token.amount)),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 24.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    ) {
                        QrCode(content = token.payload, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.child_pay_qr_expires, state.secondsLeft),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.child_pay_qr_guide),
                        color = colors.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    KidButton(
                        text = stringResource(R.string.child_pay_confirm),
                        onClick = viewModel::confirmPayment,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.paying,
                    )
                    KidButton(
                        text = stringResource(R.string.child_pay_cancel),
                        onClick = viewModel::cancelToken,
                        modifier = Modifier.fillMaxWidth(),
                        variant = KidButtonVariant.Ghost,
                    )
                }
            }
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_pay_history),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            if (state.history.isEmpty()) {
                Text(
                    text = stringResource(R.string.child_pay_history_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
            state.history.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.description,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(text = item.dateLabel, color = colors.muted, fontSize = 12.sp)
                    }
                    Text(
                        text = signedPoint(item.point),
                        color = if (item.point >= 0) colors.safe else colors.danger,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 14.sp,
                    )
                }
                if (index != state.history.lastIndex) HorizontalDivider(color = colors.border)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 1234567 → "1,234,567" */
private fun formatPoint(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

private fun signedPoint(value: Int): String =
    if (value >= 0) "+" + formatPoint(value) + "P" else "-" + formatPoint(-value) + "P"
