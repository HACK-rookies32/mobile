package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

enum class AppControlFilter { ALL, BLOCKED, ALLOWED, LIMITED }

data class AppControlUiState(
    val items: List<AppControlItem> = emptyList(),
    val filter: AppControlFilter = AppControlFilter.ALL,
    val adding: Boolean = false,
    val newName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class AppControlViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appControlRepository: AppControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppControlUiState())
    val state: StateFlow<AppControlUiState> = _state.asStateFlow()

    init { load() }

    fun onFilter(f: AppControlFilter) = _state.update { it.copy(filter = f) }
    fun onNewName(v: String) = _state.update { it.copy(newName = v) }
    fun toggleAdding() = _state.update { it.copy(adding = !it.adding) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching emptyList()
                appControlRepository.items(id)
            }.onSuccess { items ->
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun toggle(name: String, blocked: Boolean) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.name == name) it.copy(blocked = blocked, limit = if (blocked) "차단" else it.limit) else it })
        }
    }

    fun addApp() {
        val name = _state.value.newName.trim()
        if (name.isBlank()) return
        _state.update {
            it.copy(
                items = it.items + AppControlItem(name, "📱", "기타", false, "무제한"),
                newName = "",
                adding = false,
            )
        }
    }
}

@Composable
fun ParentAppControlScreen(
    onBack: () -> Unit = {},
    viewModel: AppControlViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val filtered = state.items.filter { item ->
        when (state.filter) {
            AppControlFilter.ALL -> true
            AppControlFilter.BLOCKED -> item.blocked
            AppControlFilter.ALLOWED -> !item.blocked && (item.limit.isBlank() || item.limit == "무제한")
            AppControlFilter.LIMITED -> !item.blocked && item.limit.isNotBlank() && item.limit != "무제한" && item.limit != "차단"
        }
    }

    ParentScreenScaffold(
        header = {
            ParentHeaderBar(stringResource(R.string.parent_appcontrol_title), onBack)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.parent_filter_all), state.filter == AppControlFilter.ALL) { viewModel.onFilter(AppControlFilter.ALL) }
                FilterChip(stringResource(R.string.parent_filter_blocked), state.filter == AppControlFilter.BLOCKED) { viewModel.onFilter(AppControlFilter.BLOCKED) }
                FilterChip(stringResource(R.string.parent_filter_allowed), state.filter == AppControlFilter.ALLOWED) { viewModel.onFilter(AppControlFilter.ALLOWED) }
                FilterChip(stringResource(R.string.parent_filter_limited), state.filter == AppControlFilter.LIMITED) { viewModel.onFilter(AppControlFilter.LIMITED) }
            }
        },
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)
        filtered.forEach { app ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) { Text(app.icon, fontSize = 20.sp) }
                    Spacer(Modifier.size(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(app.name, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Text(app.category, color = colors.muted, fontSize = 12.sp)
                    }
                    if (!app.blocked && app.limit.isNotBlank()) {
                        Text(
                            app.limit,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.brandLight)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = colors.brand,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    KidSwitch(
                        checked = !app.blocked,
                        onCheckedChange = { viewModel.toggle(app.name, !it) },
                        trackColor = if (app.blocked) colors.danger else colors.safe,
                    )
                }
            }
        }
        if (state.adding) {
            KidCard {
                KidTextField(state.newName, viewModel::onNewName, stringResource(R.string.parent_appcontrol_add_hint))
                KidButton(stringResource(R.string.parent_appcontrol_add_confirm), viewModel::addApp, Modifier.fillMaxWidth())
            }
        }
        KidButton(
            stringResource(R.string.parent_appcontrol_add),
            viewModel::toggleAdding,
            Modifier.fillMaxWidth(),
            KidButtonVariant.Secondary,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
            .clickableChip(onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (selected) colors.brand else Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

private fun Modifier.clickableChip(onClick: () -> Unit) = clickable(onClick = onClick)
