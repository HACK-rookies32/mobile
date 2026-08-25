package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
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
    val photoMessage: String? = null,
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

    fun onName(v: String) = _state.update { it.copy(name = v, saved = false) }
    fun onAlias(v: String) = _state.update { it.copy(alias = v, saved = false) }
    fun onPhoto() = _state.update { it.copy(photoMessage = "stub") }

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
                childRepository.updateChild(child.id, _state.value.name.trim(), _state.value.alias.trim().ifBlank { null }, null)
            }.onSuccess { updated ->
                _state.update { it.copy(child = updated, name = updated.name, alias = updated.alias.orEmpty(), saved = true) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }
}

@Composable
fun ParentChildProfileScreen(
    onBack: () -> Unit = {},
    viewModel: ChildProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val child = state.child

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
                    ) { Text("👦", fontSize = 36.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text(state.name.ifBlank { child.name }, fontFamily = KidhackFontFamily, fontWeight = FontBlack, fontSize = 20.sp, color = colors.text)
                    Text(child.ageLabel.orEmpty(), color = colors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    KidButton(stringResource(R.string.parent_profile_change_photo), viewModel::onPhoto, variant = KidButtonVariant.Secondary)
                    if (state.photoMessage != null) {
                        Text(stringResource(R.string.parent_profile_photo_stub), color = colors.muted, fontSize = 12.sp)
                    }
                }
            }
            KidCard {
                Text(stringResource(R.string.parent_profile_basic), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                Spacer(Modifier.height(8.dp))
                KidTextField(state.name, viewModel::onName, stringResource(R.string.parent_profile_name))
                KidTextField(state.alias, viewModel::onAlias, stringResource(R.string.parent_profile_alias))
                ProfileReadRow(stringResource(R.string.parent_profile_birth), child.birthDate.orEmpty())
                ProfileReadRow(stringResource(R.string.parent_profile_school), child.school.orEmpty())
                ProfileReadRow(stringResource(R.string.parent_profile_device), child.deviceModel.orEmpty(), showDivider = false)
            }
            Text(stringResource(R.string.parent_profile_readonly), color = colors.muted, fontSize = 12.sp)
            KidButton(stringResource(R.string.parent_profile_save), viewModel::save, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileReadRow(label: String, value: String, showDivider: Boolean = true) {
    val colors = LocalKidhackColors.current
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = colors.muted, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 12.sp)
        Text(value, color = colors.text, fontSize = 12.sp)
    }
    if (showDivider) HorizontalDivider(color = colors.border)
}
