package com.hack.kidhack.feature.child.home

import android.content.Intent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.feature.child.formatDurationMin
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import org.koin.androidx.compose.koinViewModel

/**
 * 자녀 홈 — 부모쪽과 같은 규격으로 다시 지었다.
 *
 * <p>예전엔 주황 히어로 + 이모지(😊⚠️🔔)가 화면을 채웠다. 아이 화면이라 화려했지만 정보가
 * 색에 묻히고 부모 앱과 두 앱처럼 보였다. 흰 헤더 + 회색 캔버스에 흰 카드로 바꾸고, 이모지는
 * 전부 벡터 아이콘으로 갈았다. 색은 「보호 중/확인 필요」 상태 하나에만 얹는다.
 */
@Composable
fun ChildHomeScreen(
    onScreenshot: () -> Unit = {},
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

    ChildTabScaffold(
        title = stringResource(R.string.child_tab_home),
        action = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onScreenshot),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bell),
                    contentDescription = stringResource(R.string.child_home_alerts_title),
                    modifier = Modifier.size(21.dp),
                    tint = colors.text,
                )
                if (state.needsAttention.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 9.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.danger),
                    )
                }
            }
        },
    ) {
        state.error?.let { RetryCard(message = it, onRetry = viewModel::refresh) }

        // 보호 상태 히어로 — 색은 여기 하나에만. 안전=초록, 확인 필요=주황.
        ProtectionHero(
            name = state.name,
            protectedNow = state.protectedNow,
            attention = state.needsAttention.firstOrNull(),
            onTurnOn = { action ->
                runCatching {
                    context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )

        // 오늘 사용 + 한도까지 남은 시간(부모가 정한 한도가 있으면).
        KidSectionCard(contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.child_home_today_screentime),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.usedLabel,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 26.sp,
                        color = colors.text,
                    )
                }
                state.remainingMinutes?.let { rem ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.child_home_limit_left),
                            color = colors.subtle,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 11.5.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatDurationMin(rem),
                            color = if (rem == 0) colors.danger else colors.brand,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBlack,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            // 한도가 있으면 사용 비율 막대. 없으면 생략(근거 없는 3시간 상수는 두지 않는다).
            state.limitMinutes?.takeIf { it > 0 }?.let { limit ->
                Spacer(Modifier.height(12.dp))
                UsageBar(fraction = (state.usedMinutes.toFloat() / limit).coerceIn(0f, 1f), over = state.usedMinutes >= limit)
            }
        }

        // 오늘 많이 쓴 앱 — 자녀가 스스로 「내가 뭘 얼마나 썼는지」 본다.
        KidSectionCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                stringResource(R.string.child_home_top_apps),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(4.dp))
            if (state.usages.isEmpty()) {
                Text(
                    stringResource(R.string.child_home_top_apps_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                val maxSec = (state.usages.maxOfOrNull { it.durationSec } ?: 1).coerceAtLeast(1)
                state.usages.take(5).forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.appName,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 13.5.sp,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            UsageBar(fraction = (app.durationSec.toFloat() / maxSec).coerceIn(0f, 1f), over = false)
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            formatDurationMin(app.durationSec / 60),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 사용 비율 막대. 한도 초과면 붉게. */
@Composable
private fun UsageBar(fraction: Float, over: Boolean) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(colors.line),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (over) colors.danger else colors.brand),
        )
    }
}

/** 보호 상태 히어로 카드. 색은 상태 하나에만 얹는다 — 안전=초록, 확인 필요=주황. */
@Composable
private fun ProtectionHero(
    name: String,
    protectedNow: Boolean,
    attention: HomeServiceRow?,
    onTurnOn: (String) -> Unit,
) {
    val colors = LocalKidhackColors.current
    val tint = if (protectedNow) colors.safe else colors.warn
    KidSectionCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tint,
                )
            }
            Spacer(Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (protectedNow) R.string.child_home_hero_safe else R.string.child_home_hero_attention,
                    ),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 16.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (protectedNow || attention == null) {
                        if (name.isBlank()) stringResource(R.string.child_home_hero_safe_sub)
                        else stringResource(R.string.child_home_hero_safe_sub_named, name)
                    } else {
                        stringResource(R.string.child_home_protection_off, KoreanParticles.subject(attention.label))
                    },
                    color = colors.muted,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 꺼진 보호가 있으면 바로 켤 수 있게 버튼을 붙인다.
        if (!protectedNow && attention?.settingsAction != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.child_home_protection_turn_on),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warn)
                    .clickable { onTurnOn(attention.settingsAction) }
                    .padding(vertical = 12.dp),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

