package com.hack.kidhack.feature.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
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
    val comments: List<String> = emptyList(),
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
                .onSuccess { post -> _state.update { it.copy(loading = false, post = post) } }
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
                    _state.update {
                        it.copy(
                            draft = "",
                            comments = it.comments + text,
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

    fun submit() {
        val title = _state.value.title.trim()
        val body = _state.value.body.trim()
        if (title.isBlank() || body.isBlank()) {
            _state.update { it.copy(error = "제목과 내용을 입력해 주세요") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { communityRepository.create(title, body) }
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
                    Text(c, fontSize = 14.sp, color = colors.text)
                }
            }
        }
    }
}

@Composable
fun ParentCommunityWriteScreen(
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: CommunityWriteViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }
    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_community_write), onBack) },
    ) {
        ParentLoadingOrError(state.loading, state.error)
        KidTextField(state.title, viewModel::onTitle, stringResource(R.string.parent_community_write_title))
        KidTextField(state.body, viewModel::onBody, stringResource(R.string.parent_community_write_body))
        KidButton(stringResource(R.string.parent_community_write_submit), viewModel::submit, Modifier.fillMaxWidth(), enabled = !state.loading)
        Spacer(Modifier.height(8.dp))
    }
}
