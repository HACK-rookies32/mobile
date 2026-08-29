package com.hack.kidhack.feature.child.mypage

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.ListRow
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildMypageScreen(
    onServices: () -> Unit = {},
    onLinkParent: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onCamera: () -> Unit = {},
    onPay: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onTerms: () -> Unit = {},
    onNotice: () -> Unit = {},
    viewModel: ChildMypageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    // 부모가 용돈을 보내거나 회수했을 수 있으니 탭 복귀 때마다 잔액을 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val comingSoon = {
        Toast.makeText(context, context.getString(R.string.child_mypage_coming_soon), Toast.LENGTH_SHORT).show()
    }

    ChildScreenScaffold(
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("😊", fontSize = 28.sp)
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        text = state.name,
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = state.email,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
            }
        },
    ) {
        KidCard {
            Text(
                text = stringResource(R.string.child_mypage_today),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                TodayStat(
                    emoji = "⏱️",
                    value = state.screenTime,
                    label = stringResource(R.string.child_mypage_stat_screentime),
                    background = colors.brandLight,
                    tint = colors.brandDark,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                TodayStat(
                    emoji = "💬",
                    value = stringResource(R.string.child_mypage_chat_count, state.chatCount),
                    label = stringResource(R.string.child_mypage_stat_chat),
                    background = Color(0xFFEFF6FF),
                    tint = colors.info,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                TodayStat(
                    emoji = "🚫",
                    value = stringResource(R.string.child_mypage_blocked_count, state.blockedCount),
                    label = stringResource(R.string.child_mypage_stat_blocked),
                    background = Color(0xFFFEF2F2),
                    tint = colors.danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_mypage_section_info),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
            ListRow(title = stringResource(R.string.child_mypage_edit_profile), onClick = onEditProfile)
            ListRow(
                title = stringResource(R.string.child_mypage_link_parent),
                leading = "\uD83D\uDD17",
                onClick = onLinkParent,
            )
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_mypage_section_service),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
            ListRow(
                title = stringResource(R.string.child_mypage_menu_pay),
                leading = "💳",
                subtitle = stringResource(
                    R.string.child_mypage_menu_pay_balance,
                    formatPoint(state.payBalance),
                ),
                onClick = onPay,
            )
            ListRow(title = stringResource(R.string.child_mypage_menu_services), onClick = onServices)
            ListRow(title = stringResource(R.string.child_mypage_menu_screenshots), onClick = onScreenshot)
            ListRow(
                title = stringResource(R.string.child_mypage_menu_gallery),
                onClick = onCamera,
                showDivider = false,
            )
        }

        KidCard {
            Text(
                text = stringResource(R.string.child_mypage_section_etc),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
            ListRow(title = stringResource(R.string.child_mypage_notice), onClick = onNotice)
            ListRow(title = stringResource(R.string.child_mypage_terms), onClick = onTerms)
            ListRow(title = stringResource(R.string.child_mypage_version), onClick = null)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::logout)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.child_mypage_logout),
                    modifier = Modifier.weight(1f),
                    color = colors.danger,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 14.sp,
                )
                Text("›", color = colors.border, fontSize = 16.sp)
            }
            // 자녀 로그아웃은 서버상 부모 권한(POST /children/{id}/logout)이다.
            // 이 버튼은 테스트 중 계정 전환용 임시 조치라 그 사실을 화면에 밝혀 둔다.
            Text(
                text = stringResource(R.string.child_mypage_logout_temp_notice),
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 1234567 → "1,234,567" */
private fun formatPoint(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
private fun TodayStat(
    emoji: String,
    value: String,
    label: String,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = tint,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 14.sp,
        )
        Text(
            text = label,
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 12.sp,
        )
    }
}
