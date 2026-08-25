package com.hack.kidhack.feature.parent

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

data class CommunityListUiState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class CommunityListViewModel(
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityListUiState())
    val state: StateFlow<CommunityListUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.posts() }
                .onSuccess { posts -> _state.update { it.copy(loading = false, posts = posts) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }
}

data class CommunityDetailUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class CommunityDetailViewModel(
    private val postId: String,
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityDetailUiState())
    val state: StateFlow<CommunityDetailUiState> = _state.asStateFlow()

    init { load() }

    fun onDraft(v: String) = _state.update { it.copy(draft = v) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.post(postId) }
                .onSuccess { post -> _state.update { it.copy(loading = false, post = post, comments = post.comments) } }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun like() {
        viewModelScope.launch {
            runCatching {
                communityRepository.like(postId)
                communityRepository.post(postId)
            }.onSuccess { post ->
                val current = _state.value.post
                val liked = current?.let { !it.liked } ?: true
                val count = (current?.likeCount ?: 0) + if (liked) 1 else -1
                _state.update { it.copy(post = (post).copy(liked = liked, likeCount = count.coerceAtLeast(0))) }
            }
        }
    }

    fun comment() {
        val text = _state.value.draft.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { communityRepository.comment(postId, text) }
                .onSuccess {
                    val newComment = Comment(
                        id = "local-${System.currentTimeMillis()}",
                        author = "나", // TODO: 실제 사용자 이름으로 연동
                        text = text,
                        createdAt = "방금"
                    )
                    _state.update {
                        it.copy(
                            draft = "",
                            comments = it.comments + newComment,
                            post = it.post?.copy(commentCount = it.post.commentCount + 1),
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

data class CommunityWriteUiState(
    val title: String = "",
    val body: String = "",
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val showLinkDialog: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

class CommunityWriteViewModel(
    private val communityRepository: CommunityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityWriteUiState())
    val state: StateFlow<CommunityWriteUiState> = _state.asStateFlow()

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }

    fun addImage(uri: Uri?) {
        if (uri == null) return
        // TODO: Backend Integration - 실제 서버 연동 시 이미지를 업로드하고 반환된 URL을 사용해야 함
        _state.update { it.copy(imageUrls = it.imageUrls + uri.toString()) }
    }

    fun removeImage(url: String) {
        _state.update { it.copy(imageUrls = it.imageUrls - url) }
    }

    fun toggleLinkDialog(show: Boolean) = _state.update { it.copy(showLinkDialog = show) }

    fun addLink(url: String) {
        if (url.isBlank()) return
        _state.update { it.copy(links = it.links + url, showLinkDialog = false) }
    }

    fun removeLink(url: String) {
        _state.update { it.copy(links = it.links - url) }
    }

    fun submit() {
        val title = _state.value.title.trim()
        val body = _state.value.body.trim()
        if (title.isBlank() || body.isBlank()) {
            _state.update { it.copy(error = "제목과 내용을 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                communityRepository.create(
                    title = title,
                    body = body,
                    imageUrls = _state.value.imageUrls,
                    links = _state.value.links
                )
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

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
    ) {
        ParentLoadingOrError(state.loading, state.error, viewModel::load)
        state.posts.forEach { post ->
            KidCard(Modifier.clickable { onOpenPost(post.id) }) {
                Text(post.title, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(post.author, fontFamily = KidhackFontFamily, fontWeight = FontSemibold, fontSize = 12.sp, color = colors.text)
                    Text(post.createdAtLabel, color = colors.muted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.parent_community_comments, post.commentCount), color = colors.muted, fontSize = 12.sp)
                    Text(stringResource(R.string.parent_community_likes, post.likeCount), color = colors.muted, fontSize = 12.sp)
                }
            }
        }
        KidButton(stringResource(R.string.parent_community_write), onWrite, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ParentCommunityDetailScreen(
    postId: String,
    onBack: () -> Unit = {},
    viewModel: CommunityDetailViewModel = koinViewModel(parameters = { parametersOf(postId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val post = state.post

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
                Text("${post.author} · ${post.createdAtLabel}", color = colors.muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.parent_community_empty_body), color = colors.text, fontSize = 14.sp)
                
                // 이미지 표시
                if (post.imageUrls.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(post.imageUrls) { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
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
                                .clickable { /* TODO: Open browser */ }
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.parent_community_like) + " ${post.likeCount}",
                        modifier = Modifier.clickable(onClick = viewModel::like),
                        color = if (post.liked) colors.danger else colors.muted,
                        fontWeight = FontBold,
                    )
                    Text(stringResource(R.string.parent_community_comments, post.commentCount), color = colors.muted)
                }
            }
            state.comments.forEach { c ->
                KidCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = c.author,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 13.sp,
                                color = colors.text
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = c.createdAt,
                                color = colors.muted,
                                fontSize = 11.sp
                            )
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
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: CommunityWriteViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    var linkInput by remember { mutableStateOf("") }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.addImage(uri)
    }

    if (state.done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }
    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_community_write), onBack) },
    ) {
        ParentLoadingOrError(state.loading, state.error)
        KidTextField(state.title, viewModel::onTitle, stringResource(R.string.parent_community_write_title))
        
        // 본문 입력창 높이 확대
        KidTextField(
            value = state.body,
            onValueChange = viewModel::onBody,
            label = stringResource(R.string.parent_community_write_body),
            singleLine = false,
            minLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // 이미지 첨부 영역
        Text("이미지 첨부", fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .clickable { imageLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("➕", fontSize = 20.sp)
                }
            }
            items(state.imageUrls) { url ->
                Box(Modifier.size(80.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
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
                            .clickable { viewModel.removeImage(url) },
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
        KidButton(stringResource(R.string.parent_community_write_submit), viewModel::submit, Modifier.fillMaxWidth(), enabled = !state.loading)
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
