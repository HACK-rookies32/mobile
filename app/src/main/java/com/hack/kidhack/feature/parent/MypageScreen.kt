package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.SubscriptionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class MypageUiState(
    val user: User? = null,
    val subscription: Subscription? = null,
    val editing: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val message: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class MypageViewModel(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MypageUiState())
    val state: StateFlow<MypageUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val user = userRepository.me()
                val sub = runCatching { subscriptionRepository.me() }.getOrNull()
                user to sub
            }.onSuccess { (user, sub) ->
                _state.update {
                    it.copy(loading = false, user = user, subscription = sub, name = user.name, phone = user.phone)
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun toggleEdit() = _state.update { it.copy(editing = !it.editing, message = null) }
    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onPhone(v: String) = _state.update { it.copy(phone = formatPhoneNumber(v)) }

    fun saveProfile() {
        viewModelScope.launch {
            runCatching { userRepository.updateMe(_state.value.name, _state.value.phone, null) }
                .onSuccess { user ->
                    _state.update { it.copy(user = user, editing = false, message = "ok") }
                }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun logout() {
        viewModelScope.launch { runCatching { authRepository.logout() } }
    }
}

@Composable
fun ParentMypageScreen(
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
        if (state.editing) {
            KidCard {
                KidTextField(state.name, viewModel::onName, stringResource(R.string.parent_mypage_name))
                KidTextField(state.phone, viewModel::onPhone, stringResource(R.string.parent_mypage_phone))
                KidButton(stringResource(R.string.parent_mypage_save), viewModel::saveProfile, Modifier.fillMaxWidth())
            }
        }
        if (state.message == "ok") {
            Text(stringResource(R.string.parent_mypage_saved), color = colors.safe, fontSize = 12.sp, fontWeight = FontBold)
        }
        GroupCard(stringResource(R.string.parent_mypage_account)) {
            ListRow(stringResource(R.string.parent_mypage_edit_profile), onClick = viewModel::toggleEdit)
            ListRow(stringResource(R.string.parent_mypage_change_password), subtitle = stringResource(R.string.parent_mypage_password_stub), onClick = null)
            ListRow(stringResource(R.string.parent_mypage_notif_settings), onClick = onOpenNotifications, showDivider = false)
        }
        GroupCard(stringResource(R.string.parent_mypage_child_manage)) {
            ListRow(stringResource(R.string.parent_mypage_app_control), leading = "🛡️", onClick = onOpenAppControl)
            ListRow(stringResource(R.string.parent_mypage_screenshot), leading = "📸", onClick = onOpenScreenshot)
            ListRow(stringResource(R.string.parent_mypage_child_profile), leading = "👦", onClick = onOpenProfile)
            ListRow(stringResource(R.string.parent_mypage_harmful), leading = "🚫", onClick = onOpenHarmful)
            ListRow(stringResource(R.string.parent_mypage_pairing), leading = "🔗", onClick = onOpenPairing, showDivider = false)
        }
        GroupCard(stringResource(R.string.parent_mypage_community_group)) {
            ListRow(stringResource(R.string.parent_mypage_community), leading = "👨‍👩‍👧", onClick = onOpenCommunity)
            ListRow(stringResource(R.string.parent_mypage_notice), leading = "📢", onClick = onOpenNotice)
            ListRow(stringResource(R.string.parent_mypage_contact), leading = "📮", onClick = onOpenContact, showDivider = false)
        }
        GroupCard(stringResource(R.string.parent_mypage_service)) {
            ListRow(stringResource(R.string.parent_mypage_pay), leading = "💳", onClick = onOpenPay)
            ListRow(stringResource(R.string.parent_mypage_payments), leading = "🧾", onClick = onOpenPay, showDivider = false)
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
