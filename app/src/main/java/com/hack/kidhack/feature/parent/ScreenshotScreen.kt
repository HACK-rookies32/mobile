package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
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
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ScreenshotUiState(
    val selectedShot: Screenshot? = null,
    val requesting: Boolean = false,
    val error: String? = null,
)

class ScreenshotViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val screenshotRepository: ScreenshotRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state.asStateFlow()

    // 캡처 요청으로 새 항목이 감지되면 UI 가 paging 목록을 refresh 하도록 알리는 1회성 신호.
    private val _refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSignal: SharedFlow<Unit> = _refreshSignal.asSharedFlow()

    // 선택된 자녀가 바뀔 때마다 그 자녀의 캡처 목록으로 Pager 를 다시 만든다.
    @OptIn(ExperimentalCoroutinesApi::class)
    val screenshots: Flow<PagingData<Screenshot>> =
        selectedChildRepository.selectedChildId
            .flatMapLatest { selected ->
                val id = selected
                    ?: runCatching { selectedChildRepository.resolveId(childRepository.children()) }.getOrNull()
                if (id == null) flowOf(PagingData.empty<Screenshot>()) else screenshotRepository.paged(id)
            }
            .cachedIn(viewModelScope)

    fun selectShot(shot: Screenshot?) = _state.update { it.copy(selectedShot = shot) }

    fun request() {
        viewModelScope.launch {
            _state.update { it.copy(requesting = true, error = null) }
            runCatching {
                val id = selectedChildRepository.current()
                    ?: selectedChildRepository.resolveId(childRepository.children())
                    ?: error("자녀를 선택해 주세요")

                // 폴링 비교 기준(현재 첫 항목). paged() 는 첫 페이지만 다시 엿보는 API 가 마땅치 않아
                // 기존 non-paged list() 로 새 캡처 도착만 감지하고, 목록 자체는 items.refresh() 로 갱신한다.
                val lastId = runCatching { screenshotRepository.list(id).firstOrNull()?.id }.getOrNull()

                // TODO: Backend Integration - 202 Accepted 응답 후 FCM/Socket 알림이 없으므로 폴링 수행 (GAP-11)
                screenshotRepository.requestCapture(id)

                // 최대 2분간 5초 간격으로 폴링
                var found = false
                for (i in 1..24) {
                    delay(5000)
                    val firstId = runCatching { screenshotRepository.list(id).firstOrNull()?.id }.getOrNull()
                    if (firstId != lastId) {
                        _state.update { it.copy(requesting = false) }
                        _refreshSignal.tryEmit(Unit)
                        found = true
                        break
                    }
                }
                if (!found) error("캡처 응답 시간이 초과되었습니다. 잠시 후 목록을 확인해 주세요.")
            }.onFailure { t ->
                _state.update { it.copy(requesting = false, error = t.message) }
            }
        }
    }
}

@Composable
fun ParentScreenshotScreen(
    onBack: () -> Unit = {},
    viewModel: ScreenshotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val items = viewModel.screenshots.collectAsLazyPagingItems()

    // 캡처 요청으로 새 항목이 감지되면 paging 목록만 새로고침한다.
    LaunchedEffect(Unit) {
        viewModel.refreshSignal.collect { items.refresh() }
    }

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_screenshot_title), onBack) },
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 캡처 요청 카드(고정 헤더)
            item {
                KidCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.parent_screenshot_live), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                        Text(stringResource(R.string.parent_screenshot_desc), color = colors.muted, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                        if (state.requesting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = colors.brand, strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.parent_screenshot_requesting), color = colors.brand, fontWeight = FontBold)
                            }
                        } else {
                            KidButton(stringResource(R.string.parent_screenshot_request), viewModel::request)
                        }
                    }
                }
            }

            // 요청 실패 메시지(폴링/캡처 요청 오류)
            if (state.error != null) {
                item { ParentLoadingOrError(false, state.error) }
            }

            item {
                Text(stringResource(R.string.parent_screenshot_recent), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            }

            // 목록 첫 로딩 / 에러 / 빈 상태
            val refresh = items.loadState.refresh
            when {
                refresh is LoadState.Loading && items.itemCount == 0 ->
                    item { ParentLoadingOrError(true, null) }
                refresh is LoadState.Error ->
                    item { ParentLoadingOrError(false, refresh.error.message ?: "", onRetry = { items.retry() }) }
                refresh is LoadState.NotLoading && items.itemCount == 0 ->
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_screenshot_empty),
                                color = colors.muted,
                                fontFamily = KidhackFontFamily,
                                fontSize = 14.sp,
                            )
                        }
                    }
            }

            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
            ) { index ->
                items[index]?.let { shot -> ScreenshotRow(shot, onView = { viewModel.selectShot(shot) }) }
            }

            // 다음 페이지 로딩/에러(맨 아래)
            when (items.loadState.append) {
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
                            KidButton(stringResource(R.string.parent_retry), { items.retry() }, Modifier.fillMaxWidth())
                        }
                    }
                else -> Unit
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // 상세 보기 다이얼로그
    state.selectedShot?.let { shot ->
        Dialog(
            onDismissRequest = { viewModel.selectShot(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { viewModel.selectShot(null) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(shot.imageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_launcher_foreground)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(shot.description, color = Color.White, fontWeight = FontBold, fontSize = 16.sp)
                    Text(shot.capturedAt, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    KidButton("닫기", { viewModel.selectShot(null) }, variant = KidButtonVariant.Secondary)
                }
            }
        }
    }
}

@Composable
private fun ScreenshotRow(shot: Screenshot, onView: () -> Unit) {
    val colors = LocalKidhackColors.current
    KidCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(shot.imageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_launcher_foreground)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(shot.description, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                Text(shot.capturedAt, color = colors.muted, fontSize = 12.sp)
            }
            KidButton(
                text = stringResource(R.string.parent_screenshot_view),
                onClick = onView,
                variant = KidButtonVariant.Ghost,
            )
        }
    }
}
