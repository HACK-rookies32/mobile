package com.hack.kidhack.feature.child.pay

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.QrCode
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 루키즈 페이 (자녀 탭).
 *
 * 자녀는 잔액 한도 안에서 **결제 QR** 을 띄우고, 매장 단말이 스캔·승인하면 결제가 완료된다.
 * 금액은 자녀가 정하지 않고 단말이 확정한다(보내기·회수는 부모 기능이라 여기 없다).
 * 하단 탭이므로 뒤로가기 헤더 대신 「페이」 제목만 둔다.
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

    ChildTabScaffold(title = stringResource(R.string.child_tab_pay)) {
        // 잔액 — 깔끔한 흰 카드. 브랜드색은 결제 버튼에만 남기고 숫자는 먹색으로 차분하게.
        KidSectionCard(contentPadding = PaddingValues(18.dp)) {
            Text(
                stringResource(R.string.child_pay_balance_label),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.child_pay_balance_value, formatPoint(state.balance)),
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 30.sp,
            )
        }

        val ticket = state.ticket
        if (ticket == null) {
            // QR 발급 버튼 — 이 화면의 유일한 브랜드색.
            KidButton(
                text = stringResource(R.string.child_pay_issue),
                onClick = viewModel::issueQr,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.issuing,
            )
            Text(
                stringResource(R.string.child_pay_balance_note),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            KidSectionCard(contentPadding = PaddingValues(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.child_pay_qr_title),
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
                        stringResource(R.string.child_pay_qr_expires, state.secondsLeft),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.child_pay_qr_hint),
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

        // 결제 내역
        KidSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                stringResource(R.string.child_pay_history),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(6.dp))
            if (state.history.isEmpty()) {
                Text(
                    stringResource(R.string.child_pay_history_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            state.history.forEachIndexed { index, item ->
                val cancelled = item.status == "CANCELLED"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_card),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.muted,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.storeName,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(item.paidAtLabel, color = colors.subtle, fontSize = 11.5.sp)
                    }
                    Text(
                        text = if (cancelled) stringResource(R.string.child_pay_cancelled) else "-" + formatPoint(item.amount) + "원",
                        color = if (cancelled) colors.subtle else colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 14.sp,
                    )
                }
                if (index != state.history.lastIndex) HorizontalDivider(color = colors.line)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 1234567 → "1,234,567" */
private fun formatPoint(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
