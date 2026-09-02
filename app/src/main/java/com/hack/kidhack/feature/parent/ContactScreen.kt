package com.hack.kidhack.feature.parent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
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

/**
 * 1:1 문의.
 *
 * <p>목록·작성·상세가 한 화면 안에서 갈린다(`pane`). 헤더 왼쪽이 <b>✕</b> 였는데, 마이페이지
 * 에서 들어온 화면이라 닫기 표시는 어디로 돌아가는지 알려 주지 않는다. 뒤로 가기로 바꿨다.
 */
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

    // 안쪽 화면에서 뒤로 가면 목록으로. 여기서 막지 않으면 문의를 쓰다 말고 화면이 닫힌다.
    BackHandler(enabled = state.pane != ContactPane.INQUIRY_LIST) {
        viewModel.open(ContactPane.INQUIRY_LIST)
    }
    val leaveInner = { viewModel.open(ContactPane.INQUIRY_LIST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (state.pane == ContactPane.INQUIRY_LIST) onBack() else leaveInner() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.pane == ContactPane.INQUIRY_CREATE) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.parent_back),
                        modifier = Modifier.size(19.dp),
                        tint = colors.text,
                    )
                } else {
                    Text("‹", color = colors.text, fontSize = 26.sp)
                }
            }
            if (state.pane == ContactPane.INQUIRY_CREATE) {
                Text(
                    text = stringResource(
                        if (state.editingId == null) R.string.parent_contact_create
                        else R.string.parent_contact_edit,
                    ),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 16.sp,
                    color = colors.text,
                )
            }
            Spacer(Modifier.weight(1f))
            when (state.pane) {
                // 폭 전체 「+ 새로운 문의하기」 버튼이 목록 위를 가로막고 있었다.
                ContactPane.INQUIRY_LIST -> HeaderAction(
                    label = stringResource(R.string.parent_contact_create),
                    enabled = true,
                    onClick = viewModel::startCreate,
                )
                ContactPane.INQUIRY_CREATE -> HeaderAction(
                    label = stringResource(R.string.parent_contact_submit),
                    enabled = state.title.isNotBlank() && state.content.isNotBlank() &&
                        !state.loading && !state.uploading,
                    onClick = viewModel::submit,
                )
                ContactPane.INQUIRY_DETAIL -> Unit
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.error != null) {
                Text(
                    state.error.orEmpty(),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.danger,
                    fontSize = 13.sp,
                )
            }
            when (state.pane) {
                ContactPane.INQUIRY_LIST -> InquiryList(state, viewModel)
                ContactPane.INQUIRY_CREATE -> InquiryForm(state, viewModel, context) {
                    fileLauncher.launch("image/*")
                }
                ContactPane.INQUIRY_DETAIL -> InquiryDetailBody(state, viewModel, context)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 헤더 오른쪽 글자 버튼. 쓸 수 없을 때는 눌리지 않고 색만 가라앉는다. */
@Composable
private fun HeaderAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        color = if (enabled) colors.brandDark else colors.border,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
    )
}

/* ---- 목록 ---- */

@Composable
private fun ColumnScope.InquiryList(state: ContactUiState, viewModel: ContactViewModel) {
    val colors = LocalKidhackColors.current
    Text(
        text = stringResource(R.string.parent_contact_title),
        modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 18.dp),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 25.sp,
        letterSpacing = (-1).sp,
        color = colors.text,
    )

    if (state.inquiries.isEmpty() && !state.loading) {
        Text(
            stringResource(R.string.parent_contact_empty),
            modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
            color = colors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }

    state.inquiries.forEachIndexed { index, inquiry ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openDetail(inquiry) }
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            // 상태를 제목 위로 올린다. 목록을 훑을 때 먼저 알고 싶은 것이 「답이 왔나」인데,
            // 오른쪽 끝에 두면 제목을 지나 눈이 되돌아온다.
            AnswerState(answered = inquiry.answered)
            Spacer(Modifier.height(7.dp))
            Text(
                inquiry.title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                letterSpacing = (-0.3).sp,
                color = colors.text,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
            Spacer(Modifier.height(6.dp))
            Text(inquiry.createdAt.toDisplayDate(), color = colors.subtle, fontSize = 11.5.sp, fontWeight = FontSemibold)
        }
        if (index != state.inquiries.lastIndex) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
        }
    }

    if (state.hasMore) {
        Text(
            text = stringResource(R.string.parent_contact_load_more),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::loadMore)
                .padding(vertical = 16.dp),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 답변 상태 — 점 하나와 글자. 알약보다 가벼워 제목을 누르지 않는다. */
@Composable
private fun AnswerState(answered: Boolean) {
    val colors = LocalKidhackColors.current
    val tint = if (answered) colors.safe else colors.subtle
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (answered) R.string.parent_contact_answered else R.string.parent_contact_waiting),
            color = tint,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.5.sp,
        )
    }
}

/* ---- 작성 ---- */

@Composable
private fun ColumnScope.InquiryForm(
    state: ContactUiState,
    viewModel: ContactViewModel,
    context: android.content.Context,
    onPickImage: () -> Unit,
) {
    val colors = LocalKidhackColors.current

    UnderlineField(
        label = stringResource(R.string.parent_contact_field_title),
        value = state.title,
        onValueChange = viewModel::onTitle,
        hint = stringResource(R.string.parent_contact_field_title_hint),
        fontSize = 16.sp,
        weight = FontBold,
        singleLine = true,
    )
    UnderlineField(
        label = stringResource(R.string.parent_contact_field_content),
        value = state.content,
        onValueChange = viewModel::onContent,
        hint = stringResource(R.string.parent_contact_field_content_hint),
        fontSize = 14.5.sp,
        weight = FontSemibold,
        singleLine = false,
        minHeight = 140.dp,
        underline = false,
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp), color = colors.line)

    FieldLabel(stringResource(R.string.parent_contact_field_photo))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.attachments.forEach { path ->
            Box(Modifier.size(72.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(RemoteUrl.absolute(path))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { viewModel.removeAttachment(path) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.parent_community_attach_remove),
                        modifier = Modifier.size(10.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface)
                .then(if (state.uploading) Modifier else Modifier.clickable(onClick = onPickImage)),
            contentAlignment = Alignment.Center,
        ) {
            if (state.uploading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = colors.brand, strokeWidth = 2.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = colors.subtle,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.parent_community_attach_photo),
                        color = colors.subtle,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }

    // 서버가 링크를 하나만 받는다. 여러 개를 담던 예전 UI 는 저장되지 않았다.
    UnderlineField(
        label = stringResource(R.string.parent_contact_field_link),
        value = state.linkUrl,
        onValueChange = viewModel::onLinkUrl,
        hint = "https://",
        fontSize = 14.5.sp,
        weight = FontSemibold,
        singleLine = true,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 9.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/**
 * 라벨 + 밑줄 입력칸.
 *
 * <p>[KidTextField] 는 사각 테두리를 두른다. 한 화면에 네 개를 쌓으면 서식이 내용보다
 * 커진다. 라벨은 위에 작게 두고 아래에 선 하나만 남긴다.
 */
@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    singleLine: Boolean,
    minHeight: Dp = 0.dp,
    underline: Boolean = true,
) {
    val colors = LocalKidhackColors.current
    FieldLabel(label)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .heightIn(min = minHeight),
        textStyle = TextStyle(
            fontFamily = KidhackFontFamily,
            fontWeight = weight,
            fontSize = fontSize,
            lineHeight = fontSize * 1.7f,
            color = colors.text,
            lineBreak = KidLineBreak,
        ),
        cursorBrush = SolidColor(colors.brand),
        singleLine = singleLine,
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(bottom = if (underline) 11.dp else 0.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            color = colors.border,
                            fontFamily = KidhackFontFamily,
                            fontWeight = weight,
                            fontSize = fontSize,
                        )
                    }
                    inner()
                }
                if (underline) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(if (value.isEmpty()) colors.border else colors.text),
                    )
                }
            }
        },
    )
}

/* ---- 상세 ---- */

@Composable
private fun ColumnScope.InquiryDetailBody(
    state: ContactUiState,
    viewModel: ContactViewModel,
    context: android.content.Context,
) {
    val colors = LocalKidhackColors.current
    val detail = state.detail
    if (detail == null) {
        Text(
            stringResource(if (state.loading) R.string.parent_contact_loading else R.string.parent_contact_load_failed),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            color = colors.muted,
            fontSize = 13.sp,
        )
        return
    }

    Box(Modifier.padding(start = 22.dp, top = 6.dp)) { AnswerState(answered = detail.answered) }
    Text(
        detail.title,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 21.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.7).sp,
        color = colors.text,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )
    // 기다리는 사람이 알고 싶은 것은 「얼마나 지났나」다. 「답변을 기다리는 중입니다」보다
    // 접수된 날짜가 쓸모 있다.
    Text(
        stringResource(R.string.parent_contact_received, detail.createdAt.toDisplayDate()),
        modifier = Modifier.padding(start = 22.dp, top = 9.dp),
        color = colors.subtle,
        fontSize = 11.5.sp,
        fontWeight = FontSemibold,
    )
    Text(
        detail.content,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp),
        color = colors.text,
        fontSize = 14.5.sp,
        lineHeight = 26.sp,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )

    if (detail.attachments.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            detail.attachments.forEach { path ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(RemoteUrl.absolute(path))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }

    if (!detail.linkUrl.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .clickable {
                    val url = RemoteUrl.absolute(detail.linkUrl.filterNot { ch -> ch.isWhitespace() })
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.muted,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                detail.linkUrl,
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // 답변이 달리면 서버가 수정·삭제를 막는다(400). 그때는 버튼을 감춘다.
    if (!detail.answered) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.parent_contact_edit),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::startEdit)
                    .padding(vertical = 4.dp),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
            Text(
                stringResource(R.string.parent_contact_delete),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::delete)
                    .padding(vertical = 4.dp),
                color = colors.danger,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
    } else {
        Spacer(Modifier.height(30.dp))
    }

    val answer = detail.answer
    if (answer != null) {
        // 내 문의는 그냥 글로, 답변만 옅은 바탕에 담는다. 카드 두 장을 쌓으면 누가 한
        // 말인지 제목을 읽어야 안다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.parent_contact_answer_from),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 12.5.sp,
                    color = colors.text,
                )
                Spacer(Modifier.width(8.dp))
                Text(answer.createdAt.toDisplayDate(), color = colors.subtle, fontSize = 11.sp, fontWeight = FontSemibold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                answer.content,
                color = colors.text,
                fontSize = 14.sp,
                lineHeight = 25.sp,
                style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
            )
        }
    }
}

/** 서버는 ISO-8601 로만 준다. 목록·상세 모두 날짜 부분만 보여 준다. */
private fun String.toDisplayDate(): String = take(10).replace('-', '.').ifBlank { this }
