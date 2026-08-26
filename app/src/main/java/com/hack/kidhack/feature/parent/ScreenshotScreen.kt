package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ScreenshotUiState(
    val items: List<Screenshot> = emptyList(),
    val selectedShot: Screenshot? = null,
    val requesting: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class ScreenshotViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val screenshotRepository: ScreenshotRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state.asStateFlow()

    init { load() }

    fun selectShot(shot: Screenshot?) = _state.update { it.copy(selectedShot = shot) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching emptyList()
                screenshotRepository.list(id)
            }.onSuccess { items ->
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun request() {
        viewModelScope.launch {
            val lastId = _state.value.items.firstOrNull()?.id
            _state.update { it.copy(requesting = true, error = null) }
            runCatching {
                val id = selectedChildRepository.current()
                    ?: selectedChildRepository.resolveId(childRepository.children())
                    ?: error("자녀를 선택해 주세요")
                
                // TODO: Backend Integration - 202 Accepted 응답 후 FCM/Socket 알림이 없으므로 폴링 수행 (GAP-11)
                screenshotRepository.requestCapture(id)
                
                // 최대 2분간 5초 간격으로 폴링
                var found = false
                for (i in 1..24) {
                    delay(5000)
                    val items = screenshotRepository.list(id)
                    if (items.firstOrNull()?.id != lastId) {
                        _state.update { it.copy(requesting = false, items = items) }
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

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_screenshot_title), onBack) },
    ) {
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
        Text(stringResource(R.string.parent_screenshot_recent), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
        
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)

        if (!state.loading && state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.parent_screenshot_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontSize = 14.sp
                )
            }
        }

        state.items.forEach { shot ->
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shot.description, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Text(shot.capturedAt, color = colors.muted, fontSize = 12.sp)
                    }
                    KidButton(
                        text = stringResource(R.string.parent_screenshot_view),
                        onClick = { viewModel.selectShot(shot) },
                        variant = KidButtonVariant.Ghost
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
