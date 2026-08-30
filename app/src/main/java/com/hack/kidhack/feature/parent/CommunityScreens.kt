package com.hack.kidhack.feature.parent

import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class CommunityListViewModel(
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    // 게시글 목록은 Paging3 로 노출한다(loading/error 는 화면의 loadState 에서 파생).
    val posts: Flow<PagingData<Post>> =
        communityRepository.pagedPosts().cachedIn(viewModelScope)

    /** 목록 화면이 한 번이라도 그려졌는지. 글쓰기·상세에서 돌아오면 목록을 다시 읽기 위한 표시. */
    var shownOnce = false
}

data class CommunityDetailUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val draft: String = "",
    /** 내 회원 id. 내 글·내 댓글에만 삭제 버튼을 보인다(남의 것은 서버가 막는다). */
    val myMemberId: String = "",
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val error: String? = null,
)

/**
 * 게시글 상세 (명세 20).
 *
 * 신고 기능은 서버에 없다. 예전에는 `/report` 를 부르며 404 를 받고도 "신고 접수" 를
 * 띄우고 있었다 — 화면과 함께 걷어냈다.
 */
class CommunityDetailViewModel(
    private val postId: String,
    private val communityRepository: CommunityRepository,
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityDetailUiState())
    val state: StateFlow<CommunityDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            runCatching { userRepository.me() }.getOrNull()?.let { user ->
                _state.update { it.copy(myMemberId = user.id) }
            }
        }
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.post(postId) }
                .onSuccess { post -> _state.update { it.copy(loading = false, post = post, comments = post.comments) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    /** 서버가 토글 결과를 준다. 예전에는 글을 다시 읽고 앱이 카운트를 ±1 했다. */
    fun like() {
        viewModelScope.launch {
            runCatching { communityRepository.like(postId) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(post = it.post?.copy(liked = result.liked, likeCount = result.likeCount))
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    /** 서버가 돌려준 댓글을 그대로 붙인다. 예전에는 가짜 댓글을 만들어 넣었다. */
    fun comment() {
        val text = _state.value.draft.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { communityRepository.comment(postId, text) }
                .onSuccess { created ->
                    _state.update { it.copy(draft = "", comments = it.comments + created) }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            runCatching { communityRepository.deleteComment(postId, commentId) }
                .onSuccess {
                    _state.update { it.copy(comments = it.comments.filterNot { c -> c.id == commentId }) }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun deletePost() {
        viewModelScope.launch {
            runCatching { communityRepository.delete(postId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

data class CommunityWriteUiState(
    /** 수정 모드. 제목·버튼 문구와 호출할 API 가 달라진다. */
    val editing: Boolean = false,
    val title: String = "",
    val content: String = "",
    /** 업로드를 마친 첨부의 **공개 경로**. 갤러리 URI 가 아니다. */
    val attachments: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val showLinkDialog: Boolean = false,
    val uploading: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * 글쓰기 (명세 20).
 *
 * 첨부는 `POST /community/attachments` 로 **먼저 올려** 공개 경로를 받고 그 경로만
 * 글에 실어 보낸다. 예전에는 갤러리 `content://` URI 를 그대로 보냈다(서버에 없는 파일).
 */
class CommunityWriteViewModel(
    private val communityRepository: CommunityRepository,
    /** 수정할 글 id. 비어 있으면 새 글이다. */
    private val postId: String = "",
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityWriteUiState(editing = postId.isNotBlank()))
    val state: StateFlow<CommunityWriteUiState> = _state.asStateFlow()

    init {
        if (postId.isNotBlank()) loadForEdit()
    }

    /** 목록에는 본문·첨부가 없어서 수정 폼을 채우려면 상세를 받아와야 한다. */
    private fun loadForEdit() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.post(postId) }
                .onSuccess { post ->
                    _state.update {
                        it.copy(
                            loading = false,
                            title = post.title,
                            content = post.content,
                            attachments = post.attachments,
                            links = post.links,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v) }

    fun addAttachment(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            runCatching { communityRepository.uploadAttachment(uri.toString()) }
                .onSuccess { url -> _state.update { it.copy(attachments = it.attachments + url, uploading = false) } }
                .onFailure { t ->
                    _state.update { it.copy(uploading = false, error = t.message ?: "첨부 업로드에 실패했어요") }
                }
        }
    }

    fun removeAttachment(url: String) = _state.update { it.copy(attachments = it.attachments - url) }

    fun toggleLinkDialog(show: Boolean) = _state.update { it.copy(showLinkDialog = show) }

    fun addLink(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(links = it.links + url, showLinkDialog = false) }
    }

    fun removeLink(url: String) = _state.update { it.copy(links = it.links - url) }

    fun submit() {
        val title = _state.value.title.trim()
        val content = _state.value.content.trim()
        if (title.isBlank() || content.isBlank()) {
            _state.update { it.copy(error = "제목과 내용을 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                if (postId.isBlank()) {
                    communityRepository.create(
                        title = title,
                        content = content,
                        attachments = _state.value.attachments,
                        links = _state.value.links,
                    )
                } else {
                    communityRepository.update(
                        postId = postId,
                        title = title,
                        content = content,
                        attachments = _state.value.attachments,
                        links = _state.value.links,
                    )
                }
            }
                .onSuccess { _state.update { it.copy(loading = false, done = true) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }
}

@Composable
fun ParentCommunityScreen(
    onOpenPost: (String) -> Unit = {},
    onWrite: () -> Unit = {},
    viewModel: CommunityListViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    val posts = viewModel.posts.collectAsLazyPagingItems()
    // 글 작성·상세에서 돌아왔을 때 목록을 다시 읽는다(최초 진입은 페이징이 알아서 읽는다).
    LaunchedEffect(Unit) {
        if (viewModel.shownOnce) posts.refresh()
        viewModel.shownOnce = true
    }

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_community_title)) {
                Text(
                    "✏️",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onWrite)
                        .padding(8.dp),
                    fontSize = 18.sp,
                )
            }
        },
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        // 글쓰기 버튼은 목록 위에 고정 — 스크롤해도 항상 보인다.
        KidButton(
            stringResource(R.string.parent_community_write),
            onWrite,
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 첫 로딩 / 에러 (기존 ParentLoadingOrError 스타일 유지)
            val refresh = posts.loadState.refresh
            when {
                refresh is LoadState.Loading && posts.itemCount == 0 ->
                    item { ParentLoadingOrError(true, null) }
                refresh is LoadState.Error ->
                    item { ParentLoadingOrError(false, refresh.error.message ?: "", onRetry = { posts.retry() }) }
            }

            items(
                count = posts.itemCount,
                key = posts.itemKey { it.id },
            ) { index ->
                posts[index]?.let { post ->
                    KidCard(Modifier.clickable { onOpenPost(post.id) }) {
                        Text(post.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(post.author, fontFamily = KidhackFontFamily, fontWeight = FontSemibold, fontSize = 12.sp, color = colors.text)
                            Text(post.createdAtLabel.toDisplayDateTime(), color = colors.muted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            // 댓글 수는 서버 목록 응답에 없다. 좋아요만 보여 준다.
                            Text(stringResource(R.string.parent_community_likes, post.likeCount), color = colors.muted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 다음 페이지 로딩/에러(맨 아래)
            when (posts.loadState.append) {
                is LoadState.Loading ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.brand, strokeWidth = 2.dp)
                        }
                    }
                is LoadState.Error ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("더 불러오지 못했어요", color = colors.danger, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 13.sp)
                            KidButton(stringResource(R.string.parent_retry), { posts.retry() }, Modifier.fillMaxWidth())
                        }
                    }
                else -> Unit
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentCommunityDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    viewModel: CommunityDetailViewModel = koinViewModel(parameters = { parametersOf(postId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val post = state.post

    // 글이 지워졌으면 상세에 머물 이유가 없다.
    if (state.deleted) {
        LaunchedEffect(Unit) { onBack() }
    }

    // 수정 화면에서 돌아오면 뷰모델이 그대로 살아 있어 옛 내용이 남는다. 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_community_detail), onBack) },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraft,
                    modifier = Modifier
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(0.dp)
                        .then(
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        )
                        .padding(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = KidhackFontFamily, fontSize = 14.sp, color = colors.text),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .then(Modifier)
                                .padding(0.dp),
                        ) {
                            if (state.draft.isEmpty()) {
                                Text(stringResource(R.string.parent_community_comment_hint), color = colors.muted, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
                KidButton(stringResource(R.string.parent_community_comment_send), viewModel::comment)
            }
        },
    ) {
        ParentLoadingOrError(state.loading && post == null, state.error, viewModel::load)
        if (post != null) {
            KidCard {
                Text(post.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    post.author + " · " + post.createdAtLabel.toDisplayDateTime(),
                    color = colors.muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                // 예전에는 서버 본문을 받지 못해 자리표시 문자열을 그리고 있었다.
                Text(post.content, color = colors.text, fontSize = 14.sp)

                // 첨부 표시 — 상대 경로라 origin 을 붙여야 뜬다.
                if (post.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(post.attachments) { path ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(RemoteUrl.absolute(path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                // 링크 표시
                if (post.links.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    post.links.forEach { link ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .clickable { context.openExternal(link) }
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

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.parent_community_like) + " ${post.likeCount}",
                        modifier = Modifier.clickable(onClick = viewModel::like),
                        color = if (post.liked) colors.danger else colors.muted,
                        fontWeight = FontBold,
                    )
                    Text(stringResource(R.string.parent_community_comments, state.comments.size), color = colors.muted)
                    Spacer(Modifier.weight(1f))
                    // 서버가 작성자를 검증하지 않지만(VULN-11), 남의 글에 삭제를 노출하지는 않는다.
                    if (post.authorId.isNotBlank() && post.authorId == state.myMemberId) {
                        Text(
                            stringResource(R.string.parent_community_edit),
                            modifier = Modifier.clickable { onEdit(post.id) },
                            color = colors.brand,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.parent_community_delete),
                            modifier = Modifier.clickable(onClick = viewModel::deletePost),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            state.comments.forEach { c ->
                KidCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = c.author,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 13.sp,
                                color = colors.text
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = c.createdAt.toDisplayDateTime(),
                                color = colors.muted,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.weight(1f))
                            // 본인 댓글만 지울 수 있다(남의 댓글이면 서버가 403).
                            if (c.memberId.isNotBlank() && c.memberId == state.myMemberId) {
                                Text(
                                    text = stringResource(R.string.parent_community_delete),
                                    modifier = Modifier.clickable { viewModel.deleteComment(c.id) },
                                    color = colors.danger,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(c.text, fontSize = 14.sp, color = colors.text)
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentCommunityWriteScreen(
    /** 비어 있으면 새 글, 값이 있으면 그 글을 수정한다. */
    postId: String = "",
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: CommunityWriteViewModel = koinViewModel(parameters = { parametersOf(postId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var linkInput by remember { mutableStateOf("") }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addAttachment(uri)
    }

    if (state.done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }
    ParentScreenScaffold(
        header = {
            ParentHeaderBar(
                stringResource(
                    if (state.editing) R.string.parent_community_edit
                    else R.string.parent_community_write
                ),
                onBack,
            )
        },
    ) {
        ParentLoadingOrError(state.loading, state.error)
        KidTextField(state.title, viewModel::onTitle, stringResource(R.string.parent_community_write_title))
        
        // 본문 입력창 높이 확대
        KidTextField(
            value = state.content,
            onValueChange = viewModel::onContent,
            label = stringResource(R.string.parent_community_write_body),
            singleLine = false,
            minLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // 이미지 첨부 영역
        Text("이미지 첨부", fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
        if (state.uploading) {
            Text("올리는 중…", color = colors.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .clickable(enabled = !state.uploading) { imageLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("➕", fontSize = 20.sp)
                }
            }
            items(state.attachments) { path ->
                Box(Modifier.size(80.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(RemoteUrl.absolute(path))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { viewModel.removeAttachment(path) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 링크 첨부 영역
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("링크 첨부", Modifier.weight(1f), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Text(
                "추가하기",
                Modifier.clickable { viewModel.toggleLinkDialog(true) },
                color = colors.brand,
                fontWeight = FontBold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        state.links.forEach { link ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔗", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(link, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, color = colors.text)
                Text(
                    "삭제",
                    Modifier.clickable { viewModel.removeLink(link) },
                    color = colors.danger,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(32.dp))
        KidButton(
            stringResource(
                if (state.editing) R.string.parent_community_edit_submit
                else R.string.parent_community_write_submit
            ),
            viewModel::submit,
            Modifier.fillMaxWidth(),
            enabled = !state.loading && !state.uploading,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (state.showLinkDialog) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleLinkDialog(false) },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("링크 추가", fontWeight = FontBold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                KidTextField(linkInput, { linkInput = it }, "URL", placeholder = "https://...")
                Spacer(Modifier.height(24.dp))
                KidButton("추가", {
                    viewModel.addLink(linkInput)
                    linkInput = ""
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** 서버는 ISO-8601 로만 준다. 목록·상세 모두 분 단위까지만 보여 준다. */
private fun String.toDisplayDateTime(): String =
    if (length >= 16) substring(0, 10).replace('-', '.') + " " + substring(11, 16) else this

/**
 * 게시글 링크·첨부를 외부 브라우저로 연다.
 *
 * 첨부는 상대 경로라 origin 을 붙인다. 브라우저가 없는 기기에서는 예외가 나므로 삼킨다.
 */
private fun Context.openExternal(rawUrl: String) {
    val url = RemoteUrl.absolute(rawUrl.filterNot { it.isWhitespace() })
    if (url.isBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
