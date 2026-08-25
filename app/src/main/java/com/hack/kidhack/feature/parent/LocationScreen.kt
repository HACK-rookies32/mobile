package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class LocationUiState(
    val current: LocationPoint? = null,
    val history: List<LocationPoint> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class LocationViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedChildRepository.selectedChildId.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                if (id == null) {
                    _state.update { it.copy(loading = false) }
                    return@runCatching
                }
                val current = locationRepository.current(id)
                val history = locationRepository.history(id)
                _state.update { it.copy(loading = false, current = current, history = history) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }
}

@Composable
fun ParentLocationScreen(
    onOpenGeofence: () -> Unit = {},
    viewModel: LocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(Modifier.fillMaxSize().background(colors.surface)) {
        BrandGradientHeader {
            ParentHeaderBar(stringResource(R.string.parent_location_title))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-16).dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.surface)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(224.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFDBEAFE), Color(0xFFDCFCE7), Color(0xFFBFDBFE)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 56.sp)
                    Text(
                        stringResource(R.string.parent_map_placeholder),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-28).dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👦", fontSize = 20.sp)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ParentLoadingOrError(state.loading && state.current == null, state.error) { viewModel.refresh() }
                KidCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center,
                        ) { Text("📍") }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.parent_location_current), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                            Text(
                                state.current?.label ?: stringResource(R.string.parent_dash),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 12.sp,
                                color = colors.muted,
                            )
                        }
                        Text(
                            stringResource(R.string.parent_location_live),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF0FDF4))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = colors.safe,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                    }
                }
                KidButton(
                    text = stringResource(R.string.parent_geofence_manage),
                    onClick = onOpenGeofence,
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
                KidCard {
                    Text(stringResource(R.string.parent_location_route), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    state.history.forEachIndexed { i, point ->
                        val last = i == state.history.lastIndex
                        val icon = when {
                            point.label?.contains("집") == true -> "🏠"
                            point.label?.contains("학교") == true -> "🏫"
                            point.label?.contains("학원") == true -> "📚"
                            else -> "📍"
                        }
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 18.sp)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                point.label ?: stringResource(R.string.parent_location_current),
                                modifier = Modifier.weight(1f),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 14.sp,
                                color = if (last) colors.brand else colors.text,
                            )
                            Text(point.recordedAt.takeLast(5), color = colors.muted, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
