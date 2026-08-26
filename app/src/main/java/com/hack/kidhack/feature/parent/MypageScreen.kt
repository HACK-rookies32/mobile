package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.hack.kidhack.core.common.formatPhoneNumber
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.component.ListRow
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import com.hack.kidhack.domain.repository.SubscriptionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class PasswordError { EMPTY, MISMATCH }

data class MypageUiState(
    val user: User? = null,
    val subscription: Subscription? = null,
    val children: List<ChildProfile> = emptyList(),
    val selectedChildId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val changingPassword: Boolean = false,
    val newPassword: String = "",
    val confirmPassword: String = "",
    val passwordError: PasswordError? = null,
    val showPasswordConfirm: Boolean = false,
    val passwordChanged: Boolean = false,
)

class MypageViewModel(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MypageUiState())
    val state: StateFlow<MypageUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { id ->
                _state.update { it.copy(selectedChildId = id) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val user = userRepository.me()
                val sub = runCatching { subscriptionRepository.me() }.getOrNull()
                val children = childRepository.children()
                val selectedId = selectedChildRepository.resolveId(children)
                Triple(user, sub, children to selectedId)
            }.onSuccess { (user, sub, childInfo) ->
                _state.update {
                    it.copy(
                        loading = false,
                        user = user,
                        subscription = sub,
                        children = childInfo.first,
                        selectedChildId = childInfo.second,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun toggleEdit() = _state.update { it.copy(editing = !it.editing, message = null) }
    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onPhone(v: String) = _state.update { it.copy(phone = formatPhoneNumber(v)) }
    fun selectChild(id: String) {
        viewModelScope.launch { selectedChildRepository.select(id) }
    }

    fun togglePasswordChange() = _state.update {
        val opening = !it.changingPassword
        it.copy(
            changingPassword = opening,
            newPassword = "",
            confirmPassword = "",
            passwordError = null,
            passwordChanged = false,
        )
    }

    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, passwordError = null) }
    fun onConfirmPassword(v: String) = _state.update { it.copy(confirmPassword = v, passwordError = null) }

    fun requestPasswordChange() {
        val s = _state.value
        val error = when {
            s.newPassword.isBlank() -> PasswordError.EMPTY
            s.newPassword != s.confirmPassword -> PasswordError.MISMATCH
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(passwordError = error) }
        } else {
            _state.update { it.copy(showPasswordConfirm = true) }
        }
    }

    fun dismissPasswordConfirm() = _state.update { it.copy(showPasswordConfirm = false) }

    fun confirmPasswordChange() {
        val newPassword = _state.value.newPassword
        viewModelScope.launch {
            runCatching { userRepository.changePassword(newPassword) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            showPasswordConfirm = false,
                            changingPassword = false,
                            newPassword = "",
                            confirmPassword = "",
                            passwordError = null,
                            passwordChanged = true,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(showPasswordConfirm = false, error = t.message) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { runCatching { authRepository.logout() } }
    }
}

@Composable
fun ParentMypageScreen(
    onOpenEditProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAppControl: () -> Unit = {},
    onOpenScreenshot: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHarmful: () -> Unit = {},
    onOpenPairing: () -> Unit = {},
    onOpenCommunity: () -> Unit = {},
    onOpenNotice: () -> Unit = {},
    onOpenContact: () -> Unit = {},
    onOpenPay: () -> Unit = {},
    onOpenCards: () -> Unit = {},
    viewModel: MypageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val user = state.user
    val premium = state.subscription?.status.equals("ACTIVE", ignoreCase = true)

    ParentScreenScaffold(
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("👩", fontSize = 28.sp) }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        stringResource(R.string.parent_mypage_greeting, user?.name ?: ""),
                        color = Color.White,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 20.sp,
                    )
                    Text(user?.email ?: user?.loginId.orEmpty(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(
                        if (premium) stringResource(R.string.parent_mypage_premium) else stringResource(R.string.parent_mypage_basic),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 11.sp,
                    )
                }
            }
        },
    ) {
        ParentLoadingOrError(state.loading && user == null, state.error, viewModel::load)
        if (state.changingPassword) {
            KidCard {
                FormHeader(stringResource(R.string.parent_mypage_change_password), viewModel::togglePasswordChange)
                KidTextField(
                    state.newPassword,
                    viewModel::onNewPassword,
                    stringResource(R.string.parent_mypage_new_password),
                    placeholder = stringResource(R.string.parent_mypage_password_placeholder),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.passwordError == PasswordError.EMPTY,
                )
                Spacer(Modifier.height(8.dp))
                KidTextField(
                    state.confirmPassword,
                    viewModel::onConfirmPassword,
                    stringResource(R.string.parent_mypage_new_password_confirm),
                    placeholder = stringResource(R.string.parent_mypage_password_confirm_placeholder),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.passwordError == PasswordError.MISMATCH,
                )
                state.passwordError?.let { err ->
                    val msg = when (err) {
                        PasswordError.EMPTY -> stringResource(R.string.parent_mypage_password_error_empty)
                        PasswordError.MISMATCH -> stringResource(R.string.parent_mypage_password_error_mismatch)
                    }
                    Text(msg, color = colors.danger, fontSize = 12.sp, fontWeight = FontBold)
                }
                Spacer(Modifier.height(8.dp))
                KidButton(
                    stringResource(R.string.parent_mypage_password_change),
                    viewModel::requestPasswordChange,
                    Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.passwordChanged) {
            Text(stringResource(R.string.parent_mypage_password_changed), color = colors.safe, fontSize = 12.sp, fontWeight = FontBold)
        }
        if (state.showPasswordConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPasswordConfirm,
                title = { Text(stringResource(R.string.parent_mypage_password_confirm_title), fontWeight = FontBold) },
                text = { Text(stringResource(R.string.parent_mypage_password_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmPasswordChange) {
                        Text(stringResource(R.string.parent_mypage_password_confirm_ok), color = colors.brand, fontWeight = FontBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPasswordConfirm) {
                        Text(stringResource(R.string.parent_mypage_password_confirm_cancel), color = colors.muted)
                    }
                },
            )
        }
        GroupCard(stringResource(R.string.parent_mypage_account)) {
            ListRow(stringResource(R.string.parent_mypage_edit_profile), onClick = onOpenEditProfile)
            ListRow(stringResource(R.string.parent_mypage_change_password), subtitle = stringResource(R.string.parent_mypage_password_stub), onClick = viewModel::togglePasswordChange)
            ListRow(stringResource(R.string.parent_mypage_notif_settings), onClick = onOpenNotifications, showDivider = false)
        }

        GroupCard(stringResource(R.string.parent_mypage_child_manage)) {
            // 자녀 선택기 추가
            if (state.children.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.children.forEach { child ->
                        val selected = child.id == state.selectedChildId
                        Text(
                            text = child.name,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (selected) colors.brand else colors.surface)
                                .clickable { viewModel.selectChild(child.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (selected) Color.White else colors.text,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
            }

            ListRow(stringResource(R.string.parent_mypage_app_control), leading = "🛡️", onClick = onOpenAppControl)
            ListRow(stringResource(R.string.parent_mypage_screenshot), leading = "📸", onClick = onOpenScreenshot)
            ListRow(stringResource(R.string.parent_mypage_child_profile), leading = "👦", onClick = onOpenProfile)
            ListRow(stringResource(R.string.parent_mypage_harmful), leading = "🚫", onClick = onOpenHarmful)
            ListRow(stringResource(R.string.parent_mypage_pay), leading = "💳", onClick = onOpenPay, showDivider = false)
        }

        GroupCard(stringResource(R.string.parent_mypage_community_group)) {
            ListRow(stringResource(R.string.parent_mypage_community), leading = "👨‍👩‍👧", onClick = onOpenCommunity)
            ListRow(stringResource(R.string.parent_mypage_notice), leading = "📢", onClick = onOpenNotice)
            ListRow(stringResource(R.string.parent_mypage_contact), leading = "📮", onClick = onOpenContact, showDivider = false)
        }

        GroupCard(stringResource(R.string.parent_mypage_service)) {
            ListRow(stringResource(R.string.parent_mypage_pairing), leading = "🔗", onClick = onOpenPairing)
            ListRow(stringResource(R.string.parent_mypage_cards), leading = "🪪", onClick = onOpenCards, showDivider = false)
        }
        GroupCard(stringResource(R.string.parent_mypage_other)) {
            ListRow(stringResource(R.string.parent_mypage_terms), onClick = {})
            ListRow(stringResource(R.string.parent_mypage_version), trailing = "", onClick = null, showDivider = false)
        }
        KidButton(
            stringResource(R.string.parent_mypage_logout),
            viewModel::logout,
            Modifier.fillMaxWidth(),
            KidButtonVariant.Danger,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FormHeader(title: String, onClose: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Text(
            "✕",
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = colors.muted,
            fontSize = 16.sp,
            fontWeight = FontBold,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKidhackColors.current
    KidCard {
        Text(
            title.uppercase(),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
            color = colors.muted,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}
