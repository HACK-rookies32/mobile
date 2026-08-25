package com.hack.kidhack.feature.parent

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class GeofenceUiState(
    val items: List<Geofence> = emptyList(),
    val name: String = "",
    val radius: String = "200",
    val notify: Boolean = true,
    val loading: Boolean = true,
    val error: String? = null,
)

class GeofenceViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val geofenceRepository: GeofenceRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeofenceUiState())
    val state: StateFlow<GeofenceUiState> = _state.asStateFlow()

    init { refresh() }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onRadius(v: String) = _state.update { it.copy(radius = v.filter { ch -> ch.isDigit() }) }
    fun onNotify(v: Boolean) = _state.update { it.copy(notify = v) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching emptyList()
                geofenceRepository.list(id)
            }.onSuccess { list ->
                _state.update { it.copy(loading = false, items = list) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun add() {
        val name = _state.value.name.trim()
        val radius = _state.value.radius.toIntOrNull() ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children) ?: return@runCatching
                val loc = runCatching { locationRepository.current(id) }.getOrNull()
                geofenceRepository.create(
                    id,
                    Geofence("", name, loc?.lat ?: "37.5172", loc?.lng ?: "127.0473", radius, _state.value.notify),
                )
            }.onSuccess {
                _state.update { it.copy(name = "") }
                refresh()
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun delete(geofenceId: String) {
        viewModelScope.launch {
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching
                geofenceRepository.delete(id, geofenceId)
            }.onSuccess { refresh() }
        }
    }
}

@Composable
fun ParentGeofenceScreen(
    onBack: () -> Unit = {},
    viewModel: GeofenceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_geofence_title), onBack) },
    ) {
        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::refresh)
        if (state.items.isEmpty() && !state.loading) {
            Text(stringResource(R.string.parent_geofence_empty), color = colors.muted, fontSize = 13.sp)
        }
        state.items.forEach { item ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 20.sp)
                    Spacer(Modifier.size(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(item.name, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                        Text(
                            stringResource(R.string.parent_geofence_radius_unit, item.radius),
                            color = colors.muted,
                            fontSize = 12.sp,
                        )
                    }
                    KidButton(
                        stringResource(R.string.parent_geofence_delete),
                        { viewModel.delete(item.id) },
                        variant = KidButtonVariant.Danger,
                    )
                }
            }
        }
        KidCard {
            Text(stringResource(R.string.parent_geofence_add), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
            Spacer(Modifier.height(8.dp))
            KidTextField(state.name, viewModel::onName, stringResource(R.string.parent_geofence_name), placeholder = stringResource(R.string.parent_geofence_name_hint))
            KidTextField(
                state.radius,
                viewModel::onRadius,
                stringResource(R.string.parent_geofence_radius),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.parent_geofence_notify), modifier = Modifier.weight(1f), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp)
                KidSwitch(state.notify, viewModel::onNotify)
            }
            KidButton(stringResource(R.string.parent_geofence_save), viewModel::add, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}
