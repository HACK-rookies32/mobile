package com.hack.kidhack.feature.parent

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
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
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildProfileUiState(
    val child: ChildProfile? = null,
    val name: String = "",
    val alias: String = "",
    val birthDate: String = "",
    val profileImageUrl: String? = null,
    val showPhotoOptions: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class ChildProfileViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildProfileUiState())
    val state: StateFlow<ChildProfileUiState> = _state.asStateFlow()

    init { load() }

    fun onAlias(v: String) = _state.update { it.copy(alias = v, saved = false) }
    
    fun togglePhotoOptions(show: Boolean) = _state.update { it.copy(showPhotoOptions = show) }

    fun onPhotoSelected(uri: Uri?) {
        // TODO: Backend Integration - 실제 서버 연동 시 이미지를 서버에 업로드하고 반환된 URL을 저장해야 함
        _state.update { it.copy(profileImageUrl = uri?.toString(), showPhotoOptions = false) }
    }

    fun onPhotoCaptured(bitmap: Bitmap?) {
        // TODO: Backend Integration - 캡처된 비트맵을 서버에 업로드
        _state.update { it.copy(showPhotoOptions = false) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching null
                childRepository.child(id)
            }.onSuccess { child ->
                if (child != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            child = child,
                            name = child.name,
                            alias = child.alias.orEmpty(),
                            birthDate = child.birthDate.orEmpty(),
                            profileImageUrl = child.profileImageUrl
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun save() {
        val child = _state.value.child ?: return
        viewModelScope.launch {
            runCatching {
                // 서버 ChildUpdateRequest 는 nickname·profileImageUrl 만 받는다.
                // 이름·생년월일을 같이 보내던 시절에는 서버가 조용히 버리는데도
                // 화면은 "저장되었습니다" 를 띄웠다.
                childRepository.updateChild(
                    childId = child.id,
                    nickname = _state.value.alias.trim().ifBlank { null },
                    profileImageUrl = _state.value.profileImageUrl,
                )
            }.onSuccess { updated ->
                _state.update {
                    it.copy(
                        child = updated,
                        name = updated.name,
                        alias = updated.alias.orEmpty(),
                        birthDate = updated.birthDate.orEmpty(),
                        profileImageUrl = updated.profileImageUrl,
                        saved = true,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentChildProfileScreen(
    onBack: () -> Unit = {},
    viewModel: ChildProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val child = state.child

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onPhotoSelected(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        viewModel.onPhotoCaptured(bitmap)
    }

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_profile_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading && child == null, state.error, viewModel::load)
        if (child != null) {
            KidCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(state.profileImageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_launcher_foreground)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(state.name.ifBlank { child.name }, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 20.sp, color = colors.text)
                    // 나이/학년 라벨은 서버가 주지 않는다. 별명이 있으면 그걸 보여 준다.
                    child.alias?.takeIf { it.isNotBlank() && it != child.name }?.let {
                        Text(it, color = colors.muted, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    KidButton(
                        text = stringResource(R.string.parent_profile_change_photo),
                        onClick = { viewModel.togglePhotoOptions(true) },
                        variant = KidButtonVariant.Secondary
                    )
                }
            }
            KidCard {
                Text(stringResource(R.string.parent_profile_basic), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                Spacer(Modifier.height(8.dp))
                // 서버가 수정 대상으로 받는 건 별명뿐이다. 이름·생년월일은 가입 정보라
                // 부모가 바꿀 수 없어 읽기 전용으로 보여 준다.
                KidTextField(
                    value = state.name,
                    onValueChange = {},
                    label = stringResource(R.string.parent_profile_name),
                    enabled = false,
                )
                KidTextField(state.alias, viewModel::onAlias, stringResource(R.string.parent_profile_alias))
                KidTextField(
                    value = state.birthDate,
                    onValueChange = {},
                    label = stringResource(R.string.parent_profile_birth),
                    enabled = false,
                )
            }
            if (state.saved) {
                Text("성공적으로 저장되었습니다", color = colors.safe, fontSize = 12.sp, fontWeight = FontBold)
            }
            KidButton(stringResource(R.string.parent_profile_save), viewModel::save, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.showPhotoOptions) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.togglePhotoOptions(false) },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    "프로필 사진 변경",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontBold,
                    fontSize = 18.sp
                )
                HorizontalDivider(color = colors.border)
                PhotoOptionRow("📸", "카메라로 촬영") { cameraLauncher.launch() }
                PhotoOptionRow("🖼️", "갤러리에서 선택") { galleryLauncher.launch("image/*") }
            }
        }
    }
}

@Composable
private fun PhotoOptionRow(icon: String, label: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(16.dp))
        Text(label, color = colors.text, fontWeight = FontSemibold, fontSize = 15.sp)
    }
}

