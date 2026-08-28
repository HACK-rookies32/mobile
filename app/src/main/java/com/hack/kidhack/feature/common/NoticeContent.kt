package com.hack.kidhack.feature.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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

/**
 * 공지 첨부 링크를 외부 브라우저로 연다.
 *
 * 공지 본문은 운영자가 넣는 값이라 앞뒤 공백이나 `https:// example.com` 처럼
 * 스킴 뒤에 공백이 낀 문자열이 들어올 수 있다. 그대로 [Uri.parse] 하면 처리할 앱을
 * 못 찾아 조용히 실패하므로 공백을 먼저 걷어낸다.
 *
 * 브라우저가 없는 기기에서는 `ActivityNotFoundException` 이 나므로 삼킨다
 * (`ContactScreen` 의 메일·전화 인텐트와 같은 처리).
 */
private fun Context.openLink(rawUrl: String) {
    val url = rawUrl.filterNot { it.isWhitespace() }
    if (url.isBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

data class NoticeItem(
    val title: String,
    val date: String,
    val important: Boolean,
    val body: String,
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
)

/**
 * 공지 목록.
 *
 * 모바일용 `GET /notices` 가 명세에 없어(GAP-08) 서버에서 받아오지 않는다.
 * 관리자 경로(`/admin/notices`)는 관리자 토큰이 필요하므로 재사용하지 않는다.
 * 백엔드가 모바일 공지 GET 을 열면 여기를 리포지토리 호출로 바꾸면 된다.
 *
 * 부모·자녀가 같은 목록을 본다. 역할별로 다른 공지를 노출해야 하면
 * 이 값을 화면별로 넘기도록 [NoticeViewModel] 을 고친다.
 */
private val DEFAULT_NOTICES = listOf(
    NoticeItem(
        title = "[필독] 개인정보처리방침 개정 안내",
        date = "2024.08.20",
        important = true,
        body = "안녕하세요. 루키즈 서비스 개인정보처리방침이 개정되었습니다. 자세한 내용은 아래 첨부된 이미지와 링크를 참고해 주시기 바랍니다.",
        imageUrls = listOf("https://api.childsafe.example.com/notices/policy.png"),
        links = listOf("https:// rookiekids.com/privacy-v2")
    ),
    NoticeItem(
        title = "루키즈 2.5.0 업데이트 - AI 유해사이트 감지 강화",
        date = "2024.08.15",
        important = false,
        body = "키워드 기반 유해사이트 차단이 강화되었습니다. AI 필터링 기술이 도입되어 더욱 안전한 환경을 제공합니다.",
        imageUrls = listOf("https://api.childsafe.example.com/notices/update250.png")
    ),
    NoticeItem("자녀 기기 원격 잠금 기능 추가 안내", "2024.08.10", false, "원격 잠금은 준비 중인 안내 문구입니다. 모바일 API는 없습니다."),
)

data class NoticeUiState(
    val items: List<NoticeItem> = DEFAULT_NOTICES,
    val opened: NoticeItem? = null,
)

/** 부모·자녀 공지 화면이 함께 쓴다. 목적지마다 별도 인스턴스를 받으므로 펼침 상태는 섞이지 않는다. */
class NoticeViewModel : ViewModel() {
    private val _state = MutableStateFlow(NoticeUiState())
    val state: StateFlow<NoticeUiState> = _state.asStateFlow()
    fun open(item: NoticeItem) = _state.update { it.copy(opened = item) }
    fun close() = _state.update { it.copy(opened = null) }
}

/**
 * 공지 목록 또는 펼친 공지 하나를 그린다.
 *
 * 호출부의 스캐폴드 안에 그대로 펼쳐지도록 [ColumnScope] 확장으로 둔다.
 */
@Composable
fun ColumnScope.NoticeBody(
    state: NoticeUiState,
    onOpen: (NoticeItem) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val opened = state.opened

    if (opened != null) {
        KidCard {
            Text(opened.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
            Text(opened.date, color = colors.muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Text(opened.body, fontSize = 14.sp, color = colors.text)

            // 이미지 첨부 표시
            if (opened.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(opened.imageUrls) { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 200.dp, height = 120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                        )
                    }
                }
            }

            // 링크 첨부 표시
            if (opened.links.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                val context = LocalContext.current
                opened.links.forEach { link ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .clickable { context.openLink(link) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔗", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(link, fontSize = 13.sp, color = colors.brand, maxLines = 1)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        KidCard(Modifier.clickable(onClick = onClose)) {
            Text("← 목록으로 돌아가기", color = colors.brand, fontWeight = FontBold, fontSize = 14.sp)
        }
    } else {
        state.items.forEach { n ->
            KidCard(Modifier.clickable { onOpen(n) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (n.important) {
                        Text(
                            stringResource(R.string.notice_badge),
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
                    Column(Modifier.weight(1f)) {
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
