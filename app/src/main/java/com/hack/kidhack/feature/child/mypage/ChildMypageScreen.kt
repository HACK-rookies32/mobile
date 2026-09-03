package com.hack.kidhack.feature.child.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 자녀 마이 — 부모쪽처럼 깔끔한 목록으로.
 *
 * <p>예전엔 주황 헤더 + 오늘의 활동 통계 3칸 + 페이/서비스/스샷/갤러리/버전이 잔뜩 있었다.
 * 페이는 이제 탭으로 나갔고, 서비스·카메라는 뺀 기능, 버전·통계는 죽은 값이라 걷어냈다.
 * 남은 것은 프로필, 부모님 연결, 공지·약관, 로그아웃뿐이다.
 */
@Composable
fun ChildMypageScreen(
    onLinkParent: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onTerms: () -> Unit = {},
    onNotice: () -> Unit = {},
    viewModel: ChildMypageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildTabScaffold(title = stringResource(R.string.child_tab_mypage)) {
        // 프로필 — 누르면 수정.
        KidSectionCard(modifier = Modifier.clickable(onClick = onEditProfile), contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.name.trim().take(1).ifBlank { "?" },
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 19.sp,
                    )
                }
                Spacer(Modifier.size(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.name,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                        color = colors.text,
                    )
                    Text(state.email, color = colors.subtle, fontSize = 12.sp)
                }
                Text("›", color = colors.subtle, fontSize = 20.sp)
            }
        }

        // 메뉴
        KidSectionCard(contentPadding = PaddingValues(horizontal = 14.dp)) {
            MyRow(R.drawable.ic_link, stringResource(R.string.child_mypage_link_parent), onLinkParent)
            HorizontalDivider(color = colors.line)
            MyRow(R.drawable.ic_report, stringResource(R.string.child_mypage_notice), onNotice)
            HorizontalDivider(color = colors.line)
            MyRow(R.drawable.ic_file, stringResource(R.string.child_mypage_terms), onTerms, last = true)
        }

        // 로그아웃
        KidSectionCard(
            modifier = Modifier.clickable(onClick = viewModel::logout),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                stringResource(R.string.child_mypage_logout),
                modifier = Modifier.fillMaxWidth(),
                color = colors.danger,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MyRow(@DrawableRes icon: Int, title: String, onClick: () -> Unit, last: Boolean = false) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
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
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.muted,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Text("›", color = colors.subtle, fontSize = 18.sp)
    }
}
