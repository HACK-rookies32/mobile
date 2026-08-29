package com.hack.kidhack.feature.parent

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class EditProfileUiState(
    val user: User? = null,
    val nickname: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val showPhotoOptions: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class EditProfileViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init { load() }

    fun onNickname(v: String) = _state.update { it.copy(nickname = v, saved = false) }
    fun onName(v: String) = _state.update { it.copy(name = v, saved = false) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, saved = false) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, saved = false) }

    fun togglePhotoOptions(show: Boolean) = _state.update { it.copy(showPhotoOptions = show) }

    fun onPhotoSelected(uri: Uri?) {
        // TODO: Backend Integration - 실제 서버 연동 시 이미지를 업로드하고 반환된 URL을 저장해야 함
        _state.update { it.copy(profileImageUrl = uri?.toString(), showPhotoOptions = false, saved = false) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { userRepository.me() }
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            loading = false,
                            user = user,
                            nickname = user.nickname.orEmpty(),
                            name = user.name,
                            phone = user.phone,
                            email = user.email.orEmpty(),
                            profileImageUrl = user.profileImageUrl,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            runCatching {
                userRepository.updateMe(
                    nickname = s.nickname.trim().ifBlank { null },
                    phoneNumber = s.phone.trim(),
                    email = s.email.trim().ifBlank { null },
                    address = null,
                    profileImageUrl = s.profileImageUrl,
                )
            }.onSuccess { user ->
                _state.update {
                    it.copy(
                        user = user,
                        nickname = user.nickname.orEmpty(),
                        name = user.name,
                        phone = user.phone,
                        email = user.email.orEmpty(),
                        profileImageUrl = user.profileImageUrl,
                        saved = true,
                    )
                }
            }.onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentEditProfileScreen(
    onBack: () -> Unit = {},
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val user = state.user

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onPhotoSelected(uri)
    }

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_editprofile_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading && user == null, state.error, viewModel::load)
        if (user != null) {
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.nickname.ifBlank { state.name.ifBlank { user.name } },
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 20.sp,
                        color = colors.text,
                    )
                    Text(user.loginId, color = colors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    KidButton(
                        text = stringResource(R.string.parent_editprofile_change_photo),
                        onClick = { viewModel.togglePhotoOptions(true) },
                        variant = KidButtonVariant.Secondary,
                    )
                }
            }
            KidCard {
                Text(
                    stringResource(R.string.parent_editprofile_basic),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(8.dp))
                KidTextField(
                    state.nickname,
                    viewModel::onNickname,
                    stringResource(R.string.parent_editprofile_nickname),
                    placeholder = stringResource(R.string.parent_editprofile_nickname_placeholder),
                    supportingText = stringResource(R.string.parent_editprofile_nickname_hint),
                )
                KidTextField(state.name, viewModel::onName, stringResource(R.string.parent_editprofile_name))
                KidTextField(
                    state.phone,
                    viewModel::onPhone,
                    stringResource(R.string.parent_editprofile_phone),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                KidTextField(
                    state.email,
                    viewModel::onEmail,
                    stringResource(R.string.parent_editprofile_email),
                    placeholder = stringResource(R.string.parent_editprofile_email_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
            if (state.saved) {
                Text(
                    stringResource(R.string.parent_editprofile_saved),
                    color = colors.safe,
                    fontSize = 12.sp,
                    fontWeight = FontBold,
                )
            }
            KidButton(stringResource(R.string.parent_editprofile_save), viewModel::save, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.showPhotoOptions) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.togglePhotoOptions(false) },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    stringResource(R.string.parent_editprofile_change_photo),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontBold,
                    fontSize = 18.sp,
                )
                HorizontalDivider(color = colors.border)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { galleryLauncher.launch("image/*") }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🖼️", fontSize = 24.sp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.parent_editprofile_pick_gallery),
                        color = colors.text,
                        fontWeight = FontSemibold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
