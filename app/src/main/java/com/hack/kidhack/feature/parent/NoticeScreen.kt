package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel

data class NoticeItem(
    val title: String,
    val date: String,
    val important: Boolean,
    val body: String,
)

data class NoticeUiState(
    val items: List<NoticeItem> = listOf(
        NoticeItem("[필독] 개인정보처리방침 개정 안내", "2024.08.20", true, "개인정보처리방침이 개정되었습니다. 자세한 내용은 앱 공지에서 확인해 주세요."),
        NoticeItem("루키즈 2.5.0 업데이트 - AI 유해사이트 감지 강화", "2024.08.15", false, "키워드 기반 유해사이트 차단이 강화되었습니다."),
        NoticeItem("자녀 기기 원격 잠금 기능 추가 안내", "2024.08.10", false, "원격 잠금은 준비 중인 안내 문구입니다. 모바일 API는 없습니다."),
    ),
    val opened: NoticeItem? = null,
)

class NoticeViewModel : ViewModel() {
    private val _state = MutableStateFlow(NoticeUiState())
    val state: StateFlow<NoticeUiState> = _state.asStateFlow()
    fun open(item: NoticeItem) = _state.update { it.copy(opened = item) }
    fun close() = _state.update { it.copy(opened = null) }
}

@Composable
fun ParentNoticeScreen(
    onBack: () -> Unit = {},
    viewModel: NoticeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val opened = state.opened

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_notice_title), onBack) },
    ) {
        if (opened != null) {
            KidCard {
                Text(opened.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                Text(opened.date, color = colors.muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text(opened.body, fontSize = 14.sp, color = colors.text)
            }
            KidCard(Modifier.clickable(onClick = viewModel::close)) {
                Text("← 목록", color = colors.brand, fontWeight = FontBold)
            }
        } else {
            state.items.forEach { n ->
                KidCard(Modifier.clickable { viewModel.open(n) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.important) {
                            Text(
                                stringResource(R.string.parent_notice_badge),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.brandLight)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                color = colors.brand,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBlack,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.padding(6.dp))
                        }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(
                                n.title,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 14.sp,
                                color = if (n.important) colors.brandDark else colors.text,
                            )
                            Text(n.date, color = colors.muted, fontSize = 12.sp)
                        }
                        Text("›", color = colors.border, fontSize = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
