package com.hack.kidhack.feature.child.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import androidx.compose.foundation.border
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildHomeScreen(
    onChat: () -> Unit = {},
    onServices: () -> Unit = {},
    onMypage: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onCamera: () -> Unit = {},
    viewModel: ChildHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    val context = LocalContext.current

    // 권한·GPS 는 설정 화면에서 바뀔 수 있으니 탭으로 돌아올 때마다 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildScreenScaffold(
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.alias.isBlank()) {
                            stringResource(R.string.child_home_greeting_generic)
                        } else {
                            stringResource(R.string.child_home_greeting, state.alias)
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(R.string.child_home_today_safe),
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 20.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onScreenshot),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔔", fontSize = 18.sp)
                }
            }
        },
    ) {
        val loadError = state.error
        if (loadError != null) {
            RetryCard(message = loadError, onRetry = viewModel::refresh)
        }

        // 보호 기능이 꺼져 있으면 스크롤하지 않아도 바로 보이게 맨 위에 알린다.
        state.needsAttention.firstOrNull()?.let { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(1.dp, colors.warn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_home_protection_off, KoreanParticles.subject(row.label)),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(R.string.child_home_protection_off_hint),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                KidButton(
                    text = stringResource(R.string.child_home_protection_turn_on),
                    onClick = {
                        val action = row.settingsAction ?: return@KidButton
                        runCatching {
                            context.startActivity(
                                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    variant = KidButtonVariant.Secondary,
                )
            }
        }

        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("😊", fontSize = 28.sp)
                }
                Spacer(Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.name,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 18.sp,
                        color = colors.text,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusTint = if (state.protectedNow) colors.safe else colors.warn
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusTint),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = stringResource(
                                if (state.protectedNow) {
                                    R.string.child_home_protected
                                } else {
                                    R.string.child_home_unprotected
                                },
                            ),
                            color = statusTint,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.child_home_screentime),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = state.usedLabel,
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "/ ${state.limitLabel}",
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        KidCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.child_home_today_screentime),
                    modifier = Modifier.weight(1f),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.child_home_remaining, state.remaining),
                    color = colors.brand,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.border),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(12.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Brush.horizontalGradient(listOf(colors.brand, colors.brandDark))),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.child_home_used, state.usedLabel),
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(R.string.child_home_limit, state.limitLabel),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(
                emoji = "💬",
                label = stringResource(R.string.child_home_quick_chat),
                background = Color(0xFFEFF6FF),
                tint = colors.info,
                modifier = Modifier.weight(1f),
                onClick = onChat,
            )
            QuickAction(
                emoji = "🛡️",
                label = stringResource(R.string.child_home_quick_services),
                background = colors.brandLight,
                tint = colors.brandDark,
                modifier = Modifier.weight(1f),
                onClick = onServices,
            )
            QuickAction(
                emoji = "👤",
                label = stringResource(R.string.child_home_quick_mypage),
                background = Color(0xFFFAF5FF),
                tint = Color(0xFF6B21A8),
                modifier = Modifier.weight(1f),
                onClick = onMypage,
            )
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_home_guarding),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            state.services.forEachIndexed { index, row ->
                // 꺼져 있고 열어 줄 설정 화면이 있으면 행 전체를 눌러 바로 갈 수 있게 한다.
                val action = row.settingsAction?.takeIf { !row.active }
                Row(
                    modifier = Modifier
                        .then(
                            if (action != null) {
                                Modifier.clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.emoji, fontSize = 18.sp)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.label,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(
                            text = row.desc,
                            color = colors.muted,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = stringResource(if (row.active) R.string.child_on else R.string.child_off),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (row.active) Color(0xFFF0FDF4) else colors.surface)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (row.active) colors.safe else colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                }
                if (index != state.services.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_home_recent_alerts),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            if (state.alerts.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.loading) R.string.child_home_alerts_loading
                        else R.string.child_home_alerts_empty,
                    ),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
            state.alerts.forEachIndexed { index, alert ->
                val tint = when (alert.tint) {
                    AlertTint.Brand -> colors.brand
                    AlertTint.Danger -> colors.danger
                    AlertTint.Warn -> colors.warn
                    AlertTint.Info -> colors.info
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                alert.opensScreenshot -> Modifier.clickable(onClick = onScreenshot)
                                alert.opensCamera -> Modifier.clickable(onClick = onCamera)
                                alert.opensChat -> Modifier.clickable(onClick = onChat)
                                else -> Modifier
                            },
                        )
                        .padding(vertical = 8.dp),
                ) {
                    Text(alert.emoji, fontSize = 18.sp, color = tint)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = alert.message,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                            color = colors.text,
                        )
                        Text(text = alert.time, color = colors.muted, fontSize = 12.sp)
                    }
                }
                if (index != state.alerts.lastIndex) {
                    HorizontalDivider(color = colors.border)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickAction(
    emoji: String,
    label: String,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(
            text = label,
            color = tint,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
    }
}
