package com.hack.kidhack.feature.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 확인 다이얼로그가 필요한 동작. */
enum class ChildAccountAction { FORCE_LOGOUT, UNPAIR }

data class ChildAccountUiState(
    val children: List<ChildProfile> = emptyList(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val pending: Pair<ChildAccountAction, ChildProfile>? = null,
)

/**
 * 자녀 계정 관리 — 강제 로그아웃 / 페어링 해제.
 *
 * 둘 다 **부모만** 할 수 있다(서버 `POST /children/{id}/logout`, `DELETE /children/{id}`).
 * 페어링 해제는 서버가 연결을 끊는 데서 그치지 않고 자녀 계정을 소프트 삭제까지 하므로
 * 되돌릴 수 없다 — 화면 문구와 확인 절차를 그 수준에 맞춘다.
 */
class ChildAccountViewModel(
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildAccountUiState())
    val state: StateFlow<ChildAccountUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { childRepository.children() }
                .onSuccess { list -> _state.update { it.copy(loading = false, children = list) } }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("자녀 목록을 불러오지 못했어요")) }
                }
        }
    }

    fun ask(action: ChildAccountAction, child: ChildProfile) =
        _state.update { it.copy(pending = action to child, info = null, error = null) }

    fun dismiss() = _state.update { it.copy(pending = null) }

    fun confirm() {
        val (action, child) = _state.value.pending ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true, pending = null, error = null, info = null) }
            runCatching {
                when (action) {
                    ChildAccountAction.FORCE_LOGOUT -> childRepository.forceLogout(child.id)
                    ChildAccountAction.UNPAIR -> childRepository.unpair(child.id)
                }
            }.onSuccess {
                _state.update { it.copy(working = false) }
                when (action) {
                    // 해제하면 목록에서 사라져야 하므로 다시 불러온다.
                    ChildAccountAction.UNPAIR -> load()
                    ChildAccountAction.FORCE_LOGOUT ->
                        _state.update { it.copy(info = "${child.name} 기기를 로그아웃시켰어요") }
                }
            }.onFailure { t ->
                _state.update { it.copy(working = false, error = t.userMessage("처리하지 못했어요")) }
            }
        }
    }
}

@Composable
fun ParentChildAccountScreen(
    onBack: () -> Unit = {},
    viewModel: ChildAccountViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_child_account_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading, state.error, viewModel::load)

        if (!state.loading && state.children.isEmpty()) {
            Text(stringResource(R.string.parent_child_account_empty), color = colors.muted, fontSize = 13.sp)
        }

        state.info?.let { Text(it, color = colors.safe, fontSize = 13.sp, fontWeight = FontBold) }

        state.children.forEach { child ->
            KidCard {
                Text(
                    child.name,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 16.sp,
                    color = colors.text,
                )
                child.alias?.takeIf { it.isNotBlank() && it != child.name }?.let {
                    Text(it, color = colors.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KidButton(
                        stringResource(R.string.parent_child_account_force_logout),
                        { viewModel.ask(ChildAccountAction.FORCE_LOGOUT, child) },
                        Modifier.weight(1f),
                        KidButtonVariant.Secondary,
                        enabled = !state.working,
                    )
                    KidButton(
                        stringResource(R.string.parent_child_account_unpair),
                        { viewModel.ask(ChildAccountAction.UNPAIR, child) },
                        Modifier.weight(1f),
                        KidButtonVariant.Danger,
                        enabled = !state.working,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.parent_child_account_notice), color = colors.muted, fontSize = 12.sp)
    }

    val pending = state.pending
    if (pending != null) {
        val (action, child) = pending
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = {
                Text(
                    stringResource(
                        when (action) {
                            ChildAccountAction.FORCE_LOGOUT -> R.string.parent_child_account_logout_confirm_title
                            ChildAccountAction.UNPAIR -> R.string.parent_child_account_unpair_confirm_title
                        },
                        child.name,
                    ),
                    fontWeight = FontBold,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            when (action) {
                                ChildAccountAction.FORCE_LOGOUT -> R.string.parent_child_account_logout_confirm_message
                                ChildAccountAction.UNPAIR -> R.string.parent_child_account_unpair_confirm_message
                            },
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirm) {
                    Text(
                        stringResource(R.string.parent_child_account_confirm_ok),
                        color = if (action == ChildAccountAction.UNPAIR) colors.danger else colors.brand,
                        fontWeight = FontBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) {
                    Text(stringResource(R.string.parent_child_account_confirm_cancel), color = colors.muted)
                }
            },
        )
    }
}
