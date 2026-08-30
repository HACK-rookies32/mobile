package com.hack.kidhack.feature.child.pay

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.QrCode
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 마이페이지 › 루키즈 페이 (자녀 모드).
 *
 * 자녀는 잔액 한도 안에서 **결제 QR** 을 띄우고, 매장 단말이 스캔·승인하면 결제가 완료된다.
 * 금액은 자녀가 정하지 않고 단말이 확정한다(보내기·회수 UI 는 부모 모드 기능이라 여기 없다).
 */
@Composable
fun ChildPayScreen(
    onBack: () -> Unit = {},
    viewModel: ChildPayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

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

        val ticket = state.ticket
        if (ticket == null) {
            KidCard {
                Text(
                    text = "매장에서 결제할 때 QR 을 보여 주세요",
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "금액은 매장 단말이 정해요. QR 을 만들면 60초 동안 쓸 수 있어요.",
                    color = colors.muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_pay_issue),
                    onClick = viewModel::issueQr,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.issuing,
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
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    ) {
                        QrCode(content = ticket.qrToken, modifier = Modifier.fillMaxWidth())
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
                        text = "매장 단말이 스캔해 승인하면 결제가 완료돼요.",
                        color = colors.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    KidButton(
                        text = stringResource(R.string.child_pay_cancel),
                        onClick = viewModel::cancelTicket,
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
                val cancelled = item.status == "CANCELLED"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = if (cancelled) "↩️" else "🧾", fontSize = 18.sp)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.storeName,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(text = item.paidAtLabel, color = colors.muted, fontSize = 12.sp)
                    }
                    Text(
                        text = if (cancelled) "취소됨" else "-" + formatPoint(item.amount) + "P",
                        color = if (cancelled) colors.muted else colors.danger,
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
