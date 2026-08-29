package com.hack.kidhack.feature.parent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryDetail
import com.hack.kidhack.domain.repository.InquiryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 화면 상태.
 *
 * 예전에는 이메일·전화·FAQ 타일이 깔린 시작 화면(GRID)이 있었는데, 이메일 주소·전화번호가
 * 가짜였고 FAQ 도 상수 두 줄이었다. 실제로 서버와 오가는 건 1:1 문의뿐이라 목록을
 * 첫 화면으로 둔다.
 */
enum class ContactPane { INQUIRY_LIST, INQUIRY_CREATE, INQUIRY_DETAIL }

data class ContactUiState(
    val pane: ContactPane = ContactPane.INQUIRY_LIST,
    val inquiries: List<Inquiry> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    /** 펼친 문의. 목록 항목에는 본문·첨부·답변이 없어 서버에서 따로 받아온다. */
    val detail: InquiryDetail? = null,
    val title: String = "",
    val content: String = "",
    /** 업로드를 마친 첨부의 **공개 경로**. 갤러리 URI 가 아니다. */
    val attachments: List<String> = emptyList(),
    /** 서버는 링크를 하나만 받는다. */
    val linkUrl: String = "",
    /** 수정 중인 문의 id. null 이면 새 문의다. */
    val editingId: String? = null,
    val uploading: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 고객센터 — 1:1 문의 (명세 21).
 *
 * 첨부는 `POST /inquiries/upload` 로 **먼저 올려** 공개 경로를 받고, 그 경로만 문의
 * 본문에 실어 보낸다. 예전에는 갤러리에서 고른 `content://` URI 를 그대로 보냈는데
 * 서버에는 그런 파일이 없다.
 *
 * 답변이 달린 문의는 서버가 수정·삭제를 400 으로 막으므로 버튼도 감춘다.
 */
class ContactViewModel(
    private val inquiryRepository: InquiryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    init { loadInquiries(page = 0, append = false) }

    fun open(pane: ContactPane) {
        _state.update { it.copy(pane = pane, error = null) }
        if (pane == ContactPane.INQUIRY_LIST) loadInquiries(page = 0, append = false)
    }

    /** 목록 항목을 누르면 본문을 받으러 간다. */
    fun openDetail(inquiry: Inquiry) {
        _state.update { it.copy(pane = ContactPane.INQUIRY_DETAIL, detail = null, loading = true, error = null) }
        viewModelScope.launch {
            runCatching { inquiryRepository.get(inquiry.id) }
                .onSuccess { detail -> _state.update { it.copy(detail = detail, loading = false) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        loadInquiries(page = current.page + 1, append = true)
    }

    private fun loadInquiries(page: Int, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { inquiryRepository.list(page) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            inquiries = if (append) it.inquiries + result.items else result.items,
                            page = result.page,
                            hasMore = result.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message, loading = false) } }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v) }
    fun onLinkUrl(v: String) = _state.update { it.copy(linkUrl = v) }

    /** 새 문의 작성 화면. 이전에 쓰던 내용이 남지 않도록 폼을 비운다. */
    fun startCreate() {
        _state.update {
            it.copy(
                pane = ContactPane.INQUIRY_CREATE,
                editingId = null,
                title = "",
                content = "",
                attachments = emptyList(),
                linkUrl = "",
                error = null,
            )
        }
    }

    /** 상세에서 수정으로. 답변 전에만 부른다. */
    fun startEdit() {
        val detail = _state.value.detail ?: return
        _state.update {
            it.copy(
                pane = ContactPane.INQUIRY_CREATE,
                editingId = detail.id,
                title = detail.title,
                content = detail.content,
                attachments = detail.attachments,
                linkUrl = detail.linkUrl.orEmpty(),
                error = null,
            )
        }
    }

    /** 고른 파일을 곧바로 업로드하고 공개 경로만 들고 있는다. */
    fun addAttachment(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            runCatching { inquiryRepository.uploadAttachment(uri.toString()) }
                .onSuccess { url -> _state.update { it.copy(attachments = it.attachments + url, uploading = false) } }
                .onFailure { t ->
                    _state.update { it.copy(uploading = false, error = t.message ?: "첨부 업로드에 실패했어요") }
                }
        }
    }

    /** 목록에서만 뺀다. 서버의 실물은 문의를 저장할 때 정리된다. */
    fun removeAttachment(url: String) = _state.update { it.copy(attachments = it.attachments - url) }

    fun submit() {
        val current = _state.value
        if (current.title.isBlank() || current.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val id = current.editingId
                if (id == null) {
                    inquiryRepository.create(current.title, current.content, current.attachments, current.linkUrl)
                } else {
                    inquiryRepository.update(id, current.title, current.content, current.attachments, current.linkUrl)
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        loading = false,
                        title = "",
                        content = "",
                        attachments = emptyList(),
                        linkUrl = "",
                        editingId = null,
                    )
                }
                open(ContactPane.INQUIRY_LIST)
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun delete() {
        val id = _state.value.detail?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { inquiryRepository.delete(id) }
                .onSuccess {
                    _state.update { it.copy(loading = false, detail = null) }
                    open(ContactPane.INQUIRY_LIST)
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }
}

@Composable
fun ParentContactScreen(
    onBack: () -> Unit = {},
    viewModel: ContactViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addAttachment(uri)
    }

    ParentScreenScaffold(
        header = {
            val title = when (state.pane) {
                ContactPane.INQUIRY_LIST -> stringResource(R.string.parent_contact_title)
                ContactPane.INQUIRY_CREATE ->
                    if (state.editingId == null) "1:1 문의하기" else "문의 수정"
                ContactPane.INQUIRY_DETAIL -> "문의 상세"
            }
            ParentHeaderBar(title) {
                // 목록이 첫 화면이라 여기서는 화면을 닫고, 안쪽에서는 목록으로 돌아간다.
                if (state.pane == ContactPane.INQUIRY_LIST) {
                    IconButton(onClick = onBack) { Text("✕", color = Color.White) }
                } else {
                    Text(
                        "←",
                        Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.open(ContactPane.INQUIRY_LIST) }
                            .padding(8.dp),
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            }
        },
    ) {
        if (state.error != null) {
            KidCard { Text(state.error.orEmpty(), color = colors.danger, fontSize = 13.sp) }
        }
        when (state.pane) {
            ContactPane.INQUIRY_LIST -> {
                KidButton("+ 새로운 문의하기", viewModel::startCreate, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                if (state.inquiries.isEmpty() && !state.loading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("문의 내역이 없습니다.", color = colors.muted)
                    }
                }
                state.inquiries.forEach { inquiry ->
                    KidCard(Modifier.clickable { viewModel.openDetail(inquiry) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inquiry.title, fontWeight = FontBold, fontSize = 14.sp)
                                Text(inquiry.createdAt.toDisplayDate(), color = colors.muted, fontSize = 12.sp)
                            }
                            Text(
                                if (inquiry.answered) "답변완료" else "답변대기",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (inquiry.answered) colors.safe.copy(0.1f) else colors.surface)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (inquiry.answered) colors.safe else colors.muted,
                                fontWeight = FontBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                if (state.hasMore) {
                    KidButton("더 보기", viewModel::loadMore, Modifier.fillMaxWidth(), KidButtonVariant.Secondary)
                }
            }

            ContactPane.INQUIRY_CREATE -> {
                KidTextField(state.title, viewModel::onTitle, "문의 제목", placeholder = "제목을 입력하세요")
                KidTextField(
                    value = state.content,
                    onValueChange = viewModel::onContent,
                    label = "문의 내용",
                    placeholder = "문의하실 내용을 상세히 적어주세요",
                    singleLine = false,
                    minLines = 6
                )

                Spacer(Modifier.height(16.dp))
                Text("첨부파일", fontWeight = FontBold, fontSize = 14.sp)
                if (state.uploading) {
                    Text("올리는 중…", color = colors.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface)
                                .clickable(enabled = !state.uploading) { fileLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) { Text("📷") }
                    }
                    items(state.attachments) { path ->
                        Box(Modifier.size(60.dp)) {
                            // 서버가 준 상대 경로라 그대로는 못 연다. origin 을 붙여 띄운다.
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(RemoteUrl.absolute(path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                            )
                            IconButton({ viewModel.removeAttachment(path) }, Modifier.align(Alignment.TopEnd).size(20.dp)) {
                                Text("✕", color = Color.White, fontSize = 10.sp, modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 서버가 링크를 하나만 받는다. 여러 개를 담던 예전 UI 는 저장되지 않았다.
                KidTextField(state.linkUrl, viewModel::onLinkUrl, "관련 링크", placeholder = "https://…")

                Spacer(Modifier.height(24.dp))
                KidButton(
                    if (state.editingId == null) "문의 제출하기" else "수정 저장하기",
                    viewModel::submit,
                    Modifier.fillMaxWidth(),
                    enabled = !state.loading && !state.uploading,
                )
            }

            ContactPane.INQUIRY_DETAIL -> {
                val detail = state.detail
                if (detail == null) {
                    Text(
                        if (state.loading) "불러오는 중…" else "문의를 불러오지 못했어요",
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                } else {
                    KidCard {
                        Text(detail.title, fontWeight = FontBold, fontSize = 16.sp)
                        Text(detail.createdAt.toDisplayDate(), color = colors.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(detail.content, fontSize = 14.sp)

                        if (detail.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(detail.attachments) { path ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(RemoteUrl.absolute(path))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                        if (!detail.linkUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val url = RemoteUrl.absolute(detail.linkUrl.filterNot { ch -> ch.isWhitespace() })
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🔗", fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(detail.linkUrl, color = colors.brand, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }

                    // 답변이 달리면 서버가 수정·삭제를 막는다(400). 그때는 버튼을 감춘다.
                    if (!detail.answered) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KidButton("수정", viewModel::startEdit, Modifier.weight(1f), KidButtonVariant.Secondary)
                            KidButton("삭제", viewModel::delete, Modifier.weight(1f), KidButtonVariant.Danger)
                        }
                    }

                    Text("관리자 답변", Modifier.padding(vertical = 12.dp), fontWeight = FontBold, fontSize = 14.sp)
                    val answer = detail.answer
                    if (answer == null) {
                        KidCard { Text("답변을 기다리는 중입니다.", color = colors.muted, fontSize = 13.sp) }
                    } else {
                        KidCard {
                            Column {
                                Text(answer.createdAt.toDisplayDate(), color = colors.muted, fontSize = 11.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(answer.content, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 서버는 ISO-8601 로만 준다. 목록·상세 모두 날짜 부분만 보여 준다. */
private fun String.toDisplayDate(): String = take(10).replace('-', '.').ifBlank { this }
